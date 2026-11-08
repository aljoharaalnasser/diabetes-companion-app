package com.z.diabetescompanion.data.auth

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Account operations that must outlive the screen that started them (FR-01, FR-02).
 *
 * Both sign-up and sign-in flip the Firebase auth state the moment they succeed, which
 * swaps the nav graph and clears the calling ViewModel. Anything that has to happen after
 * that point (writing the profile, verifying the role) runs on the application scope, and
 * the results are published here so a freshly created login screen can still show them.
 */
class AccountService(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val appScope: CoroutineScope,
) {
    private val _registrationInProgress = MutableStateFlow(false)
    private val _signInInProgress = MutableStateFlow(false)

    /** True while an account operation is mid-flight; the root screen shows a spinner. */
    val busy: StateFlow<Boolean> =
        combine(_registrationInProgress, _signInInProgress) { a, b -> a || b }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    private val _signInError = MutableStateFlow<String?>(null)
    /** Survives the login screen being destroyed and recreated by the role-mismatch sign-out. */
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    /** Remembered so the rebuilt login screen can prefill what the user already typed. */
    var lastSignInEmail: String = ""
        private set
    var lastSelectedRole: UserRole = UserRole.Patient
        private set

    fun clearSignInError() {
        _signInError.value = null
    }

    /**
     * Signs in, then checks the account's stored role against [expectedRole].
     *
     * The selector is a verification step, never a grant: the real role always comes from
     * the Firestore profile, and a mismatch signs the user straight back out (NFR-07).
     */
    suspend fun signIn(
        email: String,
        password: String,
        expectedRole: UserRole,
    ): AuthResult<Unit> = appScope.async {
        _signInInProgress.value = true
        _signInError.value = null
        lastSignInEmail = email.trim()
        lastSelectedRole = expectedRole
        try {
            when (val result = authRepository.signIn(email, password)) {
                is AuthResult.Error -> result
                is AuthResult.Success -> verifyRole(result.data.uid, expectedRole)
            }
        } finally {
            _signInInProgress.value = false
        }
    }.await()

    private suspend fun verifyRole(uid: String, expectedRole: UserRole): AuthResult<Unit> {
        val profile = try {
            userRepository.getProfile(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Can't read the profile (offline, rules). Let the session continue; the root
            // screen surfaces the real error rather than pretending the role was wrong.
            return AuthResult.Success(Unit)
        }
        // No profile yet: the complete-profile screen handles it, seeded with this role.
        if (profile == null) return AuthResult.Success(Unit)

        if (profile.role != expectedRole) {
            val message = "This account is registered as a ${profile.role.label}. " +
                "Select ${profile.role.label} to sign in."
            // Publish first: the nav host reads this to decide where to send the user back to.
            _signInError.value = message
            authRepository.signOut()
            // Stay "busy" until the sign-out has actually propagated, otherwise the combine
            // in RootViewModel briefly sees (old user, profile loaded, not busy) = Ready.
            withTimeoutOrNull(SIGN_OUT_TIMEOUT_MS) { authRepository.authState.first { it == null } }
            return AuthResult.Error(message)
        }
        return AuthResult.Success(Unit)
    }

    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        role: UserRole,
    ): AuthResult<Unit> = appScope.async {
        _registrationInProgress.value = true
        lastSelectedRole = role
        try {
            when (val auth = authRepository.signUp(displayName, email, password)) {
                is AuthResult.Error -> auth
                is AuthResult.Success -> createProfile(auth.data.uid, displayName, email, role)
            }
        } finally {
            _registrationInProgress.value = false
        }
    }.await()

    /** Also used by the "complete your profile" screen for accounts that pre-date roles. */
    suspend fun createProfile(
        uid: String,
        displayName: String,
        email: String,
        role: UserRole,
    ): AuthResult<Unit> = try {
        userRepository.createProfile(
            UserProfile(uid = uid, role = role, displayName = displayName.trim(), email = email.trim())
        )
        AuthResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Error(e.toFirestoreMessage(), e)
    }

    private companion object {
        const val SIGN_OUT_TIMEOUT_MS = 3_000L
    }
}

package com.z.diabetescompanion.data.auth

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import android.util.Log
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.IcrEnteredBy
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.IcrStatus
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.IcrRepository
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
import java.util.Date
import java.util.UUID

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
    private val icrRepository: IcrRepository,
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

        // A suspended account is refused before its role is even considered (FR-14).
        if (profile.isSuspended) return reject(SUSPENDED_MESSAGE)

        if (profile.role != expectedRole) {
            return reject(
                "This account is registered as a ${profile.role.label}. " +
                    "Select ${profile.role.label} to sign in."
            )
        }
        return AuthResult.Success(Unit)
    }

    /** Signs the user back out and leaves [message] where the rebuilt login screen can read it. */
    private suspend fun reject(message: String): AuthResult<Unit> {
        // Publish first: the nav host reads this to decide where to send the user back to.
        _signInError.value = message
        authRepository.signOut()
        // Stay "busy" until the sign-out has actually propagated, otherwise the combine
        // in RootViewModel briefly sees (old user, profile loaded, not busy) = Ready.
        withTimeoutOrNull(SIGN_OUT_TIMEOUT_MS) { authRepository.authState.first { it == null } }
        return AuthResult.Error(message)
    }

    /** Ends an open session whose account was suspended while it was running. */
    fun signOutSuspended() {
        _signInError.value = SUSPENDED_MESSAGE
        authRepository.signOut()
    }

    /**
     * [initialIcrGramsPerUnit] is the optional ratio a patient may type at sign-up. It is always
     * saved as UNCONFIRMED; only the linked doctor can confirm it.
     */
    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        role: UserRole,
        initialIcrGramsPerUnit: Double? = null,
    ): AuthResult<Unit> = appScope.async {
        _registrationInProgress.value = true
        lastSelectedRole = role
        try {
            when (val auth = authRepository.signUp(displayName, email, password)) {
                is AuthResult.Error -> auth
                is AuthResult.Success -> {
                    val created = createProfile(auth.data.uid, displayName, email, role)
                    if (created is AuthResult.Success && role == UserRole.Patient && initialIcrGramsPerUnit != null) {
                        saveInitialIcr(auth.data.uid, initialIcrGramsPerUnit)
                    }
                    created
                }
            }
        } finally {
            _registrationInProgress.value = false
        }
    }.await()

    /** Best effort: if this fails the chatbot simply asks for the ratio on first use. */
    private suspend fun saveInitialIcr(uid: String, gramsPerUnit: Double) {
        try {
            icrRepository.create(
                IcrSetting(
                    icrId = UUID.randomUUID().toString(),
                    patientId = uid,
                    gramsPerUnit = gramsPerUnit,
                    status = IcrStatus.Unconfirmed,
                    enteredBy = IcrEnteredBy.Patient,
                    version = 1,
                    effectiveFrom = Date(),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the sign-up ICR; the chatbot will ask again", e)
        }
    }

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

    companion object {
        const val SUSPENDED_MESSAGE =
            "This account has been suspended by an administrator. Contact the project team to " +
                "have it reactivated."

        private const val SIGN_OUT_TIMEOUT_MS = 3_000L
        private const val TAG = "AccountService"
    }
}

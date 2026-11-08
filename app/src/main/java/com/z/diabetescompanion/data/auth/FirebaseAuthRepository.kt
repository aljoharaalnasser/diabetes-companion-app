package com.z.diabetescompanion.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed [AuthRepository]. Lives for the whole process (created by AppContainer),
 * so the auth-state listener is registered once and never removed.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val isConfigured: Boolean = true

    private val _authState = MutableStateFlow(auth.currentUser?.toAuthUser())
    override val authState: Flow<AuthUser?> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = auth.currentUser?.toAuthUser()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = firebaseAuth.currentUser?.toAuthUser()
        }
    }

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> = runAuth {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        result.user?.toAuthUser() ?: error("Sign-in returned no user")
    }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): AuthResult<AuthUser> = runAuth {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Sign-up returned no user")

        // Creating the account fires the auth-state listener, which swaps the nav graph and
        // clears the sign-up ViewModel, cancelling the calling coroutine. Everything after this
        // point must therefore be non-cancellable or the profile update never lands locally.
        withContext(NonCancellable) {
            val name = displayName.trim()
            val finalUser = if (name.isNotEmpty()) {
                user.updateProfile(userProfileChangeRequest { this.displayName = name }).await()
                user.reload().await()
                // The listener already emitted a snapshot without the name; push the refreshed one.
                (auth.currentUser ?: user).also { _authState.value = it.toAuthUser() }
            } else {
                user
            }
            // Best-effort: a failure here shouldn't block onboarding.
            runCatching { finalUser.sendEmailVerification().await() }
            finalUser.toAuthUser()
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> = runAuth {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override fun signOut() = auth.signOut()

    // -----------------------------------------------------------------------

    private inline fun <T> runAuth(block: () -> T): AuthResult<T> = try {
        AuthResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage(), e)
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified,
    )
}

/** Translate Firebase exceptions into calm, actionable copy. */
internal fun Throwable.toUserMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException ->
        "That password is too weak. Use at least 8 characters with a mix of letters and numbers."
    is FirebaseAuthInvalidUserException -> when (errorCode) {
        "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact support."
        else -> "We couldn't find an account with that email."
    }
    is FirebaseAuthUserCollisionException ->
        "An account with this email already exists. Try signing in instead."
    is FirebaseAuthInvalidCredentialsException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        else -> "Incorrect email or password. Please try again."
    }
    is FirebaseTooManyRequestsException ->
        "Too many attempts. Please wait a moment and try again."
    is FirebaseNetworkException ->
        "No internet connection. Check your network and try again."
    is FirebaseAuthException -> when {
        errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "Email/password sign-in is not enabled for this project. Enable it in the Firebase console."
        // Returned when the Authentication product has never been initialised for the project.
        message?.contains("CONFIGURATION_NOT_FOUND") == true ->
            "Firebase Authentication isn't set up yet. In the Firebase console open Authentication, " +
                "press Get started, and enable Email/Password."
        else -> localizedMessage ?: "Something went wrong. Please try again."
    }
    else -> localizedMessage?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}

package com.z.diabetescompanion.data.auth

import kotlinx.coroutines.flow.Flow

/** Minimal, provider-agnostic snapshot of the signed-in user. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
) {
    /** "Zarrar Hussain" -> "ZH", "zarrar@x.com" -> "Z". */
    val initials: String
        get() {
            val source = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty()
            return source
                .split(" ", "@", ".")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifEmpty { "?" }
        }

    val firstName: String
        get() = displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')
            ?: "there"
}

/** Result type for auth operations. Errors always carry a user-presentable message. */
sealed interface AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult<Nothing>
}

inline fun <T> AuthResult<T>.onSuccess(block: (T) -> Unit): AuthResult<T> {
    if (this is AuthResult.Success) block(data)
    return this
}

inline fun <T> AuthResult<T>.onError(block: (String) -> Unit): AuthResult<T> {
    if (this is AuthResult.Error) block(message)
    return this
}

/**
 * Contract for authentication. Screens and ViewModels only ever talk to this
 * interface, so swapping Firebase for something else is a one-file change.
 */
interface AuthRepository {
    /** False when the backend has not been set up (e.g. missing google-services.json). */
    val isConfigured: Boolean

    /** Synchronous read of the current session, used to pick the start destination. */
    val currentUser: AuthUser?

    /** Emits on every sign-in / sign-out, starting with the current state. */
    val authState: Flow<AuthUser?>

    suspend fun signIn(email: String, password: String): AuthResult<AuthUser>

    suspend fun signUp(displayName: String, email: String, password: String): AuthResult<AuthUser>

    suspend fun sendPasswordReset(email: String): AuthResult<Unit>

    fun signOut()
}

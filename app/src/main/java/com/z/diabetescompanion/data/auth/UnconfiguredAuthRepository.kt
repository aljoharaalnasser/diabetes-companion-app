package com.z.diabetescompanion.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fallback used when Firebase failed to initialise (no google-services.json).
 * Lets the app build and run so the UI can be developed before the backend is wired up.
 */
class UnconfiguredAuthRepository : AuthRepository {

    override val isConfigured: Boolean = false
    override val currentUser: AuthUser? = null
    override val authState: Flow<AuthUser?> = flowOf(null)

    override suspend fun signIn(email: String, password: String): AuthResult<AuthUser> = notConfigured()

    override suspend fun signUp(displayName: String, email: String, password: String): AuthResult<AuthUser> =
        notConfigured()

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> = notConfigured()

    override fun signOut() = Unit

    private fun notConfigured() = AuthResult.Error(NOT_CONFIGURED_MESSAGE)

    companion object {
        const val NOT_CONFIGURED_MESSAGE =
            "Firebase isn't configured yet. Add app/google-services.json and rebuild."
    }
}

package com.z.diabetescompanion.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.auth.AccountService
import com.z.diabetescompanion.data.auth.AuthRepository
import com.z.diabetescompanion.data.auth.onError
import com.z.diabetescompanion.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val role: UserRole = UserRole.Patient,
    val emailError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val isLoading: Boolean = false,
    val isBackendConfigured: Boolean = true,
) {
    val canSubmit: Boolean get() = !isLoading && email.isNotBlank() && password.isNotEmpty()
}

class LoginViewModel(
    authRepository: AuthRepository,
    private val accountService: AccountService,
) : ViewModel() {

    private val form = MutableStateFlow(
        LoginUiState(
            // Restored after a role-mismatch sign-out so the user only changes the role.
            email = accountService.lastSignInEmail,
            role = accountService.lastSelectedRole,
            isBackendConfigured = authRepository.isConfigured,
        )
    )

    /**
     * A role mismatch destroys this screen (sign-in, then sign-out), so the error lives in
     * [AccountService] and is merged back in here rather than held in local state.
     */
    val uiState: StateFlow<LoginUiState> =
        combine(form, accountService.signInError) { state, sharedError ->
            state.copy(formError = state.formError ?: sharedError)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    /** Sign-in offers every role, including Admin, which self-registration does not. */
    val selectableRoles = UserRole.entries

    fun onEmailChange(value: String) {
        accountService.clearSignInError()
        form.update { it.copy(email = value, emailError = null, formError = null) }
    }

    fun onPasswordChange(value: String) {
        accountService.clearSignInError()
        form.update { it.copy(password = value, passwordError = null, formError = null) }
    }

    fun onRoleChange(role: UserRole) {
        accountService.clearSignInError()
        form.update { it.copy(role = role, formError = null) }
    }

    /**
     * Validates locally, then signs in and verifies the role. On success nothing else is
     * needed here: the auth-state flow observed by RootViewModel switches the nav graph.
     */
    fun submit() {
        val state = form.value
        if (state.isLoading) return

        val emailError = AuthValidation.emailError(state.email)
        val passwordError = AuthValidation.passwordPresenceError(state.password)
        if (emailError != null || passwordError != null) {
            form.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        form.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            accountService.signIn(state.email, state.password, state.role)
                .onError { message -> form.update { it.copy(formError = message) } }
            form.update { it.copy(isLoading = false) }
        }
    }
}

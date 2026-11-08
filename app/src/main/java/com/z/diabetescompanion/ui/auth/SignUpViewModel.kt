package com.z.diabetescompanion.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.auth.AccountService
import com.z.diabetescompanion.data.auth.AuthRepository
import com.z.diabetescompanion.data.auth.onError
import com.z.diabetescompanion.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val role: UserRole = UserRole.Patient,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val formError: String? = null,
    val isLoading: Boolean = false,
    val isBackendConfigured: Boolean = true,
) {
    val passwordStrength: Int get() = AuthValidation.passwordStrength(password)
    val canSubmit: Boolean
        get() = !isLoading && name.isNotBlank() && email.isNotBlank() &&
            password.isNotEmpty() && confirmPassword.isNotEmpty()
}

class SignUpViewModel(
    authRepository: AuthRepository,
    private val accountService: AccountService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SignUpUiState(isBackendConfigured = authRepository.isConfigured)
    )
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    /** Self-registration covers the three care roles; admins are provisioned separately. */
    val selectableRoles = listOf(UserRole.Patient, UserRole.Doctor, UserRole.Caregiver)

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, nameError = null, formError = null) }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, confirmPasswordError = null, formError = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null, formError = null) }

    fun onRoleChange(role: UserRole) = _uiState.update { it.copy(role = role) }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        val nameError = AuthValidation.nameError(state.name)
        val emailError = AuthValidation.emailError(state.email)
        val passwordError = AuthValidation.newPasswordError(state.password)
        val confirmError = AuthValidation.confirmPasswordError(state.password, state.confirmPassword)

        if (listOf(nameError, emailError, passwordError, confirmError).any { it != null }) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError,
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            accountService.register(state.name, state.email, state.password, state.role)
                .onError { message -> _uiState.update { it.copy(formError = message) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

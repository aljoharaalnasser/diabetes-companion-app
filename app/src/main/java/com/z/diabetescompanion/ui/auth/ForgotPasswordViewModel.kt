package com.z.diabetescompanion.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.auth.AuthRepository
import com.z.diabetescompanion.data.auth.onError
import com.z.diabetescompanion.data.auth.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val formError: String? = null,
    val isLoading: Boolean = false,
    /** Once true the screen swaps to the "check your inbox" confirmation. */
    val emailSent: Boolean = false,
    val isBackendConfigured: Boolean = true,
) {
    val canSubmit: Boolean get() = !isLoading && email.isNotBlank()
}

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ForgotPasswordUiState(isBackendConfigured = authRepository.isConfigured)
    )
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }

    /** Lets the user go back to the form to try a different address. */
    fun reset() = _uiState.update { it.copy(emailSent = false, formError = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        val emailError = AuthValidation.emailError(state.email)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            authRepository.sendPasswordReset(state.email)
                .onSuccess { _uiState.update { it.copy(emailSent = true) } }
                .onError { message -> _uiState.update { it.copy(formError = message) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

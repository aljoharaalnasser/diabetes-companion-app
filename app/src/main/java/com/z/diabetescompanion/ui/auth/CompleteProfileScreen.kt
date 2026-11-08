package com.z.diabetescompanion.ui.auth

import com.z.diabetescompanion.ui.components.RoleSelector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.auth.AccountService
import com.z.diabetescompanion.data.auth.AuthUser
import com.z.diabetescompanion.data.auth.onError
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shown when a signed-in account has no Firestore profile yet (created before roles
 * existed, or the profile write failed during registration). Creates it and moves on.
 */
@Composable
fun CompleteProfileScreen(
    user: AuthUser,
    onSignOut: () -> Unit,
    viewModel: CompleteProfileViewModel = viewModel { CompleteProfileViewModel(user, container().accountService) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScaffold(
        title = "Complete your profile",
        subtitle = "Tell us who you are so we can show the right screens.",
    ) {
        RoleSelector(
            roles = listOf(UserRole.Patient, UserRole.Doctor, UserRole.Caregiver),
            selected = state.role,
            onSelected = viewModel::onRoleChange,
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(18.dp))
        AppTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = "Full name",
            leadingIcon = Icons.Rounded.PersonOutline,
            errorText = state.nameError,
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(20.dp))
        InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))
        PrimaryButton(text = "Continue", onClick = viewModel::submit, loading = state.isLoading, enabled = state.name.isNotBlank())
        LinkButton(text = "Sign out", onClick = onSignOut, enabled = !state.isLoading)
    }
}

data class CompleteProfileUiState(
    val name: String,
    val role: UserRole = UserRole.Patient,
    val nameError: String? = null,
    val formError: String? = null,
    val isLoading: Boolean = false,
)

class CompleteProfileViewModel(
    private val user: AuthUser,
    private val accountService: AccountService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CompleteProfileUiState(
            name = user.displayName.orEmpty(),
            // Seeded with whatever role was picked on the sign-in screen.
            role = accountService.lastSelectedRole,
        )
    )
    val uiState = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null, formError = null) }
    fun onRoleChange(role: UserRole) = _uiState.update { it.copy(role = role) }

    fun submit() {
        val state = _uiState.value
        AuthValidation.nameError(state.name)?.let { error ->
            _uiState.update { it.copy(nameError = error) }
            return
        }
        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            accountService.createProfile(user.uid, state.name, user.email.orEmpty(), state.role)
                .onError { message -> _uiState.update { it.copy(formError = message) } }
            // On success the root profile flow flips to Ready and this screen disappears.
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

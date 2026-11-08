package com.z.diabetescompanion.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.ui.AppViewModelProvider
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PasswordTextField
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.RoleSelector

@Composable
fun LoginScreen(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Choose your role and sign in to keep your glucose story going.",
        isBackendConfigured = state.isBackendConfigured,
    ) {
        RoleSelector(
            roles = viewModel.selectableRoles,
            selected = state.role,
            onSelected = viewModel::onRoleChange,
            enabled = !state.isLoading,
            label = "Sign in as",
            helper = "Must match the role your account was registered with.",
        )
        Spacer(Modifier.height(6.dp))

        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            leadingIcon = Icons.Rounded.MailOutline,
            errorText = state.emailError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        PasswordTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            leadingIcon = Icons.Rounded.Lock,
            errorText = state.passwordError,
            enabled = !state.isLoading,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.submit()
            }),
        )

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            LinkButton(
                text = "Forgot password?",
                onClick = onNavigateToForgotPassword,
                enabled = !state.isLoading,
            )
        }

        InlineMessage(
            message = state.formError,
            tone = MessageTone.Error,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        PrimaryButton(
            text = "Sign in",
            onClick = {
                focusManager.clearFocus()
                viewModel.submit()
            },
            loading = state.isLoading,
            enabled = state.canSubmit,
        )

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "New here?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinkButton(text = "Create an account", onClick = onNavigateToSignUp, enabled = !state.isLoading)
        }
    }
}

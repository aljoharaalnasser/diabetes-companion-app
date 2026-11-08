package com.z.diabetescompanion.ui.auth

import com.z.diabetescompanion.ui.components.RoleSelector
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PersonOutline
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
import com.z.diabetescompanion.ui.theme.AppTheme

@Composable
fun SignUpScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SignUpViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    AuthScaffold(
        title = "Create your account",
        subtitle = "Choose your role and a few details to get started.",
        onBack = onNavigateBack,
        isBackendConfigured = state.isBackendConfigured,
    ) {
        RoleSelector(
            roles = viewModel.selectableRoles,
            selected = state.role,
            onSelected = viewModel::onRoleChange,
            enabled = !state.isLoading,
            helper = "You'll sign in with this role. Admin accounts are created by the project team.",
        )
        Spacer(Modifier.height(6.dp))

        AppTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = "Full name",
            leadingIcon = Icons.Rounded.PersonOutline,
            errorText = state.nameError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Text,
        )
        Spacer(Modifier.height(14.dp))
        AppTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = "Email",
            leadingIcon = Icons.Rounded.MailOutline,
            errorText = state.emailError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(14.dp))
        PasswordTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            leadingIcon = Icons.Rounded.Lock,
            errorText = state.passwordError,
            enabled = !state.isLoading,
            imeAction = ImeAction.Next,
        )
        PasswordStrengthMeter(strength = state.passwordStrength, visible = state.password.isNotEmpty())
        Spacer(Modifier.height(14.dp))
        PasswordTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = "Confirm password",
            leadingIcon = Icons.Rounded.LockReset,
            errorText = state.confirmPasswordError,
            enabled = !state.isLoading,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                viewModel.submit()
            }),
        )

        Spacer(Modifier.height(16.dp))
        InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))

        PrimaryButton(
            text = "Create account",
            onClick = {
                focusManager.clearFocus()
                viewModel.submit()
            },
            loading = state.isLoading,
            enabled = state.canSubmit,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "By continuing you agree to the prototype terms and privacy notice. Your health data is only shared with people you approve.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkButton(text = "Sign in", onClick = onNavigateToLogin, enabled = !state.isLoading)
        }
    }
}

/** Four-segment strength bar; colour walks from coral -> amber -> green. */
@Composable
private fun PasswordStrengthMeter(strength: Int, visible: Boolean) {
    if (!visible) return
    val clinical = AppTheme.clinical
    val (label, colour) = when (strength) {
        0, 1 -> "Weak" to clinical.veryHigh
        2 -> "Okay" to clinical.high
        3 -> "Good" to clinical.inRange
        else -> "Strong" to clinical.inRange
    }
    val animatedColour by animateColorAsState(colour, label = "strengthColour")

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { index ->
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (index < strength) animatedColour else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
            }
        }
        Text(
            text = "Password strength: $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

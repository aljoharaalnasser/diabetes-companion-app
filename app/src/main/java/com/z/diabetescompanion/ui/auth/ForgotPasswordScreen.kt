package com.z.diabetescompanion.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.ui.AppViewModelProvider
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.theme.AppTheme

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    AuthScaffold(
        title = if (state.emailSent) "Check your inbox" else "Reset your password",
        subtitle = if (state.emailSent) "We've sent instructions to get you back in."
        else "Enter the email you signed up with and we'll send a reset link.",
        onBack = onNavigateBack,
        isBackendConfigured = state.isBackendConfigured,
    ) {
        AnimatedContent(
            targetState = state.emailSent,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            label = "forgotPasswordContent",
            modifier = Modifier.fillMaxWidth(),
        ) { sent ->
            if (sent) {
                SentContent(
                    email = state.email,
                    onBackToSignIn = onNavigateBack,
                    onTryAgain = viewModel::reset,
                )
            } else {
                FormContent(
                    state = state,
                    onEmailChange = viewModel::onEmailChange,
                    onSubmit = {
                        focusManager.clearFocus()
                        viewModel.submit()
                    },
                )
            }
        }
    }
}

@Composable
private fun FormContent(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email",
            leadingIcon = Icons.Rounded.MailOutline,
            errorText = state.emailError,
            enabled = !state.isLoading,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
        Spacer(Modifier.height(20.dp))
        InlineMessage(
            message = state.formError,
            tone = MessageTone.Error,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        PrimaryButton(
            text = "Send reset link",
            onClick = onSubmit,
            loading = state.isLoading,
            enabled = state.canSubmit,
        )
    }
}

@Composable
private fun SentContent(
    email: String,
    onBackToSignIn: () -> Unit,
    onTryAgain: () -> Unit,
) {
    val clinical = AppTheme.clinical
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(clinical.inRangeContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MarkEmailRead,
                contentDescription = null,
                tint = clinical.inRange,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "If an account exists for",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = email.trim(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "you'll receive a link to choose a new password. It can take a minute, and check spam too.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(24.dp))
        SecondaryButton(text = "Back to sign in", onClick = onBackToSignIn)
        LinkButton(text = "Use a different email", onClick = onTryAgain)
    }
}

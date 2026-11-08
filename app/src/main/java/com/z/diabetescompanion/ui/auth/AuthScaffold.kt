package com.z.diabetescompanion.ui.auth

import com.z.diabetescompanion.ui.theme.Red50
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.ui.components.BrandMark
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.theme.Blue50
import com.z.diabetescompanion.ui.theme.Blue80

/**
 * Shared layout for the auth flow: soft gradient blobs, brand mark, headline,
 * then a form card. Scrolls and pads for the IME so fields never hide behind the keyboard.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    isBackendConfigured: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuthBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 4.dp).offset(x = (-12).dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(48.dp))
            }

            BrandMark(size = 64.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            InlineMessage(
                message = if (isBackendConfigured) null else
                    "Firebase isn't configured. Add app/google-services.json to enable sign-in.",
                tone = MessageTone.Warning,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Two blurred-looking radial blobs; cheap to draw and gives the page depth. */
@Composable
private fun AuthBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Blue80.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(w * 0.9f, h * 0.05f),
                radius = w * 0.7f,
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.9f, h * 0.05f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Red50.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * 0.05f, h * 0.35f),
                radius = w * 0.5f,
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.05f, h * 0.35f),
        )
    }
}

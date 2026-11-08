package com.z.diabetescompanion.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.ui.LocalAppContainer
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.launch
import com.z.diabetescompanion.ui.theme.Blue30
import com.z.diabetescompanion.ui.theme.Blue40
import com.z.diabetescompanion.ui.theme.Blue50
import com.z.diabetescompanion.ui.theme.Blue80

data class MoreItem(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The "More" tab: account card, role-specific entries, demo role switcher and sign-out.
 * The role switcher only changes which screens are shown; permissions stay with the account.
 */
@Composable
fun MoreScreen(items: List<MoreItem>) {
    val session = currentSession()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var showRoleDialog by rememberSaveable { mutableStateOf(false) }
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "More") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            AccountCard()
            Spacer(Modifier.height(20.dp))

            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                items.forEachIndexed { index, item ->
                    MoreRow(item.label, item.description, item.icon, item.onClick)
                    if (index < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 64.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                MoreRow(
                    label = "Demo role switcher",
                    description = "Presentation tool. Shows another role's screens without changing permissions.",
                    icon = Icons.Rounded.SwitchAccount,
                    onClick = { showRoleDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 64.dp))
                MoreRow(
                    label = "Sign out",
                    description = "You'll need your email and password to sign back in.",
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    onClick = { showSignOutDialog = true },
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(Modifier.height(20.dp))
            PrototypeNotice()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRoleDialog) {
        AlertDialog(
            onDismissRequest = { showRoleDialog = false },
            title = { Text("View as role") },
            text = {
                Column {
                    Text(
                        "For demonstrations only. Your account stays a ${session.profile.role.label} and data access does not change.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    UserRole.entries.forEach { role ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        container.preferencesRepository.setDemoRole(role.takeIf { it != session.profile.role })
                                    }
                                    showRoleDialog = false
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(selected = role == session.effectiveRole, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(role.label + if (role == session.profile.role) " (your account)" else "")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRoleDialog = false }) { Text("Close") } },
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need your email and password to sign back in.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    scope.launch { container.preferencesRepository.setDemoRole(null) }
                    container.authRepository.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountCard() {
    val session = currentSession()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Brush.linearGradient(listOf(Blue50, Blue30)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                session.profile.initials,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(session.profile.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(session.profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(session.profile.role.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MoreRow(
    label: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

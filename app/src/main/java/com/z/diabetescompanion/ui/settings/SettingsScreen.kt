package com.z.diabetescompanion.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.prefs.TextSize
import com.z.diabetescompanion.data.prefs.ThemeMode
import com.z.diabetescompanion.data.reminders.ReminderReceiver
import com.z.diabetescompanion.ui.LocalAppContainer
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.launch

/**
 * Appearance, notification and privacy preferences (FR-22; refined in S3-02).
 *
 * Every control writes straight to DataStore, so the change persists and takes effect
 * immediately: text size and theme reach the whole tree through the app root, and the two
 * notification switches gate the reminder alarms and the alert notifications separately.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val session = currentSession()
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = session.preferences

    var permissionDenied by remember { mutableStateOf(!ReminderReceiver.hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
    }
    fun askForPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SectionHeader("Appearance")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingRow(
                    icon = Icons.Rounded.FormatSize,
                    title = "Text size",
                    description = "Applies across the whole app, on top of your device's font size.",
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TextSize.entries.forEachIndexed { index, size ->
                        SegmentedButton(
                            selected = prefs.textSize == size,
                            onClick = { scope.launch { container.preferencesRepository.setTextSize(size) } },
                            shape = SegmentedButtonDefaults.itemShape(index, TextSize.entries.size),
                            label = { Text(size.label, maxLines = 1) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextSizePreview()

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(18.dp))

                SettingRow(
                    icon = when (prefs.themeMode) {
                        ThemeMode.System -> Icons.Rounded.PhoneAndroid
                        ThemeMode.Light -> Icons.Rounded.LightMode
                        ThemeMode.Dark -> Icons.Rounded.DarkMode
                    },
                    title = "Theme",
                    description = "System follows your device's light or dark setting.",
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                ) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = prefs.themeMode == mode,
                            onClick = { scope.launch { container.preferencesRepository.setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(mode.label, maxLines = 1) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Notifications")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                ToggleRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Allow notifications",
                    description = "Turning this off silences both kinds below.",
                    checked = prefs.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferencesRepository.setNotificationsEnabled(enabled) }
                        if (enabled) askForPermission()
                    },
                )

                AnimatedVisibility(visible = prefs.notificationsEnabled) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(14.dp))
                        ToggleRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Medication reminders",
                            description = "A notification at each saved reminder time.",
                            checked = prefs.reminderNotifications,
                            onCheckedChange = { enabled ->
                                scope.launch { container.preferencesRepository.setReminderNotifications(enabled) }
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        ToggleRow(
                            icon = Icons.Rounded.WarningAmber,
                            title = "Glucose alerts",
                            description = "A notification when an alert is raised or escalates. " +
                                "Escalation itself always continues and is always recorded, " +
                                "whether or not it notifies you here.",
                            checked = prefs.alertNotifications,
                            onCheckedChange = { enabled ->
                                scope.launch { container.preferencesRepository.setAlertNotifications(enabled) }
                            },
                        )
                    }
                }

                if (prefs.notificationsEnabled && permissionDenied) {
                    Spacer(Modifier.height(14.dp))
                    InlineMessage(
                        message = "Android is blocking notifications for this app. Allow them in " +
                            "system settings, or nothing will appear on your screen.",
                        tone = MessageTone.Warning,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        LinkButton(text = "Ask again", onClick = { askForPermission() })
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Privacy")
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SettingRow(
                    icon = Icons.Rounded.Shield,
                    title = "How your data is protected",
                    description = "Encryption, role-based access, retention and deletion requests.",
                )
                LinkButton(text = "Open privacy information", onClick = onOpenPrivacy)
            }

            Spacer(Modifier.height(24.dp))
            PrototypeNotice()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The whole row is one switch: `toggleable` on the Row with `onCheckedChange = null` on the
 * [Switch] merges them into a single control, so a screen reader announces the title with the
 * on/off state instead of an unlabelled switch, and the touch target is the full row (NFR-02).
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Shows the chosen size on real content before the user leaves the screen. Hidden from
 * screen readers, since it repeats text that is already on the page (NFR-02).
 */
@Composable
private fun TextSizePreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
            .clearAndSetSemantics { },
    ) {
        Text("Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("142 mg/dL", style = MaterialTheme.typography.headlineSmall)
        Text("Within your target range", style = MaterialTheme.typography.bodyMedium)
        Text("Recorded today at 8:15 AM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

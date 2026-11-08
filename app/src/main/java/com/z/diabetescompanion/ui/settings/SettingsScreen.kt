package com.z.diabetescompanion.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.prefs.TextSize
import com.z.diabetescompanion.data.reminders.ReminderReceiver
import com.z.diabetescompanion.ui.LocalAppContainer
import com.z.diabetescompanion.ui.components.ChoiceChips
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.launch

/** Text size, notification preference and privacy entry points (FR-22 essentials). */
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

    ScreenScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SectionHeader("Text size")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ChoiceChips(
                        options = TextSize.entries,
                        selected = prefs.textSize,
                        onSelected = { size -> scope.launch { container.preferencesRepository.setTextSize(size) } },
                        label = { it.label },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Larger text applies across the whole app and works together with your device's font size.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Notifications")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Medication reminders", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Show a notification at each reminder time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { container.preferencesRepository.setNotificationsEnabled(enabled) }
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                    }
                    if (prefs.notificationsEnabled && permissionDenied) {
                        Spacer(Modifier.height(10.dp))
                        InlineMessage(
                            message = "Android is blocking notifications for this app. Allow them in system settings or reminders won't appear.",
                            tone = MessageTone.Warning,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            LinkButton(text = "Ask again", onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Privacy")
            Spacer(Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How your data is protected", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Encryption, role-based access, retention and deletion requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinkButton(text = "Open privacy information", onClick = onOpenPrivacy)
                }
            }

            Spacer(Modifier.height(24.dp))
            PrototypeNotice()
            Spacer(Modifier.height(24.dp))
        }
    }
}

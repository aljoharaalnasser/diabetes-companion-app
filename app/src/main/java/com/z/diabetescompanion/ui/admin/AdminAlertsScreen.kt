package com.z.diabetescompanion.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertSeverity
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AlertSeverityChip
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.components.icon
import com.z.diabetescompanion.ui.theme.AppTheme

/**
 * Operational alert monitoring (FR-14, S3-01): how far each alert travelled, whether it was
 * resolved, and by whom.
 *
 * Deliberately operational, not clinical. The admin sees severity, stage, timings and the
 * resolution note's author, and no glucose value, because running the platform does not
 * require reading a patient's readings (NFR-09).
 */
@Composable
fun AdminAlertsScreen(state: AdminUiState) {
    var filter by rememberSaveable { mutableStateOf(AlertFilter.Open) }
    val visible = remember(state.alerts, filter) { state.alerts.filter { filter.matches(it) } }

    ScreenScaffold(title = "Operational alerts") { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlertFilter.entries.forEach { option ->
                            FilterChip(
                                selected = option == filter,
                                onClick = { filter = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                item { SectionHeader("${visible.size} ${filter.label.lowercase()} alert(s)") }

                if (state.alertsError != null) {
                    item {
                        InlineMessage(
                            message = "Alerts could not be loaded, so this list is not empty " +
                                "because nothing happened. ${state.alertsError}",
                            tone = MessageTone.Error,
                        )
                    }
                } else if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.CheckCircle,
                            title = "Nothing to show",
                            description = if (state.alerts.isEmpty())
                                "No alert has been raised on this platform yet. Alerts appear here as soon as a patient saves an out-of-range reading."
                            else "No alert matches this filter.",
                        )
                    }
                } else {
                    items(visible, key = { "${it.patientUid}_${it.id}" }) { alert ->
                        OperationalAlertCard(alert)
                    }
                }

                item {
                    InlineMessage(
                        message = "This view shows alert status only. Glucose values, medication " +
                            "and conversations are not part of administration and are not read here.",
                        tone = MessageTone.Info,
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationalAlertCard(alert: Alert) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (alert.isResolved) AppTheme.clinical.inRangeContainer
                            else if (alert.severity == AlertSeverity.Critical) AppTheme.clinical.veryHighContainer
                            else AppTheme.clinical.highContainer,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (alert.isResolved) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (alert.isResolved) AppTheme.clinical.inRange
                        else if (alert.severity == AlertSeverity.Critical) AppTheme.clinical.veryHigh
                        else AppTheme.clinical.high,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        alert.patientName.ifBlank { "Unnamed patient" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Raised ${Format.dateTime(alert.recordedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AlertSeverityChip(alert.severity, alert.isResolved)
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    alert.stage.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(alert.stage.label, style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(6.dp))
            DetailRow("Patient response", alert.patientResponse?.label ?: "None recorded")
            alert.caregiverNotifiedAt?.let { DetailRow("Caregiver notified", Format.dateTime(it)) }
            alert.doctorNotifiedAt?.let { DetailRow("Doctor notified", Format.dateTime(it)) }
            DetailRow("Notification attempts", alert.events.count { it.type == "notification_attempt" }.toString())

            if (alert.isResolved) {
                DetailRow(
                    "Resolved by",
                    buildString {
                        append(alert.resolvedByName.ifBlank { "Unknown" })
                        alert.resolvedByRole?.let { append(" (${it.label})") }
                    },
                )
                alert.resolvedAt?.let { DetailRow("Resolved at", Format.dateTime(it)) }
                if (alert.resolutionNote.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Follow-up note", style = MaterialTheme.typography.labelMedium)
                    Text(
                        alert.resolutionNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (alert.acknowledgedButOpen) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Acknowledged but still open: the care team has not recorded a resolution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

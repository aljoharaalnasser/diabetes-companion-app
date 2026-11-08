package com.z.diabetescompanion.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.model.AlertStage
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader

/**
 * The administrator's landing page (FR-14, S3-01): account totals, how many patients have a
 * care team, and where the platform's alerts currently stand.
 *
 * Every figure comes from AdminMetrics over the same live lists the other tabs render, so a
 * count here always matches the records behind it.
 */
@Composable
fun AdminOverviewScreen(state: AdminUiState) {
    ScreenScaffold(title = "Overview") { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error, Modifier.padding(padding))
            else -> {
                val m = state.metrics
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { SectionHeader("Accounts") }
                    item {
                        StatRow { tile ->
                            StatTile(
                                label = "Patients",
                                value = m.totalPatients.toString(),
                                icon = Icons.Rounded.Groups,
                                tone = StatTone.Brand,
                                modifier = tile,
                            )
                            StatTile(
                                label = "Active cases",
                                value = m.activeCases.toString(),
                                icon = Icons.Rounded.MonitorHeart,
                                tone = StatTone.Brand,
                                caption = "Patients with a care team",
                                modifier = tile,
                            )
                        }
                    }
                    item {
                        StatRow { tile ->
                            StatTile(
                                label = "Care-team accounts",
                                value = (m.totalDoctors + m.totalCaregivers).toString(),
                                icon = Icons.Rounded.Groups,
                                caption = "${m.totalDoctors} doctors · ${m.totalCaregivers} caregivers",
                                modifier = tile,
                            )
                            StatTile(
                                label = "Suspended",
                                value = m.suspendedAccounts.toString(),
                                icon = Icons.Rounded.PersonOff,
                                tone = if (m.suspendedAccounts > 0) StatTone.Accent else StatTone.Neutral,
                                modifier = tile,
                            )
                        }
                    }

                    item { SectionHeader("Alerts") }
                    if (state.alertsError != null) {
                        item {
                            InlineMessage(
                                message = "Alert figures are unavailable: ${state.alertsError}",
                                tone = MessageTone.Error,
                            )
                        }
                    }
                    item {
                        StatRow { tile ->
                            StatTile(
                                label = "Open alerts",
                                value = m.openAlerts.toString(),
                                icon = Icons.Rounded.NotificationsActive,
                                tone = if (m.openAlerts > 0) StatTone.Accent else StatTone.Neutral,
                                modifier = tile,
                            )
                            StatTile(
                                label = "Open critical",
                                value = m.openCriticalAlerts.toString(),
                                icon = Icons.Rounded.Warning,
                                tone = if (m.openCriticalAlerts > 0) StatTone.Accent else StatTone.Neutral,
                                modifier = tile,
                            )
                        }
                    }
                    item {
                        StatRow { tile ->
                            StatTile(
                                label = "Resolved",
                                value = m.resolvedAlerts.toString(),
                                icon = Icons.Rounded.CheckCircle,
                                caption = m.resolutionRatePercent?.let { "$it% of all alerts" }
                                    ?: "No alerts raised yet",
                                modifier = tile,
                            )
                            StatTile(
                                label = "Average time to resolve",
                                value = m.averageMinutesToResolve?.let { "$it min" } ?: "—",
                                icon = Icons.Rounded.Schedule,
                                caption = if (m.averageMinutesToResolve == null) "Nothing resolved yet" else null,
                                modifier = tile,
                            )
                        }
                    }

                    item { SectionHeader("Escalation stage") }
                    item {
                        BreakdownBars(
                            rows = listOf(
                                BreakdownRow("Awaiting patient", m.stageCount(AlertStage.Created), StatTone.Accent),
                                BreakdownRow("Caregiver notified", m.stageCount(AlertStage.CaregiverNotified), StatTone.Accent),
                                BreakdownRow("Doctor notified", m.stageCount(AlertStage.DoctorNotified), StatTone.Accent),
                                BreakdownRow("Resolved", m.stageCount(AlertStage.Resolved), StatTone.Brand),
                            ),
                            emptyLabel = "No alert has been raised on this platform yet.",
                        )
                    }
                    item {
                        Text(
                            text = "Acknowledgment is not resolution: ${m.acknowledgedNotResolved} " +
                                "alert(s) have a patient response but are still open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (m.pendingDeletionRequests > 0) {
                        item { SectionHeader("Needs action") }
                        item {
                            StatTile(
                                label = "Pending deletion requests",
                                value = m.pendingDeletionRequests.toString(),
                                icon = Icons.Rounded.PersonOff,
                                tone = StatTone.Accent,
                                caption = "Open the Users tab to record completion",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item { PrototypeNotice() }
                }
            }
        }
    }
}

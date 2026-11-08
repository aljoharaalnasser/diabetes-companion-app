package com.z.diabetescompanion.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader

/**
 * Aggregate reporting (FR-14, S3-01). Breakdowns of who uses the platform, how consent is
 * shared, and how the alert workflow performs, with the retention position for NFR-10.
 *
 * Reuses the same AdminMetrics the overview counts from, in the spirit of the charter's
 * "underlying aggregate queries are reusable for admin reporting" (US-18).
 */
@Composable
fun AdminAnalyticsScreen(state: AdminUiState) {
    ScreenScaffold(title = "Analytics") { padding ->
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
                    item { SectionHeader("Accounts by role") }
                    item {
                        BreakdownBars(
                            rows = UserRole.entries.map { role ->
                                BreakdownRow(
                                    label = role.label,
                                    count = m.accountsByRole[role] ?: 0,
                                    tone = if (role == UserRole.Admin) StatTone.Accent else StatTone.Brand,
                                )
                            },
                            emptyLabel = "No accounts have been registered yet.",
                        )
                    }

                    item { SectionHeader("Care-team coverage") }
                    item {
                        DetailCard {
                            DetailRow("Patients with an active care team", m.activeCases.toString())
                            DetailRow("Patients with nobody linked", m.patientsWithoutCareTeam.toString())
                            DetailRow("Active doctor links", m.activeDoctorLinks.toString())
                            DetailRow("Active caregiver links", m.activeCaregiverLinks.toString())
                            DetailRow("Revoked links", m.revokedLinks.toString())
                        }
                    }
                    item {
                        Text(
                            text = "A revoked link stops the next server read, so a revoked " +
                                "caregiver drops off their dashboard immediately (NFR-11).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    item { SectionHeader("Alert workflow") }
                    item {
                        DetailCard {
                            DetailRow("Alerts raised", m.totalAlerts.toString())
                            DetailRow("Still open", m.openAlerts.toString())
                            DetailRow("Open and critical", m.openCriticalAlerts.toString())
                            DetailRow("Escalated past the patient", m.escalatedBeyondPatient.toString())
                            DetailRow("Acknowledged but not resolved", m.acknowledgedNotResolved.toString())
                            DetailRow(
                                "Resolution rate",
                                m.resolutionRatePercent?.let { "$it%" } ?: "No alerts yet",
                            )
                            DetailRow(
                                "Average time to resolve",
                                m.averageMinutesToResolve?.let { "$it min" } ?: "Nothing resolved yet",
                            )
                        }
                    }

                    item { SectionHeader("Data handling") }
                    item {
                        DetailCard {
                            DetailRow("Deletion requests pending", m.pendingDeletionRequests.toString())
                            DetailRow("Deletion requests completed", m.completedDeletionRequests.toString())
                            DetailRow("Suspended accounts", m.suspendedAccounts.toString())
                        }
                    }
                    item {
                        InlineMessage(
                            message = "Retention policy: identifiable project records are deleted " +
                                "within 90 days of the final evaluation; an earlier request targets " +
                                "30 days, with backups expiring within a further 30 (NFR-10).",
                            tone = MessageTone.Info,
                        )
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        PrototypeNotice()
                    }
                }
            }
        }
    }
}

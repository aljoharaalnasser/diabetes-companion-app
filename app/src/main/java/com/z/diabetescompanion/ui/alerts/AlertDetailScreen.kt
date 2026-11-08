package com.z.diabetescompanion.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.alerts.AlertService
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.PatientResponse
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.AlertRepository
import com.z.diabetescompanion.domain.AlertRules
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AlertSeverityChip
import com.z.diabetescompanion.ui.components.AlertTimeline
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.EmergencyNotice
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.EscalationCountdown
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertDetailUiState(
    val isWorking: Boolean = false,
    val error: String? = null,
    val resolutionNote: String = "",
    val noteError: String? = null,
    val showResolveForm: Boolean = false,
)

class AlertDetailViewModel(
    private val patientUid: String,
    private val alertId: String,
    private val viewer: UserProfile,
    private val alertService: AlertService,
    alertRepository: AlertRepository,
) : ViewModel() {

    val alert = alertRepository.alert(patientUid, alertId)
        .catch { _uiState.update { s -> s.copy(error = it.toFirestoreMessage()) }; emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(AlertDetailUiState())
    val uiState = _uiState.asStateFlow()

    fun respond(response: PatientResponse) = run { alertService.respond(patientUid, alertId, response) }

    /** Demo control so a review does not need a real five-minute wait. */
    fun simulateEscalation() = run { alertService.escalateNow(patientUid, alertId) }

    fun onNoteChange(value: String) =
        _uiState.update { it.copy(resolutionNote = value, noteError = null, error = null) }

    fun showResolveForm(show: Boolean) = _uiState.update { it.copy(showResolveForm = show) }

    fun resolve() {
        val note = _uiState.value.resolutionNote
        AlertRules.resolutionNoteError(note)?.let { error ->
            _uiState.update { it.copy(noteError = error) }
            return
        }
        run { alertService.resolve(patientUid, alertId, viewer, note) }
    }

    private fun run(block: suspend () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.toFirestoreMessage()) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }
}

/**
 * One alert, shown to whoever opened it. The patient gets the four response actions; a
 * linked caregiver or doctor gets follow-up and resolution instead (FR-08, FR-10).
 */
@Composable
fun AlertDetailScreen(
    patientUid: String,
    alertId: String,
    onBack: () -> Unit,
    onContactDoctor: () -> Unit,
    onMessagePatient: (() -> Unit)? = null,
) {
    val session = currentSession()
    val viewModel: AlertDetailViewModel = viewModel(key = "alert_$alertId") {
        val c = container()
        AlertDetailViewModel(patientUid, alertId, session.profile, c.alertService, c.alertRepository)
    }
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPatient = session.uid == patientUid

    ScreenScaffold(title = "Alert", onBack = onBack) { padding ->
        val current = alert
        when {
            current == null && state.error != null -> ErrorState(state.error!!, Modifier.padding(padding))
            current == null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                ReadingCard(current, isPatient)
                Spacer(Modifier.height(16.dp))

                if (!current.isResolved) {
                    EscalationCountdown(current)
                    Spacer(Modifier.height(16.dp))
                }

                if (current.acknowledgedButOpen) {
                    InlineMessage(
                        message = "You selected \"${current.patientResponse?.label}\". " +
                            "This alert is acknowledged but not marked resolved.",
                        tone = MessageTone.Info,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (isPatient && !current.isResolved) {
                    PatientActions(
                        working = state.isWorking,
                        onRespond = viewModel::respond,
                        onContactDoctor = {
                            viewModel.respond(PatientResponse.ContactDoctor)
                            onContactDoctor()
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (!isPatient && !current.isResolved) {
                    CareTeamActions(
                        state = state,
                        onMessagePatient = onMessagePatient,
                        onShowResolve = { viewModel.showResolveForm(true) },
                        onNoteChange = viewModel::onNoteChange,
                        onResolve = viewModel::resolve,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (current.isResolved) {
                    ResolvedCard(current)
                    Spacer(Modifier.height(16.dp))
                }

                SectionHeader("Timeline")
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    AlertTimeline(current.events, Modifier.padding(16.dp))
                }

                Spacer(Modifier.height(16.dp))
                InlineMessage(message = state.error, tone = MessageTone.Error)

                if (!current.isResolved && AlertRules.nextStageOnTimeout(current) != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::simulateEscalation,
                        enabled = !state.isWorking,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.FastForward, contentDescription = null)
                        Text("Simulate 5 minutes passing", Modifier.padding(start = 8.dp))
                    }
                    Text(
                        "Demonstration control. Advances the escalation immediately so a review " +
                            "does not have to wait five real minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                EmergencyNotice()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ReadingCard(alert: Alert, isPatient: Boolean) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isPatient) "Your reading" else alert.patientName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AlertSeverityChip(alert.severity, alert.isResolved)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${alert.valueMgdl.toInt()}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "mg/dL",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                "${alert.statusLabel} · ${Format.dateTime(alert.recordedAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "This reading is outside the range your doctor set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PatientActions(
    working: Boolean,
    onRespond: (PatientResponse) -> Unit,
    onContactDoctor: () -> Unit,
) {
    Column {
        SectionHeader("How are you feeling?")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton("I'm OK", Icons.Rounded.CheckCircle, !working, Modifier.weight(1f)) {
                onRespond(PatientResponse.ImOk)
            }
            ActionButton("Treating", Icons.Rounded.Healing, !working, Modifier.weight(1f)) {
                onRespond(PatientResponse.Treating)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Need Help",
            onClick = { onRespond(PatientResponse.NeedHelp) },
            enabled = !working,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Need Help notifies your caregiver and your doctor straight away.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onContactDoctor,
            enabled = !working,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null)
            Text("Contact Doctor", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.height(54.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CareTeamActions(
    state: AlertDetailUiState,
    onMessagePatient: (() -> Unit)?,
    onShowResolve: () -> Unit,
    onNoteChange: (String) -> Unit,
    onResolve: () -> Unit,
) {
    Column {
        SectionHeader("Follow up")
        Spacer(Modifier.height(10.dp))
        if (onMessagePatient != null) {
            OutlinedButton(
                onClick = onMessagePatient,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Rounded.SupportAgent, contentDescription = null)
                Text("Open conversation", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!state.showResolveForm) {
            PrimaryButton(text = "Record resolution", onClick = onShowResolve, enabled = !state.isWorking)
        } else {
            AppTextField(
                value = state.resolutionNote,
                onValueChange = onNoteChange,
                label = "Follow-up note (required)",
                placeholder = "What did you do, and what happens next?",
                errorText = state.noteError,
                enabled = !state.isWorking,
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Resolve alert",
                onClick = onResolve,
                loading = state.isWorking,
                enabled = state.resolutionNote.isNotBlank(),
            )
        }
    }
}

@Composable
private fun ResolvedCard(alert: Alert) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Resolved", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "By ${alert.resolvedByName.ifBlank { "care team" }}" +
                    (alert.resolvedByRole?.let { " (${it.label})" } ?: "") +
                    (alert.resolvedAt?.let { " · ${Format.dateTime(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (alert.resolutionNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(alert.resolutionNote, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

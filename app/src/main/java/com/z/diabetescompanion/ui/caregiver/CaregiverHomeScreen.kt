package com.z.diabetescompanion.ui.caregiver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.AlertRepository
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.domain.GlucoseStatus
import com.z.diabetescompanion.domain.formatGlucose
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AlertListItem
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.components.StatusChip
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LinkedPatient(
    val link: CareLink,
    val latest: GlucoseReading?,
    val thresholds: Thresholds?,
    val recentDoses: List<MedicationLog>,
    val openAlerts: List<Alert>,
) {
    val status: GlucoseStatus
        get() = latest?.let { GlucoseStatus.of(it.valueMgdl, thresholds) } ?: GlucoseStatus.NoThresholds
}

data class CaregiverHomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val patients: List<LinkedPatient> = emptyList(),
)

/**
 * The caregiver's view of the patients who currently share with them (FR-13, S2-02).
 *
 * Everything is driven by the live consent query, so a revoked link removes the patient from
 * this screen at once and the server refuses any later read (NFR-11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaregiverHomeViewModel(
    memberUid: String,
    careLinkRepository: CareLinkRepository,
    private val glucoseRepository: GlucoseRepository,
    private val thresholdRepository: ThresholdRepository,
    private val medicationRepository: MedicationRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

    val uiState: StateFlow<CaregiverHomeUiState> =
        careLinkRepository.activeLinksForMember(memberUid)
            .map { links -> links.filter { it.memberRole == UserRole.Caregiver } }
            .flatMapLatest { links ->
                if (links.isEmpty()) flowOf(emptyList())
                else combine(links.map { detailsFor(it) }) { it.toList() }
            }
            .map { CaregiverHomeUiState(isLoading = false, patients = it) }
            .catch { emit(CaregiverHomeUiState(isLoading = false, error = it.toFirestoreMessage())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaregiverHomeUiState())

    private fun detailsFor(link: CareLink) = combine(
        glucoseRepository.latest(link.patientUid).catch { emit(null) },
        thresholdRepository.thresholds(link.patientUid).catch { emit(null) },
        medicationRepository.logs(link.patientUid, limit = 3).catch { emit(emptyList()) },
        alertRepository.alertsForPatient(link.patientUid, limit = 20).catch { emit(emptyList()) },
    ) { latest, thresholds, doses, alerts ->
        LinkedPatient(link, latest, thresholds, doses, alerts.filterNot { it.isResolved })
    }
}

@Composable
fun CaregiverHomeScreen(
    onOpenAlert: (patientUid: String, alertId: String) -> Unit,
    onMessageDoctor: (LinkedPatient) -> Unit,
) {
    val session = currentSession()
    val viewModel: CaregiverHomeViewModel = viewModel {
        val c = container()
        CaregiverHomeViewModel(
            session.uid, c.careLinkRepository, c.glucoseRepository,
            c.thresholdRepository, c.medicationRepository, c.alertRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Home") { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, Modifier.padding(padding))
            state.patients.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Groups,
                    title = "No linked patient",
                    description = "A patient needs to add you as their caregiver from their Care-team sharing screen. Until then you cannot see any records.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.patients.size) { index ->
                    PatientCard(
                        patient = state.patients[index],
                        onOpenAlert = onOpenAlert,
                        onMessageDoctor = { onMessageDoctor(state.patients[index]) },
                    )
                }
                item { PrototypeNotice() }
            }
        }
    }
}

@Composable
private fun PatientCard(
    patient: LinkedPatient,
    onOpenAlert: (String, String) -> Unit,
    onMessageDoctor: () -> Unit,
) {
    Column {
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
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            patient.link.patientName.split(' ').filter { it.isNotBlank() }
                                .take(2).joinToString("") { it.first().uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(patient.link.patientName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            patient.latest?.let {
                                "${it.valueMgdl.formatGlucose(GlucoseUnit.MgDl)} mg/dL · ${Format.relative(it.recordedAt)}"
                            } ?: "No readings recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (patient.latest != null) StatusChip(status = patient.status)
                }

                if (patient.recentDoses.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Medication,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Recent medication", style = MaterialTheme.typography.labelLarge)
                    }
                    patient.recentDoses.forEach { dose ->
                        Text(
                            "${dose.name} · ${Format.dose(dose.dose, dose.unit)} · ${Format.dateTime(dose.takenAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    LinkButton(text = "Contact the doctor", onClick = onMessageDoctor)
                }
            }
        }

        if (patient.openAlerts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionHeader("Needs attention")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                patient.openAlerts.forEach { alert ->
                    AlertListItem(alert = alert, onClick = { onOpenAlert(alert.patientUid, alert.id) })
                }
            }
        }
    }
}

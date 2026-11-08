package com.z.diabetescompanion.ui.doctor.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.data.repository.UserRepository
import com.z.diabetescompanion.domain.GlucoseStatus
import com.z.diabetescompanion.domain.Trend
import com.z.diabetescompanion.domain.TrendCalculator
import com.z.diabetescompanion.domain.formatGlucose
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.GlucoseChart
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.components.StatusChip
import com.z.diabetescompanion.ui.components.TrendLabel
import com.z.diabetescompanion.ui.container
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Date
import java.util.concurrent.TimeUnit

data class PatientDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val profile: UserProfile? = null,
    val readings: List<GlucoseReading> = emptyList(),
    val recentMedications: List<MedicationLog> = emptyList(),
    val thresholds: Thresholds? = null,
) {
    val latest: GlucoseReading? get() = readings.firstOrNull()
    val status: GlucoseStatus get() = latest?.let { GlucoseStatus.of(it.valueMgdl, thresholds) } ?: GlucoseStatus.NoThresholds
    val trend: Trend get() = TrendCalculator.of(readings)
    val chartReadings: List<GlucoseReading>
        get() {
            val start = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14))
            return readings.filter { it.recordedAt.after(start) }
        }
}

/** Doctor's view of one consented patient (FR-12 core): same records the patient sees (NFR-06). */
class PatientDetailViewModel(
    patientUid: String,
    userRepository: UserRepository,
    glucoseRepository: GlucoseRepository,
    medicationRepository: MedicationRepository,
    thresholdRepository: ThresholdRepository,
) : ViewModel() {

    val uiState: StateFlow<PatientDetailUiState> = combine(
        userRepository.profile(patientUid),
        glucoseRepository.readings(patientUid, limit = 200),
        medicationRepository.logs(patientUid, limit = 5),
        thresholdRepository.thresholds(patientUid),
    ) { profile, readings, meds, thresholds ->
        PatientDetailUiState(isLoading = false, profile = profile, readings = readings, recentMedications = meds, thresholds = thresholds)
    }
        .catch { emit(PatientDetailUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientDetailUiState())
}

@Composable
fun PatientDetailScreen(
    patientUid: String,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMedications: () -> Unit,
    onOpenThresholds: () -> Unit,
) {
    val viewModel: PatientDetailViewModel = viewModel(key = "patient_$patientUid") {
        val c = container()
        PatientDetailViewModel(patientUid, c.userRepository, c.glucoseRepository, c.medicationRepository, c.thresholdRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = state.profile?.displayName ?: "Patient", onBack = onBack) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error!!,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { LatestCard(state) }
                item { ThresholdsCard(state.thresholds, onOpenThresholds) }
                item { GlucoseCard(state, onOpenHistory) }
                item { MedicationCard(state, onOpenMedications) }
                item { ProfileCard(state.profile) }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
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
private fun LatestCard(state: PatientDetailUiState) {
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Latest reading", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (state.latest != null) StatusChip(status = state.status)
        }
        Spacer(Modifier.height(8.dp))
        val latest = state.latest
        if (latest == null) {
            Text("No readings recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(latest.valueMgdl.formatGlucose(GlucoseUnit.MgDl), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("mg/dL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                TrendLabel(trend = state.trend, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(
                "${Format.dateTime(latest.recordedAt)} · ${latest.source.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThresholdsCard(thresholds: Thresholds?, onOpen: () -> Unit) {
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Thresholds", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            LinkButton(text = if (thresholds == null) "Set" else "Edit", onClick = onOpen)
        }
        if (thresholds == null) {
            Text(
                "Not configured. The patient sees no status until you set a target range and critical thresholds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Target range: ${thresholds.targetLow}–${thresholds.targetHigh} mg/dL", style = MaterialTheme.typography.bodyMedium)
            Text("Critical: below ${thresholds.criticalLow} or above ${thresholds.criticalHigh} mg/dL", style = MaterialTheme.typography.bodyMedium)
            thresholds.updatedAt?.let {
                Text(
                    "Updated ${Format.dateFull(it)} by ${thresholds.updatedByName.ifBlank { "doctor" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GlucoseCard(state: PatientDetailUiState, onOpenHistory: () -> Unit) {
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last 14 days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            LinkButton(text = "Full history", onClick = onOpenHistory)
        }
        Spacer(Modifier.height(8.dp))
        if (state.chartReadings.isEmpty()) {
            Text("No readings in the last 14 days.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            GlucoseChart(
                readings = state.chartReadings,
                thresholds = state.thresholds,
                rangeStart = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)),
                rangeEnd = Date(),
                height = 180.dp,
            )
            Spacer(Modifier.height(6.dp))
            Text("${state.chartReadings.size} readings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MedicationCard(state: PatientDetailUiState, onOpen: () -> Unit) {
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent medication", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            LinkButton(text = "All", onClick = onOpen)
        }
        if (state.recentMedications.isEmpty()) {
            Text("No doses logged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.recentMedications.forEach { log ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${log.name} · ${Format.dose(log.dose, log.unit)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Format.dateTime(log.takenAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: UserProfile?) {
    if (profile == null) return
    DetailCard {
        SectionHeader("Patient context")
        Spacer(Modifier.height(6.dp))
        Text("Age: ${profile.age?.toString() ?: "not provided"}", style = MaterialTheme.typography.bodyMedium)
        Text("Diabetes type: ${profile.diabetesType.ifBlank { "not provided" }}", style = MaterialTheme.typography.bodyMedium)
        Text("Relevant history: ${profile.relevantHistory.ifBlank { "not provided" }}", style = MaterialTheme.typography.bodyMedium)
        Text("Care plan: ${profile.carePlan.ifBlank { "not provided" }}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


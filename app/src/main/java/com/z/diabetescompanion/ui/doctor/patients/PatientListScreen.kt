package com.z.diabetescompanion.ui.doctor.patients

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
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
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
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.domain.GlucoseStatus
import com.z.diabetescompanion.domain.formatGlucose
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.StatusChip
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PatientSummary(
    val link: CareLink,
    val latest: GlucoseReading?,
    val thresholds: Thresholds?,
) {
    val status: GlucoseStatus get() = latest?.let { GlucoseStatus.of(it.valueMgdl, thresholds) } ?: GlucoseStatus.NoThresholds
    val patientUid: String get() = link.patientUid
    val name: String get() = link.patientName
}

data class PatientListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val patients: List<PatientSummary> = emptyList(),
) {
    val filtered: List<PatientSummary>
        get() = if (query.isBlank()) patients
        else patients.filter { it.name.contains(query.trim(), ignoreCase = true) }
}

/**
 * Doctor's assigned patients (S1-05): only patients with an active consent link to this
 * doctor appear, which is also exactly what the server rules allow reading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PatientListViewModel(
    doctorUid: String,
    careLinkRepository: CareLinkRepository,
    private val glucoseRepository: GlucoseRepository,
    private val thresholdRepository: ThresholdRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val summaries = careLinkRepository.activeLinksForMember(doctorUid)
        .map { links -> links.filter { it.memberRole == UserRole.Doctor }.sortedBy { it.patientName.lowercase() } }
        .flatMapLatest { links ->
            if (links.isEmpty()) flowOf(emptyList())
            else combine(links.map { link -> summaryFor(link) }) { it.toList() }
        }

    val uiState: StateFlow<PatientListUiState> = combine(summaries, query) { patients, query ->
        PatientListUiState(isLoading = false, query = query, patients = patients)
    }
        .catch { emit(PatientListUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientListUiState())

    private fun summaryFor(link: CareLink) = combine(
        glucoseRepository.latest(link.patientUid).catch { emit(null) },
        thresholdRepository.thresholds(link.patientUid).catch { emit(null) },
    ) { latest, thresholds -> PatientSummary(link, latest, thresholds) }

    fun onQueryChange(value: String) { query.value = value }
}

@Composable
fun PatientListScreen(onOpenPatient: (String) -> Unit) {
    val session = currentSession()
    val viewModel: PatientListViewModel = viewModel {
        val c = container()
        PatientListViewModel(session.uid, c.careLinkRepository, c.glucoseRepository, c.thresholdRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Patients") { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AppTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = "Search patients",
                leadingIcon = Icons.Rounded.Search,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(message = state.error!!)
                state.patients.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.Groups,
                        title = "No assigned patients",
                        description = "Patients appear here once they grant you access from their Care-team sharing screen.",
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val list = state.filtered
                    if (list.isEmpty()) item { EmptyState(Icons.Rounded.Search, "No matches", "No patient matches \"${state.query}\".") }
                    items(list.size) { index -> PatientRow(list[index], onClick = { onOpenPatient(list[index].patientUid) }) }
                    item { Spacer(Modifier.height(8.dp)); PrototypeNotice() }
                }
            }
        }
    }
}

@Composable
private fun PatientRow(summary: PatientSummary, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    summary.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.name, style = MaterialTheme.typography.titleMedium)
                val latest = summary.latest
                Text(
                    if (latest == null) "No readings yet"
                    else "${latest.valueMgdl.formatGlucose(GlucoseUnit.MgDl)} mg/dL · ${Format.relative(latest.recordedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.thresholds == null) {
                    Text("Thresholds not set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (summary.latest != null) StatusChip(status = summary.status)
        }
    }
}

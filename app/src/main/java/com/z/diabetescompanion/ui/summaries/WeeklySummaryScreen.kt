package com.z.diabetescompanion.ui.summaries

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.ai.AiResult
import com.z.diabetescompanion.data.ai.SummaryNarrativeService
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.domain.WeeklySummary
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.GlucoseChart
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SummaryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val weeksAgo: Int = 0,
    val summary: WeeklySummary? = null,
    val narrative: String? = null,
    val isNarrating: Boolean = false,
    val narrativeError: String? = null,
)

/**
 * Weekly glucose and medication overview (FR-20, S2-09).
 *
 * The figures are computed from stored records; the AI only writes the optional narrative,
 * and the screen labels the two separately so nobody mistakes prose for data.
 */
class WeeklySummaryViewModel(
    patientUid: String,
    glucoseRepository: GlucoseRepository,
    medicationRepository: MedicationRepository,
    thresholdRepository: ThresholdRepository,
    private val narrativeService: SummaryNarrativeService,
) : ViewModel() {

    private val weeksAgo = MutableStateFlow(0)
    private val narrative = MutableStateFlow<String?>(null)
    private val narrating = MutableStateFlow(false)
    private val narrativeError = MutableStateFlow<String?>(null)

    val aiConfigured: Boolean get() = narrativeService.isConfigured

    private val records = combine(
        glucoseRepository.readings(patientUid, limit = 400),
        medicationRepository.logs(patientUid, limit = 300),
        thresholdRepository.thresholds(patientUid),
        weeksAgo,
    ) { readings, meds, thresholds, week ->
        WeeklySummary.build(readings, meds, thresholds, weeksAgo = week)
    }

    val uiState: StateFlow<SummaryUiState> = combine(
        records, narrative, narrating, narrativeError, weeksAgo,
    ) { summary, text, isNarrating, error, week ->
        SummaryUiState(
            isLoading = false,
            weeksAgo = week,
            summary = summary,
            narrative = text,
            isNarrating = isNarrating,
            narrativeError = error,
        )
    }
        .catch { emit(SummaryUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState())

    fun shiftWeek(delta: Int) {
        weeksAgo.update { (it + delta).coerceIn(0, 12) }
        // A different week means the old narrative no longer describes what is on screen.
        narrative.value = null
        narrativeError.value = null
    }

    fun writeNarrative(summary: WeeklySummary) {
        if (narrating.value) return
        narrating.value = true
        narrativeError.value = null
        viewModelScope.launch {
            when (val result = narrativeService.narrate(summary.toStatsBlock())) {
                is AiResult.Success -> narrative.value = result.text
                is AiResult.Error -> narrativeError.value = result.message
            }
            narrating.value = false
        }
    }
}

@Composable
fun WeeklySummaryScreen(
    patientUid: String,
    title: String = "Weekly summary",
    onBack: (() -> Unit)? = null,
) {
    val viewModel: WeeklySummaryViewModel = viewModel(key = "summary_$patientUid") {
        val c = container()
        WeeklySummaryViewModel(
            patientUid, c.glucoseRepository, c.medicationRepository,
            c.thresholdRepository, c.summaryNarrativeService,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = title, onBack = onBack) { padding ->
        val summary = state.summary
        when {
            state.isLoading || summary == null -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                WeekSwitcher(
                    label = summary.rangeLabel,
                    weeksAgo = state.weeksAgo,
                    onShift = viewModel::shiftWeek,
                )
                Spacer(Modifier.height(16.dp))

                if (summary.readingCount == 0) {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = "Nothing recorded this week",
                        description = "No glucose readings were saved in this date range, so there is nothing to summarise.",
                    )
                } else {
                    SectionHeader("Recorded figures")
                    Spacer(Modifier.height(10.dp))
                    StatsGrid(summary)

                    if (!summary.hasEnoughData) {
                        Spacer(Modifier.height(10.dp))
                        InlineMessage(
                            message = "Only ${summary.readingCount} reading(s) this week. That is not enough to describe a pattern, so treat these figures with care.",
                            tone = MessageTone.Warning,
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    SectionHeader("Glucose over the week")
                    Spacer(Modifier.height(10.dp))
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            GlucoseChart(
                                readings = summary.readings,
                                thresholds = summary.thresholds,
                                rangeStart = summary.start,
                                rangeEnd = summary.end,
                                height = 190.dp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (summary.thresholds != null)
                                    "Shaded band = target ${summary.thresholds.targetLow}–${summary.thresholds.targetHigh} mg/dL"
                                else "No target range set by the doctor yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    SectionHeader("Medication")
                    Spacer(Modifier.height(10.dp))
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${summary.doseCount} dose(s) logged this week",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (summary.medications.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                summary.medications.take(5).forEach { log ->
                                    Text(
                                        "${log.name} · ${log.kind.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    NarrativeSection(
                        state = state,
                        aiConfigured = viewModel.aiConfigured,
                        onWrite = { viewModel.writeNarrative(summary) },
                    )
                }

                Spacer(Modifier.height(20.dp))
                PrototypeNotice()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WeekSwitcher(label: String, weeksAgo: Int, onShift: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onShift(1) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Earlier week")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                when (weeksAgo) {
                    0 -> "This week"
                    1 -> "Last week"
                    else -> "$weeksAgo weeks ago"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onShift(-1) }, enabled = weeksAgo > 0) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Later week")
        }
    }
}

@Composable
private fun StatsGrid(summary: WeeklySummary) {
    val stats = listOf(
        "Readings" to summary.readingCount.toString(),
        "Average" to (summary.averageMgdl?.let { "$it mg/dL" } ?: "n/a"),
        "Time in range" to (summary.timeInRangePercent?.let { "$it%" } ?: "no target"),
        "Doses logged" to summary.doseCount.toString(),
        "Below target" to summary.lowEvents.toString(),
        "Above target" to summary.highEvents.toString(),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Clearly separated from the figures above, as the charter requires. */
@Composable
private fun NarrativeSection(state: SummaryUiState, aiConfigured: Boolean, onWrite: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Plain-language overview (AI generated)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Written by AI from the figures above. The figures are your recorded data; this paragraph is not.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        when {
            state.narrative != null -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(16.dp),
            ) {
                Text(
                    state.narrative,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            state.isNarrating -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Writing the overview", style = MaterialTheme.typography.bodySmall)
            }
            !aiConfigured -> InlineMessage(
                message = "AI is not configured, so the written overview is unavailable. The figures above are unaffected.",
                tone = MessageTone.Info,
            )
            else -> Column {
                state.narrativeError?.let {
                    InlineMessage(message = it, tone = MessageTone.Warning)
                    Spacer(Modifier.height(8.dp))
                }
                SecondaryButton(
                    text = if (state.narrativeError != null) "Try again" else "Write the overview",
                    onClick = onWrite,
                )
            }
        }
    }
}

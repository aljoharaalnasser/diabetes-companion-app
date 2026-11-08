package com.z.diabetescompanion.ui.patient.glucose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.domain.GlucoseStatus
import com.z.diabetescompanion.domain.Trend
import com.z.diabetescompanion.domain.TrendCalculator
import com.z.diabetescompanion.domain.formatGlucose
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.GlucoseChart
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.components.StatusChip
import com.z.diabetescompanion.ui.components.TrendLabel
import com.z.diabetescompanion.ui.components.color
import com.z.diabetescompanion.ui.components.containerColor
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit

enum class HistoryRange(val label: String, val days: Int?) {
    Week("7 days", 7),
    TwoWeeks("14 days", 14),
    Month("30 days", 30),
    Quarter("90 days", 90),
    All("All", null),
}

data class GlucoseHistoryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val range: HistoryRange = HistoryRange.Week,
    val readings: List<GlucoseReading> = emptyList(),
    val thresholds: Thresholds? = null,
    val trend: Trend = Trend.Unavailable,
    val totalCount: Int = 0,
) {
    val rangeStart: Date? get() = range.days?.let { Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it.toLong())) }
    val grouped: List<Pair<String, List<GlucoseReading>>>
        get() = readings.groupBy { Format.dayLabel(it.recordedAt) }.map { it.key to it.value }
}

/**
 * History with chart, filters and list (S1-04). Also reused by the doctor's patient
 * detail screen, which passes the patient's uid and [readOnly] = true.
 */
class GlucoseHistoryViewModel(
    private val patientUid: String,
    private val glucoseRepository: GlucoseRepository,
    thresholdRepository: ThresholdRepository,
) : ViewModel() {

    private val range = MutableStateFlow(HistoryRange.Week)
    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GlucoseHistoryUiState> = combine(
        glucoseRepository.readings(patientUid),
        thresholdRepository.thresholds(patientUid),
        range,
        actionError,
    ) { readings, thresholds, range, actionError ->
        val start = range.days?.let { Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it.toLong())) }
        GlucoseHistoryUiState(
            isLoading = false,
            error = actionError,
            range = range,
            readings = if (start == null) readings else readings.filter { it.recordedAt.after(start) },
            thresholds = thresholds,
            trend = TrendCalculator.of(readings),
            totalCount = readings.size,
        )
    }
        .catch { emit(GlucoseHistoryUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlucoseHistoryUiState())

    fun setRange(value: HistoryRange) { range.value = value }

    fun delete(reading: GlucoseReading) {
        viewModelScope.launch {
            try {
                glucoseRepository.delete(patientUid, reading.id)
            } catch (e: Exception) {
                actionError.value = e.toFirestoreMessage()
            }
        }
    }
}

@Composable
fun GlucoseHistoryScreen(
    patientUid: String,
    title: String = "History",
    readOnly: Boolean = false,
    onBack: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
) {
    val viewModel: GlucoseHistoryViewModel = viewModel(key = "history_$patientUid") {
        val c = container()
        GlucoseHistoryViewModel(patientUid, c.glucoseRepository, c.thresholdRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<GlucoseReading?>(null) }

    ScreenScaffold(
        title = title,
        onBack = onBack,
        floatingActionButton = {
            if (!readOnly && onAdd != null) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Add reading") },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null && state.totalCount == 0 -> ErrorState(message = state.error!!, modifier = Modifier.padding(padding))
            state.totalCount == 0 -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.ShowChart,
                    title = "No readings yet",
                    description = if (readOnly) "This patient hasn't recorded any glucose readings." else "Your readings and trend chart will appear here once you add some.",
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { RangeFilter(selected = state.range, onSelected = viewModel::setRange) }
                item { ChartCard(state) }
                if (state.readings.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.ShowChart,
                            title = "Nothing in this range",
                            description = "Try a longer date range.",
                        )
                    }
                }
                state.grouped.forEach { (day, readings) ->
                    item { SectionHeader(day, modifier = Modifier.padding(top = 4.dp)) }
                    items(readings.size) { index ->
                        ReadingRow(
                            reading = readings[index],
                            status = GlucoseStatus.of(readings[index].valueMgdl, state.thresholds),
                            onDelete = if (readOnly) null else ({ pendingDelete = readings[index] }),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { reading ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete reading?") },
            text = { Text("${reading.valueMgdl.formatGlucose(GlucoseUnit.MgDl)} mg/dL recorded ${Format.dateTime(reading.recordedAt)} will be removed.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(reading); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RangeFilter(selected: HistoryRange, onSelected: (HistoryRange) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        HistoryRange.entries.forEach { range ->
            FilterChip(selected = range == selected, onClick = { onSelected(range) }, label = { Text(range.label) })
        }
    }
}

@Composable
private fun ChartCard(state: GlucoseHistoryUiState) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${state.readings.size} readings", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.thresholds != null) "Shaded band = target ${state.thresholds.targetLow}–${state.thresholds.targetHigh} mg/dL"
                        else "No target range set by your doctor yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TrendLabel(trend = state.trend)
            }
            Spacer(Modifier.height(12.dp))
            if (state.readings.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("No data in this range", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                GlucoseChart(
                    readings = state.readings,
                    thresholds = state.thresholds,
                    rangeStart = state.rangeStart,
                    rangeEnd = if (state.rangeStart != null) Date() else null,
                )
            }
            if (state.trend == Trend.Unavailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Trend unavailable: needs at least ${TrendCalculator.MIN_READINGS} readings within 48 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadingRow(reading: GlucoseReading, status: GlucoseStatus, onDelete: (() -> Unit)?) {
    Card(
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(status.containerColor(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).background(status.color(), CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(reading.valueMgdl.formatGlucose(GlucoseUnit.MgDl), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(4.dp))
                    Text("mg/dL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                    if (reading.enteredUnit == GlucoseUnit.MmolL) {
                        Spacer(Modifier.width(6.dp))
                        Text("(${reading.enteredValue.formatGlucose(GlucoseUnit.MmolL)} mmol/L)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                Text(
                    buildString {
                        append(Format.time(reading.recordedAt))
                        append(" · ")
                        append(reading.source.label)
                        if (reading.note.isNotBlank()) append(" · ").append(reading.note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status != GlucoseStatus.NoThresholds) StatusChip(status = status)
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reading", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}


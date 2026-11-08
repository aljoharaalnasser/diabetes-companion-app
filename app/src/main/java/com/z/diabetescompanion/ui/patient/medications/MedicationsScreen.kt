package com.z.diabetescompanion.ui.patient.medications

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.MedicationKind
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.Reminder
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.data.repository.ReminderRepository
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MedicationsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val logs: List<MedicationLog> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
) {
    val groupedLogs: List<Pair<String, List<MedicationLog>>>
        get() = logs.groupBy { Format.dayLabel(it.takenAt) }.map { it.key to it.value }
}

/** Medication history (S1-06) and reminders (S1-07). Reused read-only by the doctor. */
class MedicationsViewModel(
    private val patientUid: String,
    private val medicationRepository: MedicationRepository,
    private val reminderRepository: ReminderRepository,
    /** Reminders are private to the patient (rules deny everyone else), so care-team views skip them. */
    includeReminders: Boolean,
) : ViewModel() {

    private val actionError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MedicationsUiState> = combine(
        medicationRepository.logs(patientUid),
        if (includeReminders) reminderRepository.reminders(patientUid) else flowOf(emptyList()),
        actionError,
    ) { logs, reminders, error ->
        MedicationsUiState(isLoading = false, error = error, logs = logs, reminders = reminders)
    }
        .catch { emit(MedicationsUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationsUiState())

    fun deleteLog(log: MedicationLog) = run { medicationRepository.delete(patientUid, log.id) }
    fun deleteReminder(reminder: Reminder) = run { reminderRepository.delete(patientUid, reminder.id) }
    fun setReminderEnabled(reminder: Reminder, enabled: Boolean) =
        run { reminderRepository.setEnabled(patientUid, reminder.id, enabled) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                actionError.value = e.toFirestoreMessage()
            }
        }
    }
}

private enum class MedTab(val label: String) { History("History"), Reminders("Reminders") }

@Composable
fun MedicationsScreen(
    patientUid: String,
    title: String = "Medications",
    readOnly: Boolean = false,
    initialTab: Int = 0,
    onBack: (() -> Unit)? = null,
    onAddLog: (() -> Unit)? = null,
    onAddReminder: (() -> Unit)? = null,
    onEditReminder: ((Reminder) -> Unit)? = null,
) {
    val viewModel: MedicationsViewModel = viewModel(key = "meds_$patientUid") {
        val c = container()
        MedicationsViewModel(patientUid, c.medicationRepository, c.reminderRepository, includeReminders = !readOnly)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MedTab.entries[initialTab.coerceIn(0, 1)]) }
    val tabs = if (readOnly) listOf(MedTab.History) else MedTab.entries
    var pendingLogDelete by remember { mutableStateOf<MedicationLog?>(null) }
    var pendingReminderDelete by remember { mutableStateOf<Reminder?>(null) }

    ScreenScaffold(
        title = title,
        onBack = onBack,
        floatingActionButton = {
            if (!readOnly) {
                when (tab) {
                    MedTab.History -> if (onAddLog != null) ExtendedFloatingActionButton(
                        onClick = onAddLog,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Log dose") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    MedTab.Reminders -> if (onAddReminder != null) ExtendedFloatingActionButton(
                        onClick = onAddReminder,
                        icon = { Icon(Icons.Rounded.Alarm, contentDescription = null) },
                        text = { Text("New reminder") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tabs.size > 1) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    tabs.forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = t == tab,
                            onClick = { tab = t },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            when {
                state.isLoading -> LoadingState()
                state.error != null && state.logs.isEmpty() && state.reminders.isEmpty() -> ErrorState(message = state.error!!)
                tab == MedTab.History -> HistoryList(state, readOnly, onDelete = { pendingLogDelete = it })
                else -> RemindersList(
                    state = state,
                    onToggle = viewModel::setReminderEnabled,
                    onEdit = onEditReminder,
                    onDelete = { pendingReminderDelete = it },
                )
            }
        }
    }

    pendingLogDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { pendingLogDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("${log.name} ${Format.dose(log.dose, log.unit)} taken ${Format.dateTime(log.takenAt)} will be removed.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteLog(log); pendingLogDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingLogDelete = null }) { Text("Cancel") } },
        )
    }
    pendingReminderDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { pendingReminderDelete = null },
            title = { Text("Delete reminder?") },
            text = { Text("The ${Format.clock(reminder.hour, reminder.minute)} reminder for ${reminder.medicationName} will be removed.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteReminder(reminder); pendingReminderDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingReminderDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryList(state: MedicationsUiState, readOnly: Boolean, onDelete: (MedicationLog) -> Unit) {
    if (state.logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Medication,
                title = "No doses logged",
                description = if (readOnly) "This patient hasn't logged any medication yet." else "Log each medication or insulin dose to build your history.",
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.error != null) item { InlineMessage(message = state.error, tone = MessageTone.Error) }
        state.groupedLogs.forEach { (day, logs) ->
            item { SectionHeader(day, modifier = Modifier.padding(top = 6.dp)) }
            items(logs.size) { index -> LogRow(logs[index], onDelete = if (readOnly) null else ({ onDelete(logs[index]) })) }
        }
    }
}

@Composable
private fun LogRow(log: MedicationLog, onDelete: (() -> Unit)?) {
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
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (log.kind == MedicationKind.Insulin) Icons.Rounded.Vaccines else Icons.Rounded.Medication,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${log.name} · ${Format.dose(log.dose, log.unit)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(log.kind.label); append(" · "); append(Format.time(log.takenAt))
                        if (log.note.isNotBlank()) append(" · ").append(log.note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun RemindersList(
    state: MedicationsUiState,
    onToggle: (Reminder, Boolean) -> Unit,
    onEdit: ((Reminder) -> Unit)?,
    onDelete: (Reminder) -> Unit,
) {
    if (state.reminders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Alarm,
                title = "No reminders",
                description = "Create a reminder with a time and days, and we'll notify you on schedule.",
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.error != null) item { InlineMessage(message = state.error, tone = MessageTone.Error) }
        items(state.reminders.size) { index ->
            val reminder = state.reminders[index]
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.clickable(enabled = onEdit != null) { onEdit?.invoke(reminder) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(Format.clock(reminder.hour, reminder.minute), style = MaterialTheme.typography.headlineSmall)
                        Text(reminder.medicationName, style = MaterialTheme.typography.titleSmall)
                        Text(Format.days(reminder.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = reminder.enabled, onCheckedChange = { onToggle(reminder, it) })
                    IconButton(onClick = { onDelete(reminder) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reminder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

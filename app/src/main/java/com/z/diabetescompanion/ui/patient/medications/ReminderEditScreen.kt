package com.z.diabetescompanion.ui.patient.medications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.Reminder
import com.z.diabetescompanion.data.reminders.ReminderReceiver
import com.z.diabetescompanion.data.reminders.ReminderScheduler
import com.z.diabetescompanion.data.repository.ReminderRepository
import com.z.diabetescompanion.domain.MedicationValidation
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.components.TimeField
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.UUID

data class ReminderEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val hour: Int = 8,
    val minute: Int = 0,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val nameError: String? = null,
    val formError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !isSaving && name.isNotBlank()
    val nextTriggerLabel: String
        get() {
            val next = ReminderScheduler.nextTriggerMillis(toReminder())
            return "${Format.dayLabel(Date(next))} at ${Format.time(Date(next))}"
        }

    fun toReminder() = Reminder(
        id = id, medicationName = name.trim(), hour = hour, minute = minute,
        days = days.sorted(), enabled = enabled,
    )
}

class ReminderEditViewModel(
    private val uid: String,
    private val reminderId: String?,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderEditUiState(isLoading = reminderId != null))
    val uiState = _uiState.asStateFlow()

    init {
        if (reminderId != null) {
            viewModelScope.launch {
                val existing = runCatching { reminderRepository.reminders(uid).first() }.getOrNull()
                    ?.firstOrNull { it.id == reminderId }
                _uiState.update {
                    if (existing == null) it.copy(isLoading = false, formError = "Reminder not found.")
                    else it.copy(
                        isLoading = false, isNew = false, id = existing.id, name = existing.medicationName,
                        hour = existing.hour, minute = existing.minute, days = existing.days.toSet(), enabled = existing.enabled,
                    )
                }
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, nameError = null, formError = null) }
    fun onTimeChange(hour: Int, minute: Int) = _uiState.update { it.copy(hour = hour, minute = minute) }
    fun onEnabledChange(v: Boolean) = _uiState.update { it.copy(enabled = v) }
    fun toggleDay(day: Int) = _uiState.update {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }
    fun setEveryDay() = _uiState.update { it.copy(days = emptySet()) }

    fun save() {
        val s = _uiState.value
        if (s.isSaving) return
        MedicationValidation.nameError(s.name)?.let { err ->
            _uiState.update { it.copy(nameError = err) }
            return
        }
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                val reminder = s.toReminder()
                reminderRepository.save(uid, reminder, isNew = s.isNew)
                reminderScheduler.schedule(reminder)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, formError = e.toFirestoreMessage()) }
            }
        }
    }

    fun delete() {
        val s = _uiState.value
        viewModelScope.launch {
            try {
                reminderRepository.delete(uid, s.id)
                reminderScheduler.cancel(s.id)
                _uiState.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(formError = e.toFirestoreMessage()) }
            }
        }
    }
}

private val weekDays = listOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat", Calendar.SUNDAY to "Sun",
)

@Composable
fun ReminderEditScreen(
    reminderId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val session = currentSession()
    val viewModel: ReminderEditViewModel = viewModel(key = "reminder_${reminderId ?: "new"}") {
        val c = container()
        ReminderEditViewModel(session.uid, reminderId, c.reminderRepository, c.reminderScheduler)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(!ReminderReceiver.hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permissionDenied) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (state.saved) LaunchedEffect(Unit) { onSaved() }

    ScreenScaffold(title = if (state.isNew) "New reminder" else "Edit reminder", onBack = onBack) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (permissionDenied) {
                InlineMessage(
                    message = "Notifications are off for this app, so reminders will be saved but won't alert you. Allow notifications to change that.",
                    tone = MessageTone.Warning,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LinkButton(text = "Allow notifications", onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!session.preferences.notificationsEnabled) {
                InlineMessage(message = "Reminder notifications are switched off in Settings.", tone = MessageTone.Info)
                Spacer(Modifier.height(12.dp))
            }

            AppTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Medication or insulin",
                placeholder = "e.g. Metformin 500 mg",
                leadingIcon = Icons.Rounded.Medication,
                errorText = state.nameError,
                enabled = !state.isSaving,
            )
            Spacer(Modifier.height(16.dp))
            TimeField(hour = state.hour, minute = state.minute, onValueChange = viewModel::onTimeChange, enabled = !state.isSaving)
            Spacer(Modifier.height(20.dp))

            Text("Repeat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = state.days.isEmpty(), onClick = viewModel::setEveryDay, label = { Text("Every day") })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { (day, label) ->
                    FilterChip(
                        selected = day in state.days,
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enabled", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.enabled) "Next: ${state.nextTriggerLabel}" else "Paused. Turn on to schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = viewModel::onEnabledChange)
            }

            Spacer(Modifier.height(24.dp))
            InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))
            PrimaryButton(text = "Save reminder", onClick = viewModel::save, loading = state.isSaving, enabled = state.canSave)
            if (!state.isNew) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Delete reminder", onClick = viewModel::delete, enabled = !state.isSaving)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

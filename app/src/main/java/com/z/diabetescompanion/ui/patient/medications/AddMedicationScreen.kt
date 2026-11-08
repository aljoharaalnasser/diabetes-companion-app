package com.z.diabetescompanion.ui.patient.medications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.MedicationKind
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.domain.MedicationValidation
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.ChoiceChips
import com.z.diabetescompanion.ui.components.DateTimeField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

data class AddMedicationUiState(
    val name: String = "",
    val doseText: String = "",
    val unit: String = "mg",
    val kind: MedicationKind = MedicationKind.Medication,
    val takenAt: Date = Date(),
    val note: String = "",
    val nameError: String? = null,
    val doseError: String? = null,
    val timeError: String? = null,
    val formError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !isSaving && name.isNotBlank() && doseText.isNotBlank()
}

class AddMedicationViewModel(
    private val uid: String,
    private val medicationRepository: MedicationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMedicationUiState())
    val uiState = _uiState.asStateFlow()

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, nameError = null, formError = null) }
    fun onDoseChange(v: String) = _uiState.update { it.copy(doseText = v, doseError = null, formError = null) }
    fun onUnitChange(v: String) = _uiState.update { it.copy(unit = v) }
    fun onKindChange(v: MedicationKind) = _uiState.update {
        it.copy(kind = v, unit = if (v == MedicationKind.Insulin) "units" else if (it.unit == "units") "mg" else it.unit)
    }
    fun onTimeChange(v: Date) = _uiState.update { it.copy(takenAt = v, timeError = null) }
    fun onNoteChange(v: String) = _uiState.update { it.copy(note = v.take(200)) }

    fun save() {
        val s = _uiState.value
        if (s.isSaving) return
        val nameError = MedicationValidation.nameError(s.name)
        val doseError = MedicationValidation.doseError(s.doseText)
        val timeError = MedicationValidation.takenAtError(s.takenAt)
        if (nameError != null || doseError != null || timeError != null) {
            _uiState.update { it.copy(nameError = nameError, doseError = doseError, timeError = timeError) }
            return
        }
        val log = MedicationLog(
            id = "${s.takenAt.time / 1000}_${UUID.randomUUID().toString().take(8)}",
            name = s.name.trim(),
            dose = s.doseText.replace(',', '.').toDouble(),
            unit = s.unit,
            kind = s.kind,
            takenAt = s.takenAt,
            note = s.note.trim(),
        )
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                medicationRepository.save(uid, log)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, formError = e.toFirestoreMessage()) }
            }
        }
    }
}

@Composable
fun AddMedicationScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val session = currentSession()
    val viewModel: AddMedicationViewModel = viewModel { AddMedicationViewModel(session.uid, container().medicationRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.saved) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onSaved() }
    }

    ScreenScaffold(title = "Log a dose", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(options = MedicationKind.entries, selected = state.kind, onSelected = viewModel::onKindChange, label = { it.label }, enabled = !state.isSaving)
            Spacer(Modifier.height(16.dp))
            AppTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = if (state.kind == MedicationKind.Insulin) "Insulin name" else "Medication name",
                placeholder = if (state.kind == MedicationKind.Insulin) "e.g. Lantus" else "e.g. Metformin",
                leadingIcon = Icons.Rounded.Medication,
                errorText = state.nameError,
                enabled = !state.isSaving,
            )
            Spacer(Modifier.height(14.dp))
            AppTextField(
                value = state.doseText,
                onValueChange = viewModel::onDoseChange,
                label = "Dose",
                leadingIcon = Icons.Rounded.Numbers,
                errorText = state.doseError,
                enabled = !state.isSaving,
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(10.dp))
            Text("Unit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(options = MedicationValidation.doseUnits, selected = state.unit, onSelected = viewModel::onUnitChange, label = { it }, enabled = !state.isSaving)
            Spacer(Modifier.height(16.dp))
            Text("Time taken", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            DateTimeField(value = state.takenAt, onValueChange = viewModel::onTimeChange, enabled = !state.isSaving, errorText = state.timeError)
            Spacer(Modifier.height(16.dp))
            AppTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = "Note (optional)",
                enabled = !state.isSaving,
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(20.dp))
            InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))
            PrimaryButton(text = "Save dose", onClick = viewModel::save, loading = state.isSaving, enabled = state.canSave)
            Spacer(Modifier.height(24.dp))
        }
    }
}

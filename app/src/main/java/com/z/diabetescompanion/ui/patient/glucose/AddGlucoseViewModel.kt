package com.z.diabetescompanion.ui.patient.glucose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.ReadingSource
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.domain.DemoReadingGenerator
import com.z.diabetescompanion.domain.GlucoseValidation
import com.z.diabetescompanion.domain.readingId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class AddGlucoseUiState(
    val valueText: String = "",
    val unit: GlucoseUnit = GlucoseUnit.MgDl,
    val recordedAt: Date = Date(),
    val note: String = "",
    val valueError: String? = null,
    val timeError: String? = null,
    val formError: String? = null,
    val isSaving: Boolean = false,
    /** Non-null after a successful save: the screen swaps to the confirmation. */
    val saved: GlucoseReading? = null,
) {
    val canSave: Boolean get() = !isSaving && valueText.isNotBlank()
}

/** Manual entry + demo generation (S1-03). */
class AddGlucoseViewModel(
    private val uid: String,
    private val glucoseRepository: GlucoseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddGlucoseUiState())
    val uiState: StateFlow<AddGlucoseUiState> = _uiState.asStateFlow()

    fun onValueChange(value: String) = _uiState.update { it.copy(valueText = value, valueError = null, formError = null) }
    fun onUnitChange(unit: GlucoseUnit) = _uiState.update { it.copy(unit = unit, valueError = null) }
    fun onTimeChange(date: Date) = _uiState.update { it.copy(recordedAt = date, timeError = null) }
    fun onNoteChange(note: String) = _uiState.update { it.copy(note = note.take(200)) }

    fun reset() = _uiState.update { AddGlucoseUiState() }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        val valueError = GlucoseValidation.valueError(state.valueText, state.unit)
        val timeError = GlucoseValidation.timeError(state.recordedAt)
        if (valueError != null || timeError != null) {
            _uiState.update { it.copy(valueError = valueError, timeError = timeError) }
            return
        }
        val entered = state.valueText.replace(',', '.').toDouble()
        val mgdl = state.unit.toMgdl(entered)
        val reading = GlucoseReading(
            id = readingId(state.recordedAt, mgdl),
            valueMgdl = mgdl,
            enteredValue = entered,
            enteredUnit = state.unit,
            source = ReadingSource.Manual,
            recordedAt = state.recordedAt,
            note = state.note.trim(),
        )
        persist(reading)
    }

    /** Charter FR-03: button-triggered demonstration reading, clearly labelled as such. */
    fun generateDemo() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            val last = glucoseRepository.latest(uid).catch { emit(null) }.first()
            persist(DemoReadingGenerator.next(last?.valueMgdl))
        }
    }

    private fun persist(reading: GlucoseReading) {
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                glucoseRepository.save(uid, reading)
                _uiState.update { it.copy(isSaving = false, saved = reading) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, formError = e.toFirestoreMessage()) }
            }
        }
    }
}

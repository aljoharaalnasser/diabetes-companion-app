package com.z.diabetescompanion.ui.doctor.patients

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.domain.ThresholdValidation
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThresholdsUiState(
    val isLoading: Boolean = true,
    val targetLow: String = "",
    val targetHigh: String = "",
    val criticalLow: String = "",
    val criticalHigh: String = "",
    val targetLowError: String? = null,
    val targetHighError: String? = null,
    val criticalLowError: String? = null,
    val criticalHighError: String? = null,
    val formError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = !isSaving && listOf(targetLow, targetHigh, criticalLow, criticalHigh).all { it.isNotBlank() }
}

/**
 * Doctor-defined target and critical thresholds (FR-05). No defaults are pre-filled:
 * the charter says not to invent clinical values, so the doctor types every number.
 */
class ThresholdsViewModel(
    private val patientUid: String,
    private val doctor: UserProfile,
    private val thresholdRepository: ThresholdRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThresholdsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = thresholdRepository.thresholds(patientUid).catch { emit(null) }.first()
            _uiState.update {
                if (existing == null) it.copy(isLoading = false)
                else it.copy(
                    isLoading = false,
                    targetLow = existing.targetLow.toString(),
                    targetHigh = existing.targetHigh.toString(),
                    criticalLow = existing.criticalLow.toString(),
                    criticalHigh = existing.criticalHigh.toString(),
                )
            }
        }
    }

    fun onTargetLow(v: String) = _uiState.update { it.copy(targetLow = v.digits(), targetLowError = null, formError = null) }
    fun onTargetHigh(v: String) = _uiState.update { it.copy(targetHigh = v.digits(), targetHighError = null, formError = null) }
    fun onCriticalLow(v: String) = _uiState.update { it.copy(criticalLow = v.digits(), criticalLowError = null, formError = null) }
    fun onCriticalHigh(v: String) = _uiState.update { it.copy(criticalHigh = v.digits(), criticalHighError = null, formError = null) }

    private fun String.digits() = filter { it.isDigit() }.take(3)

    fun save() {
        val s = _uiState.value
        if (s.isSaving) return
        val errors = listOf(s.targetLow, s.targetHigh, s.criticalLow, s.criticalHigh).map(ThresholdValidation::fieldError)
        if (errors.any { it != null }) {
            _uiState.update {
                it.copy(targetLowError = errors[0], targetHighError = errors[1], criticalLowError = errors[2], criticalHighError = errors[3])
            }
            return
        }
        val tl = s.targetLow.toInt(); val th = s.targetHigh.toInt(); val cl = s.criticalLow.toInt(); val ch = s.criticalHigh.toInt()
        ThresholdValidation.orderingError(cl, tl, th, ch)?.let { err ->
            _uiState.update { it.copy(formError = err) }
            return
        }
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                thresholdRepository.save(
                    patientUid,
                    Thresholds(targetLow = tl, targetHigh = th, criticalLow = cl, criticalHigh = ch, updatedByUid = doctor.uid, updatedByName = doctor.displayName),
                )
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
fun ThresholdsScreen(patientUid: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val session = currentSession()
    val viewModel: ThresholdsViewModel = viewModel(key = "thresholds_$patientUid") {
        ThresholdsViewModel(patientUid, session.profile, container().thresholdRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.saved) LaunchedEffect(Unit) { onSaved() }

    ScreenScaffold(title = "Glucose thresholds", onBack = onBack) { padding ->
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
            Text(
                "All values in mg/dL. Readings inside the target range show as Ideal; outside it as Low or High. Critical thresholds will trigger immediate caregiver alerts from Sprint 2.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            SectionHeader("Target range")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = state.targetLow, onValueChange = viewModel::onTargetLow, label = "Low",
                    errorText = state.targetLowError, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), enabled = !state.isSaving,
                )
                AppTextField(
                    value = state.targetHigh, onValueChange = viewModel::onTargetHigh, label = "High",
                    errorText = state.targetHighError, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), enabled = !state.isSaving,
                )
            }
            Spacer(Modifier.height(20.dp))
            SectionHeader("Critical thresholds")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = state.criticalLow, onValueChange = viewModel::onCriticalLow, label = "Critical low",
                    errorText = state.criticalLowError, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), enabled = !state.isSaving,
                )
                AppTextField(
                    value = state.criticalHigh, onValueChange = viewModel::onCriticalHigh, label = "Critical high",
                    errorText = state.criticalHighError, keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f), enabled = !state.isSaving,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Required order: critical low < target low < target high < critical high.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))
            PrimaryButton(text = "Save thresholds", onClick = viewModel::save, loading = state.isSaving, enabled = state.canSave)
            Spacer(Modifier.height(20.dp))
            PrototypeNotice()
            Spacer(Modifier.height(24.dp))
        }
    }
}

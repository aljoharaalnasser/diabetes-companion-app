package com.z.diabetescompanion.ui.doctor.patients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.FirestoreTimeoutException
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.DoseSuggestion
import com.z.diabetescompanion.data.model.DoseSuggestionStatus
import com.z.diabetescompanion.data.model.IcrEnteredBy
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.IcrStatus
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.repository.DoseSuggestionRepository
import com.z.diabetescompanion.data.repository.IcrRepository
import com.z.diabetescompanion.domain.IcrPolicy
import com.z.diabetescompanion.domain.MealInsulinValidation
import com.z.diabetescompanion.domain.formatGrams
import com.z.diabetescompanion.domain.formatUnits
import com.z.diabetescompanion.domain.icrLabel
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

// ---------------------------------------------------------------------------
// Doctor: ICR on the care-settings (thresholds) screen
// ---------------------------------------------------------------------------

data class DoctorIcrUiState(
    val isLoading: Boolean = true,
    val versions: List<IcrSetting> = emptyList(),
    val icrText: String = "",
    val icrError: String? = null,
    val formError: String? = null,
    val savedMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val newest: IcrSetting? get() = IcrPolicy.newest(versions)
    val awaitingConfirmation: IcrSetting? get() = IcrPolicy.awaitingConfirmation(versions)
}

/**
 * The linked doctor sets or confirms the ICR. Every save creates a new CONFIRMED version; old
 * versions are never changed, so past dose suggestions keep the ratio they were calculated with.
 */
class DoctorIcrViewModel(
    private val patientUid: String,
    private val doctor: UserProfile,
    private val icrRepository: IcrRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorIcrUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            icrRepository.versions(patientUid)
                .catch { e -> _uiState.update { it.copy(isLoading = false, formError = e.toFirestoreMessage()) } }
                .collect { versions ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            versions = versions,
                            // Pre-fill with the current value only; never invent a starting ratio.
                            icrText = if (it.isLoading) IcrPolicy.newest(versions)?.gramsPerUnit?.let(::formatGrams).orEmpty() else it.icrText,
                        )
                    }
                }
        }
    }

    fun onIcrChange(value: String) = _uiState.update {
        it.copy(icrText = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5), icrError = null, formError = null, savedMessage = null)
    }

    /** Saves the typed value as a new CONFIRMED version entered by the doctor. */
    fun save() {
        val s = _uiState.value
        if (s.isSaving) return
        MealInsulinValidation.icrError(s.icrText)?.let { err ->
            _uiState.update { it.copy(icrError = err) }
            return
        }
        val grams = MealInsulinValidation.parse(s.icrText) ?: return
        create(grams, enteredBy = IcrEnteredBy.Doctor, confirmsIcrId = "")
    }

    /** Confirms the patient's pending value by creating a CONFIRMED copy of it. */
    fun confirmPending() {
        val s = _uiState.value
        val pending = s.awaitingConfirmation ?: return
        if (s.isSaving) return
        create(pending.gramsPerUnit, enteredBy = IcrEnteredBy.Patient, confirmsIcrId = pending.icrId)
    }

    private fun create(grams: Double, enteredBy: IcrEnteredBy, confirmsIcrId: String) {
        val setting = IcrSetting(
            icrId = UUID.randomUUID().toString(),
            patientId = patientUid,
            gramsPerUnit = grams,
            status = IcrStatus.Confirmed,
            enteredBy = enteredBy,
            version = IcrPolicy.nextVersion(_uiState.value.versions),
            confirmedByDoctorId = doctor.uid,
            confirmedByDoctorName = doctor.displayName,
            confirmsIcrId = confirmsIcrId,
            effectiveFrom = Date(),
        )
        _uiState.update { it.copy(isSaving = true, formError = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                try {
                    icrRepository.create(setting)
                } catch (e: FirestoreTimeoutException) {
                    // Queued offline; Firestore syncs it later. Retrying would add a duplicate version.
                }
                _uiState.update {
                    it.copy(isSaving = false, icrText = formatGrams(grams), savedMessage = "Saved as version ${setting.version}: ${icrLabel(grams)}.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, formError = e.toFirestoreMessage()) }
            }
        }
    }
}

/** ICR block embedded in the doctor's thresholds screen. Saves independently of thresholds. */
@Composable
fun IcrSettingsSection(patientUid: String, doctor: UserProfile, modifier: Modifier = Modifier) {
    val viewModel: DoctorIcrViewModel = viewModel(key = "icr_$patientUid") {
        DoctorIcrViewModel(patientUid, doctor, container().icrRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader("Insulin-to-carb ratio (ICR)")
        Spacer(Modifier.height(8.dp))
        Text(
            "Used by the patient's meal insulin calculator: units = confirmed carbs / ICR, rounded down. " +
                "No dose is calculated until you confirm a ratio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        val newest = state.newest
        when {
            state.isLoading -> Text("Loading...", style = MaterialTheme.typography.bodySmall)
            newest == null -> Text("No ICR recorded yet.", style = MaterialTheme.typography.bodyMedium)
            else -> IcrVersionLine(newest, prefix = "Current")
        }

        state.awaitingConfirmation?.let { pending ->
            Spacer(Modifier.height(10.dp))
            InlineMessage(
                message = "The patient entered ${icrLabel(pending.gramsPerUnit)}. It needs your confirmation before a dose can be calculated.",
                tone = MessageTone.Warning,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Confirm patient value (${formatGrams(pending.gramsPerUnit)} g)",
                onClick = viewModel::confirmPending,
                enabled = !state.isSaving,
            )
        }

        Spacer(Modifier.height(14.dp))
        AppTextField(
            value = state.icrText,
            onValueChange = viewModel::onIcrChange,
            label = "Grams of carbs per 1 unit",
            errorText = state.icrError,
            enabled = !state.isSaving && !state.isLoading,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        )
        Text(
            MealInsulinValidation.parse(state.icrText)?.takeIf { it > 0 }?.let { icrLabel(it) }
                ?: "Shown to the patient as \"1 unit for every X g of carbs\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        Spacer(Modifier.height(12.dp))
        InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 8.dp))
        InlineMessage(message = state.savedMessage, tone = MessageTone.Success, modifier = Modifier.padding(bottom = 8.dp))
        PrimaryButton(
            text = "Save and confirm ICR",
            onClick = viewModel::save,
            loading = state.isSaving,
            enabled = state.icrText.isNotBlank() && !state.isLoading,
        )

        if (state.versions.size > 1) {
            Spacer(Modifier.height(16.dp))
            Text("History", style = MaterialTheme.typography.titleSmall)
            state.versions.drop(1).take(5).forEach { IcrVersionLine(it, prefix = "v${it.version}") }
        }
    }
}

@Composable
private fun IcrVersionLine(setting: IcrSetting, prefix: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            "$prefix: ${icrLabel(setting.gramsPerUnit)} (v${setting.version})",
            style = MaterialTheme.typography.bodyMedium,
        )
        val who = if (setting.isConfirmed) {
            "Confirmed by ${setting.confirmedByDoctorName.ifBlank { "doctor" }}"
        } else {
            "Entered by patient, not confirmed"
        }
        Text(
            "$who, ${Format.dateFull(setting.effectiveFrom)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (setting.isConfirmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}

// ---------------------------------------------------------------------------
// Doctor: patient detail cards
// ---------------------------------------------------------------------------

data class PatientMealInsulinUiState(
    val isLoading: Boolean = true,
    val icrVersions: List<IcrSetting> = emptyList(),
    val suggestions: List<DoseSuggestion> = emptyList(),
    val error: String? = null,
)

/** Separate from PatientDetailViewModel so a rules or index problem here can't blank that screen. */
class PatientMealInsulinViewModel(
    patientUid: String,
    icrRepository: IcrRepository,
    doseSuggestionRepository: DoseSuggestionRepository,
) : ViewModel() {

    val uiState: StateFlow<PatientMealInsulinUiState> = combine(
        icrRepository.versions(patientUid),
        doseSuggestionRepository.suggestions(patientUid, limit = 50),
    ) { versions, suggestions ->
        PatientMealInsulinUiState(isLoading = false, icrVersions = versions, suggestions = suggestions)
    }
        .catch { emit(PatientMealInsulinUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatientMealInsulinUiState())
}

@Composable
fun rememberPatientMealInsulinState(patientUid: String): PatientMealInsulinUiState {
    val viewModel: PatientMealInsulinViewModel = viewModel(key = "meal_insulin_$patientUid") {
        val c = container()
        PatientMealInsulinViewModel(patientUid, c.icrRepository, c.doseSuggestionRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    return state
}

@Composable
fun IcrSummaryContent(state: PatientMealInsulinUiState, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Insulin-to-carb ratio", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        LinkButton(text = if (state.icrVersions.isEmpty()) "Set" else "Edit", onClick = onOpen)
    }
    val newest = IcrPolicy.newest(state.icrVersions)
    when {
        state.isLoading -> Text("Loading...", style = MaterialTheme.typography.bodySmall)
        state.error != null -> Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        newest == null -> Text(
            "Not set. The patient's meal calculator shows no dose until you confirm a ratio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> IcrVersionLine(newest, prefix = if (newest.isConfirmed) "Confirmed" else "Awaiting your confirmation")
    }
}

/** "Meal insulin suggestions", newest first, with the exact inputs each dose came from. */
@Composable
fun MealInsulinSuggestionsContent(state: PatientMealInsulinUiState) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Text("Meal insulin suggestions", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    when {
        state.isLoading -> Text("Loading...", style = MaterialTheme.typography.bodySmall)
        state.error != null -> Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        state.suggestions.isEmpty() -> Text(
            "No calculator suggestions yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
            val shown = if (showAll) state.suggestions else state.suggestions.take(COLLAPSED_COUNT)
            shown.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SuggestionRow(suggestion)
            }
            if (state.suggestions.size > COLLAPSED_COUNT) {
                LinkButton(
                    text = if (showAll) "Show fewer" else "Show all ${state.suggestions.size}",
                    onClick = { showAll = !showAll },
                )
            }
        }
    }
}

private const val COLLAPSED_COUNT = 5

@Composable
private fun SuggestionRow(s: DoseSuggestion) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.dateTime(s.createdAt), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                outcomeLabel(s),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (s.status) {
                    DoseSuggestionStatus.Taken -> MaterialTheme.colorScheme.primary
                    DoseSuggestionStatus.Skipped -> MaterialTheme.colorScheme.onSurfaceVariant
                    DoseSuggestionStatus.Suggested -> MaterialTheme.colorScheme.tertiary
                },
            )
        }
        Text(s.mealText, style = MaterialTheme.typography.bodyMedium)
        Text(
            "AI carbs ${formatGrams(s.aiCarbs)} g | confirmed ${formatGrams(s.confirmedCarbs)} g",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "ICR ${formatGrams(s.icrValue)} g/unit (v${s.icrVersion}) | suggested ${formatUnits(s.suggestedUnits)} units",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun outcomeLabel(s: DoseSuggestion): String = when (s.status) {
    DoseSuggestionStatus.Taken -> "Took ${formatUnits(s.takenUnits ?: 0.0)} units"
    DoseSuggestionStatus.Skipped -> "Not now"
    DoseSuggestionStatus.Suggested -> "Pending"
}

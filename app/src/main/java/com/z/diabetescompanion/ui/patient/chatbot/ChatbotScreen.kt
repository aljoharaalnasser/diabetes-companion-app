package com.z.diabetescompanion.ui.patient.chatbot

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.ai.AiMessage
import com.z.diabetescompanion.data.ai.AiPrompts
import com.z.diabetescompanion.data.ai.AiResult
import com.z.diabetescompanion.data.ai.CarbEstimateResult
import com.z.diabetescompanion.data.ai.ChatbotService
import android.util.Log
import com.z.diabetescompanion.data.firestore.FirestoreTimeoutException
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.CarbEstimate
import com.z.diabetescompanion.data.model.DoseSuggestion
import com.z.diabetescompanion.data.model.DoseSuggestionStatus
import com.z.diabetescompanion.data.model.IcrEnteredBy
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.IcrStatus
import com.z.diabetescompanion.data.repository.DoseSuggestionRepository
import com.z.diabetescompanion.data.repository.IcrRepository
import com.z.diabetescompanion.domain.IcrPolicy
import com.z.diabetescompanion.domain.MealDoseOutcome
import com.z.diabetescompanion.domain.MealInsulinFlow
import com.z.diabetescompanion.domain.MealInsulinRecords
import com.z.diabetescompanion.domain.MealInsulinValidation
import com.z.diabetescompanion.domain.MedicationValidation
import com.z.diabetescompanion.domain.formatGrams
import com.z.diabetescompanion.domain.formatUnits
import com.z.diabetescompanion.domain.icrLabel
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.BrandMark
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

/**
 * App-drawn cards shown inside the chat. None of their numbers are AI text: the carb card shows
 * the parsed estimate for the patient to confirm or edit, and the dose card shows the result of
 * domain/InsulinCalculator.kt applied to the confirmed carbs.
 */
sealed interface ChatCard {
    data class Carb(
        val mealText: String,
        val estimate: CarbEstimate,
        /** Editable total, pre-filled from the AI estimate. */
        val carbsText: String,
        val carbsError: String? = null,
        val confirmed: Boolean = false,
        val saving: Boolean = false,
        /** Shown after Confirm when no dose could be calculated. */
        val note: String? = null,
    ) : ChatCard

    data class Dose(
        val suggestion: DoseSuggestion,
        val working: String,
        val takenText: String,
        val takenError: String? = null,
        val saving: Boolean = false,
        val error: String? = null,
    ) : ChatCard

    data class IcrPrompt(
        val icrText: String = "",
        val error: String? = null,
        val saving: Boolean = false,
        /** Non-null once saved or skipped; the card then shows this instead of the form. */
        val result: String? = null,
    ) : ChatCard
}

data class ChatTurn(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    /** Marks the reply that redirects a dose question, so the UI can offer the doctor route. */
    val isBoundary: Boolean = false,
    val card: ChatCard? = null,
)

/**
 * The AI asked a question about a vague meal; the patient's next message answers it.
 * [answered] keeps every earlier question and answer so a second question doesn't lose them.
 */
data class PendingClarification(
    val mealText: String,
    val answered: List<Pair<String, String>>,
    val question: String,
)

data class ChatbotUiState(
    val turns: List<ChatTurn> = emptyList(),
    val draft: String = "",
    val isThinking: Boolean = false,
    val error: String? = null,
    val retryable: Boolean = false,
    val isConfigured: Boolean = true,
    /** Next message is a meal description to estimate carbs for. */
    val mealMode: Boolean = false,
    val pendingClarification: PendingClarification? = null,
    val icrVersions: List<IcrSetting> = emptyList(),
    val icrLoaded: Boolean = false,
    /** True when the ICR could not be read; no dose is possible until it loads. */
    val icrLoadFailed: Boolean = false,
) {
    val lastWasBoundary: Boolean get() = turns.lastOrNull()?.isBoundary == true
    val canSend: Boolean get() = draft.isNotBlank() && !isThinking
    val isEmpty: Boolean get() = turns.none { it.fromUser } && !isThinking

    /** Dose cards are only possible with a doctor-confirmed ICR. */
    val usableIcr: IcrSetting? get() = IcrPolicy.usableForDose(icrVersions)

    val composerPlaceholder: String
        get() = when {
            pendingClarification != null -> "Answer the question above"
            mealMode -> "Describe your meal, e.g. 2 chapatis and dal"
            else -> "Ask a question"
        }
}

/**
 * General-guidance chatbot (FR-16, S2-05) with the meal insulin calculator.
 *
 * Chat history lives only for the session. Free chat sends recent text turns; carb requests
 * send only the meal text. The AI estimates carbs only: the dose is calculated by
 * [MealInsulinFlow] from the carbs the patient confirmed and a doctor-confirmed ICR.
 */
class ChatbotViewModel(
    private val patientUid: String,
    private val chatbot: ChatbotService,
    private val icrRepository: IcrRepository,
    private val doseSuggestionRepository: DoseSuggestionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatbotUiState(isConfigured = chatbot.isConfigured))
    val uiState = _uiState.asStateFlow()

    val suggestions = AiPrompts.SUGGESTED_PROMPTS

    private var nextId = 0L
    private var retryAction: (() -> Unit)? = null
    private var icrJob: Job? = null
    private var icrPromptShown = false

    init {
        loadIcr()
    }

    /** Live ICR versions. If they can't be read, no dose card is possible until they do. */
    private fun loadIcr() {
        icrJob?.cancel()
        icrJob = viewModelScope.launch {
            icrRepository.versions(patientUid)
                .catch { _uiState.update { it.copy(icrLoaded = true, icrLoadFailed = true) } }
                .collect { versions ->
                    val firstLoad = !icrPromptShown
                    icrPromptShown = true
                    _uiState.update { it.copy(icrVersions = versions, icrLoaded = true, icrLoadFailed = false) }
                    // First use with no ICR at all: ask for it (optional).
                    if (firstLoad && versions.isEmpty()) addIcrPrompt(FIRST_USE_ICR_TEXT)
                    // A cached empty first snapshot can be followed by the real one: close any
                    // prompt the patient hasn't answered once a ratio turns up.
                    if (versions.isNotEmpty()) closeOpenIcrPrompts(versions)
                }
        }
    }

    // --- Composer --------------------------------------------------------------

    fun onDraftChange(value: String) =
        _uiState.update { it.copy(draft = value.take(500), error = null) }

    fun sendSuggestion(text: String) {
        _uiState.update { it.copy(draft = text, mealMode = false) }
        send()
    }

    /** Toggles "Count carbs for a meal". Starting a new meal drops any unanswered question. */
    fun toggleMealMode() = _uiState.update {
        it.copy(mealMode = !it.mealMode, pendingClarification = null, error = null)
    }

    fun retry() {
        val action = retryAction ?: return
        retryAction = null
        _uiState.update { it.copy(error = null) }
        action()
    }

    fun send() {
        val state = _uiState.value
        val text = state.draft.trim()
        if (text.isEmpty() || state.isThinking) return

        val pending = state.pendingClarification
        val isMeal = state.mealMode
        val history = state.turns
            .filter { it.card == null && it.text.isNotBlank() }
            .map { if (it.fromUser) AiMessage.user(it.text) else AiMessage.assistant(it.text) }

        _uiState.update {
            it.copy(
                turns = it.turns + ChatTurn(id = newId(), fromUser = true, text = text),
                draft = "",
                error = null,
                mealMode = false,
                pendingClarification = null,
            )
        }
        when {
            pending != null -> requestCarbs(pending.mealText, pending.answered + (pending.question to text))
            isMeal -> requestCarbs(text, emptyList())
            else -> requestChat(history, text)
        }
    }

    private fun requestChat(history: List<AiMessage>, text: String) {
        _uiState.update { it.copy(isThinking = true, error = null) }
        viewModelScope.launch {
            when (val result = chatbot.reply(history, text)) {
                is AiResult.Success -> _uiState.update {
                    it.copy(
                        turns = it.turns + ChatTurn(
                            id = newId(),
                            fromUser = false,
                            text = result.text,
                            isBoundary = result.text == AiPrompts.DOSE_BOUNDARY_REPLY,
                        ),
                        isThinking = false,
                    )
                }
                is AiResult.Error -> {
                    retryAction = if (result.retryable) ({ requestChat(history, text) }) else null
                    _uiState.update { it.copy(isThinking = false, error = result.message, retryable = result.retryable) }
                }
            }
        }
    }

    /** Only the meal text (and the answers to our own questions) goes to the AI. */
    private fun requestCarbs(mealText: String, answered: List<Pair<String, String>>) {
        _uiState.update { it.copy(isThinking = true, error = null) }
        viewModelScope.launch {
            when (val result = chatbot.estimateCarbs(mealText, answered)) {
                is CarbEstimateResult.Estimate -> _uiState.update {
                    it.copy(
                        isThinking = false,
                        turns = it.turns + ChatTurn(
                            id = newId(),
                            fromUser = false,
                            text = CARB_CARD_INTRO,
                            card = ChatCard.Carb(
                                mealText = combinedMealText(mealText, answered),
                                estimate = result.estimate,
                                carbsText = formatGrams(result.estimate.totalCarbsGrams),
                            ),
                        ),
                    )
                }
                // Ask before showing any carb card, but not forever.
                is CarbEstimateResult.Clarify -> _uiState.update {
                    if (answered.size >= MAX_CLARIFICATIONS) {
                        retryAction = null
                        it.copy(isThinking = false, error = TOO_VAGUE_MESSAGE, retryable = false)
                    } else {
                        it.copy(
                            isThinking = false,
                            pendingClarification = PendingClarification(mealText, answered, result.question),
                            turns = it.turns + ChatTurn(id = newId(), fromUser = false, text = result.question),
                        )
                    }
                }
                is CarbEstimateResult.Failed -> {
                    retryAction = if (result.retryable) ({ requestCarbs(mealText, answered) }) else null
                    _uiState.update { it.copy(isThinking = false, error = result.message, retryable = result.retryable) }
                }
            }
        }
    }

    // --- Carb card -------------------------------------------------------------

    fun onCarbsChange(turnId: Long, value: String) = updateCard<ChatCard.Carb>(turnId) {
        it.copy(carbsText = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6), carbsError = null)
    }

    /** Uses the confirmed (possibly edited) grams, never the AI estimate. */
    fun confirmCarbs(turnId: Long) {
        val card = cardOf<ChatCard.Carb>(turnId) ?: return
        if (card.confirmed || card.saving) return
        // "Not loaded" must never be treated as "no ICR".
        val state = _uiState.value
        if (!state.icrLoaded || state.icrLoadFailed) {
            updateCard<ChatCard.Carb>(turnId) { it.copy(carbsError = MealInsulinFlow.ICR_NOT_LOADED_MESSAGE) }
            if (state.icrLoadFailed) loadIcr()
            return
        }

        when (val outcome = MealInsulinFlow.evaluate(card.carbsText, state.icrVersions)) {
            is MealDoseOutcome.InvalidCarbs -> updateCard<ChatCard.Carb>(turnId) { it.copy(carbsError = outcome.message) }
            is MealDoseOutcome.NeedsDoctorConfirmation -> updateCard<ChatCard.Carb>(turnId) {
                it.copy(confirmed = true, note = MealInsulinFlow.NEEDS_CONFIRMATION_MESSAGE)
            }
            MealDoseOutcome.NoIcr -> {
                updateCard<ChatCard.Carb>(turnId) { it.copy(confirmed = true, note = MealInsulinFlow.NEEDS_CONFIRMATION_MESSAGE) }
                val promptOpen = _uiState.value.turns.any { val c = it.card; c is ChatCard.IcrPrompt && c.result == null }
                if (!promptOpen) {
                    addIcrPrompt(NO_ICR_TEXT)
                }
            }
            is MealDoseOutcome.BelowMinimumDose -> updateCard<ChatCard.Carb>(turnId) {
                it.copy(confirmed = true, note = MealInsulinFlow.belowMinimumMessage(outcome.calculation))
            }
            is MealDoseOutcome.DoseReady -> saveSuggestion(turnId, card, outcome)
        }
    }

    private fun saveSuggestion(turnId: Long, card: ChatCard.Carb, ready: MealDoseOutcome.DoseReady) {
        updateCard<ChatCard.Carb>(turnId) { it.copy(saving = true, carbsError = null) }
        val suggestion = MealInsulinRecords.newSuggestion(
            suggestionId = UUID.randomUUID().toString(),
            patientId = patientUid,
            mealText = card.mealText,
            estimate = card.estimate,
            ready = ready,
        )
        viewModelScope.launch {
            try {
                // A timeout means the write is queued offline and will sync; don't create a duplicate.
                runCatchingTimeout { doseSuggestionRepository.create(suggestion) }
                updateCard<ChatCard.Carb>(turnId) { it.copy(saving = false, confirmed = true) }
                _uiState.update {
                    it.copy(
                        turns = it.turns + ChatTurn(
                            id = newId(),
                            fromUser = false,
                            text = "",
                            card = ChatCard.Dose(
                                suggestion = suggestion,
                                working = ready.calculation.working,
                                takenText = formatUnits(suggestion.suggestedUnits),
                            ),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateCard<ChatCard.Carb>(turnId) { it.copy(saving = false, carbsError = e.toFirestoreMessage()) }
            }
        }
    }

    // --- Dose card -------------------------------------------------------------

    fun onTakenChange(turnId: Long, value: String) = updateCard<ChatCard.Dose>(turnId) {
        it.copy(takenText = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5), takenError = null, error = null)
    }

    /** Saves an insulin entry to the medication log and marks the suggestion TAKEN. */
    fun tookDose(turnId: Long) {
        val card = cardOf<ChatCard.Dose>(turnId) ?: return
        if (card.saving || card.suggestion.status != DoseSuggestionStatus.Suggested) return
        MedicationValidation.doseError(card.takenText)?.let { err ->
            updateCard<ChatCard.Dose>(turnId) { it.copy(takenError = err) }
            return
        }
        val units = MealInsulinValidation.parse(card.takenText) ?: return
        val log = MealInsulinRecords.takenLog(card.suggestion, units, takenAt = Date())

        updateCard<ChatCard.Dose>(turnId) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                runCatchingTimeout { doseSuggestionRepository.recordTaken(patientUid, card.suggestion.suggestionId, units, log) }
                updateCard<ChatCard.Dose>(turnId) {
                    it.copy(saving = false, suggestion = MealInsulinRecords.markTaken(it.suggestion, units, log.id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateCard<ChatCard.Dose>(turnId) { it.copy(saving = false, error = e.toFirestoreMessage()) }
            }
        }
    }

    fun notNow(turnId: Long) {
        val card = cardOf<ChatCard.Dose>(turnId) ?: return
        if (card.saving || card.suggestion.status != DoseSuggestionStatus.Suggested) return
        updateCard<ChatCard.Dose>(turnId) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                runCatchingTimeout { doseSuggestionRepository.markSkipped(patientUid, card.suggestion.suggestionId) }
                updateCard<ChatCard.Dose>(turnId) {
                    it.copy(saving = false, suggestion = MealInsulinRecords.markSkipped(it.suggestion))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateCard<ChatCard.Dose>(turnId) { it.copy(saving = false, error = e.toFirestoreMessage()) }
            }
        }
    }

    // --- ICR prompt ------------------------------------------------------------

    fun onIcrChange(turnId: Long, value: String) = updateCard<ChatCard.IcrPrompt>(turnId) {
        it.copy(icrText = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5), error = null)
    }

    /** Patient-entered values are always UNCONFIRMED; only the doctor can confirm them. */
    fun saveIcr(turnId: Long) {
        val card = cardOf<ChatCard.IcrPrompt>(turnId) ?: return
        if (card.saving || card.result != null) return
        // The version number comes from the loaded list, so it must really be loaded.
        if (!_uiState.value.icrLoaded || _uiState.value.icrLoadFailed) {
            updateCard<ChatCard.IcrPrompt>(turnId) { it.copy(error = MealInsulinFlow.ICR_NOT_LOADED_MESSAGE) }
            if (_uiState.value.icrLoadFailed) loadIcr()
            return
        }
        MealInsulinValidation.icrError(card.icrText)?.let { err ->
            updateCard<ChatCard.IcrPrompt>(turnId) { it.copy(error = err) }
            return
        }
        val grams = MealInsulinValidation.parse(card.icrText) ?: return
        val setting = IcrSetting(
            icrId = UUID.randomUUID().toString(),
            patientId = patientUid,
            gramsPerUnit = grams,
            status = IcrStatus.Unconfirmed,
            enteredBy = IcrEnteredBy.Patient,
            version = IcrPolicy.nextVersion(_uiState.value.icrVersions),
            effectiveFrom = Date(),
        )
        updateCard<ChatCard.IcrPrompt>(turnId) { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                runCatchingTimeout { icrRepository.create(setting) }
                updateCard<ChatCard.IcrPrompt>(turnId) {
                    it.copy(
                        saving = false,
                        result = "Saved: ${icrLabel(grams)}. Your doctor needs to confirm it before the app can calculate a dose.",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateCard<ChatCard.IcrPrompt>(turnId) { it.copy(saving = false, error = e.toFirestoreMessage()) }
            }
        }
    }

    fun skipIcr(turnId: Long) = updateCard<ChatCard.IcrPrompt>(turnId) {
        if (it.saving) it else it.copy(result = "Skipped. I'll ask again when you count carbs for a meal.")
    }

    // --- Helpers ---------------------------------------------------------------

    private fun addIcrPrompt(text: String) = _uiState.update {
        it.copy(turns = it.turns + ChatTurn(id = newId(), fromUser = false, text = text, card = ChatCard.IcrPrompt()))
    }

    private fun closeOpenIcrPrompts(versions: List<IcrSetting>) {
        val newest = IcrPolicy.newest(versions) ?: return
        _uiState.update { state ->
            state.copy(turns = state.turns.map { turn ->
                val card = turn.card
                if (card is ChatCard.IcrPrompt && card.result == null && !card.saving) {
                    turn.copy(card = card.copy(result = "Your ratio is on file: ${icrLabel(newest.gramsPerUnit)} (${newest.status.label.lowercase()})."))
                } else {
                    turn
                }
            })
        }
    }

    private fun newId(): Long = nextId++

    private inline fun <reified T : ChatCard> cardOf(turnId: Long): T? =
        _uiState.value.turns.firstOrNull { it.id == turnId }?.card as? T

    private inline fun <reified T : ChatCard> updateCard(turnId: Long, crossinline transform: (T) -> T) =
        _uiState.update { state ->
            state.copy(turns = state.turns.map { turn ->
                val card = turn.card
                if (turn.id == turnId && card is T) turn.copy(card = transform(card)) else turn
            })
        }

    private fun combinedMealText(mealText: String, answered: List<Pair<String, String>>): String {
        val answers = answered.map { it.second.trim() }.filter { it.isNotEmpty() }
        return if (answers.isEmpty()) mealText else "$mealText (${answers.joinToString("; ")})"
    }

    /**
     * Firestore keeps a timed-out write queued and syncs it later, so for these one-shot records
     * a timeout counts as saved. Retrying would create a duplicate or a conflicting update.
     */
    private suspend inline fun runCatchingTimeout(block: () -> Unit) {
        try {
            block()
        } catch (e: FirestoreTimeoutException) {
            Log.w(TAG, "Write queued offline; it will sync when the connection returns", e)
        }
    }

    companion object {
        private const val TAG = "ChatbotViewModel"
        private const val MAX_CLARIFICATIONS = 3
        const val TOO_VAGUE_MESSAGE =
            "I still can't estimate that meal. Tap \"Count carbs for a meal\" and describe it again with the portion sizes."
        const val CARB_CARD_INTRO =
            "Here is my carb estimate. Check the total, edit it if it looks wrong, then confirm."
        const val FIRST_USE_ICR_TEXT =
            "To calculate meal doses, the app needs your insulin-to-carb ratio (ICR): how many grams " +
                "of carbs 1 unit of insulin covers. You can skip this for now."
        const val NO_ICR_TEXT =
            "No insulin-to-carb ratio is saved yet. Add it below. Your doctor must confirm it before " +
                "the app can calculate a dose."
    }
}

@Composable
fun ChatbotScreen(onBack: () -> Unit, onAskDoctor: () -> Unit) {
    val session = currentSession()
    val viewModel: ChatbotViewModel = viewModel {
        val c = container()
        ChatbotViewModel(session.uid, c.chatbotService, c.icrRepository, c.doseSuggestionRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.turns.size, state.isThinking) {
        val header = if (state.isEmpty) 1 + viewModel.suggestions.size else 0
        val count = header + state.turns.size + if (state.isThinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    ScreenScaffold(title = "Diabetes Companion", onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            GuidanceBanner()

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isEmpty) {
                    item(key = "welcome") { WelcomeBlock() }
                    items(viewModel.suggestions, key = { "suggestion_$it" }) { suggestion ->
                        SuggestionCard(suggestion) { viewModel.sendSuggestion(suggestion) }
                    }
                }
                items(state.turns, key = { it.id }) { turn ->
                    when (val card = turn.card) {
                        null -> ChatBubble(turn)
                        is ChatCard.Carb -> CardTurn(turn.text) {
                            CarbCard(
                                card = card,
                                // While the ICR is still loading, don't claim it needs confirmation.
                                hasUsableIcr = !state.icrLoaded || state.usableIcr != null,
                                onCarbsChange = { viewModel.onCarbsChange(turn.id, it) },
                                onConfirm = {
                                    focusManager.clearFocus()
                                    viewModel.confirmCarbs(turn.id)
                                },
                            )
                        }
                        is ChatCard.Dose -> CardTurn(turn.text) {
                            DoseCard(
                                card = card,
                                onTakenChange = { viewModel.onTakenChange(turn.id, it) },
                                onTook = {
                                    focusManager.clearFocus()
                                    viewModel.tookDose(turn.id)
                                },
                                onNotNow = { viewModel.notNow(turn.id) },
                            )
                        }
                        is ChatCard.IcrPrompt -> CardTurn(turn.text) {
                            IcrPromptCard(
                                card = card,
                                onChange = { viewModel.onIcrChange(turn.id, it) },
                                onSave = {
                                    focusManager.clearFocus()
                                    viewModel.saveIcr(turn.id)
                                },
                                onSkip = { viewModel.skipIcr(turn.id) },
                            )
                        }
                    }
                }
                if (state.isThinking) item(key = "typing") { TypingBubble() }
                if (state.lastWasBoundary) {
                    item(key = "ask_doctor_after_boundary") {
                        SecondaryButton(text = "Ask my doctor", onClick = onAskDoctor)
                    }
                }
            }

            if (!state.isConfigured || state.error != null) {
                InlineMessage(
                    message = state.error ?: "AI is not configured yet, so answers are unavailable.",
                    tone = MessageTone.Warning,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (state.error != null && state.retryable) {
                    LinkButton(
                        text = "Try again",
                        onClick = viewModel::retry,
                        enabled = !state.isThinking,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            ActionChips(
                mealMode = state.mealMode,
                enabled = !state.isThinking,
                onToggleMeal = viewModel::toggleMealMode,
                onAskDoctor = onAskDoctor,
            )

            Composer(
                draft = state.draft,
                placeholder = state.composerPlaceholder,
                canSend = state.canSend,
                enabled = !state.isThinking,
                onDraftChange = viewModel::onDraftChange,
                onSend = {
                    focusManager.clearFocus()
                    viewModel.send()
                },
            )
        }
    }
}

/** The charter requires this to be visible at all times, not buried in a menu (FR-16). */
@Composable
private fun GuidanceBanner() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            Icons.Rounded.MedicalServices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "General guidance only. Meal doses are calculated by the app from your doctor-confirmed ICR, never by AI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun WelcomeBlock() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    ) {
        BrandMark(size = 72.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            "How can I help?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask about readings, meals or daily habits, or tap \"Count carbs for a meal\" below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SuggestionCard(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** "Count carbs for a meal" (primary path) and "Ask my doctor" (secondary, always visible). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionChips(
    mealMode: Boolean,
    enabled: Boolean,
    onToggleMeal: () -> Unit,
    onAskDoctor: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        FilterChip(
            selected = mealMode,
            onClick = onToggleMeal,
            enabled = enabled,
            label = { Text("Count carbs for a meal") },
            leadingIcon = { Icon(Icons.Rounded.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = onAskDoctor,
            label = { Text("Ask my doctor") },
            leadingIcon = { Icon(Icons.Rounded.MedicalServices, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

/** App message (optional) followed by an app-drawn card, aligned with assistant replies. */
@Composable
private fun CardTurn(text: String, card: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (text.isNotBlank()) ChatBubble(ChatTurn(id = -1, fromUser = false, text = text))
        Box(modifier = Modifier.padding(start = 40.dp)) { card() }
    }
}

@Composable
private fun AppCard(content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CarbCard(
    card: ChatCard.Carb,
    hasUsableIcr: Boolean,
    onCarbsChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AppCard {
        Text("Carb estimate", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        card.estimate.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                    if (item.assumedPortion.isNotBlank()) {
                        Text(
                            item.assumedPortion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("${formatGrams(item.carbsGrams)} g", style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Estimated total: ${formatGrams(card.estimate.totalCarbsGrams)} g",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        AppTextField(
            value = card.carbsText,
            onValueChange = onCarbsChange,
            label = "Total carbs (g)",
            errorText = card.carbsError,
            enabled = !card.confirmed && !card.saving,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
        )
        if (!hasUsableIcr || card.note != null) {
            Spacer(Modifier.height(8.dp))
            InlineMessage(
                message = card.note ?: MealInsulinFlow.NEEDS_CONFIRMATION_MESSAGE,
                tone = MessageTone.Info,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (card.confirmed) {
            Text(
                "Confirmed: ${formatGrams(MealInsulinValidation.parse(card.carbsText) ?: 0.0)} g of carbs",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            PrimaryButton(text = "Confirm", onClick = onConfirm, loading = card.saving)
        }
    }
}

@Composable
private fun DoseCard(
    card: ChatCard.Dose,
    onTakenChange: (String) -> Unit,
    onTook: () -> Unit,
    onNotNow: () -> Unit,
) {
    val suggestion = card.suggestion
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Calculated meal dose", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${formatUnits(suggestion.suggestedUnits)} units",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(card.working, style = MaterialTheme.typography.bodyMedium)
        Text(
            "ICR: ${icrLabel(suggestion.icrValue)} (version ${suggestion.icrVersion}, confirmed by your doctor). " +
                "Rounded down to ${formatUnits(suggestion.roundingStep)} unit steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            MealInsulinFlow.DOSE_DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        when (suggestion.status) {
            DoseSuggestionStatus.Taken -> InlineMessage(
                message = "Logged ${formatUnits(suggestion.takenUnits ?: 0.0)} units in your medication log.",
                tone = MessageTone.Success,
            )
            DoseSuggestionStatus.Skipped -> InlineMessage(
                message = "Not taken. Saved as \"Not now\".",
                tone = MessageTone.Info,
            )
            DoseSuggestionStatus.Suggested -> {
                AppTextField(
                    value = card.takenText,
                    onValueChange = onTakenChange,
                    label = "Units you took",
                    errorText = card.takenError,
                    enabled = !card.saving,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(10.dp))
                InlineMessage(message = card.error, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 8.dp))
                PrimaryButton(
                    text = "I took ${card.takenText.ifBlank { "__" }} units",
                    onClick = onTook,
                    loading = card.saving,
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = "Not now", onClick = onNotNow, enabled = !card.saving)
            }
        }
    }
}

@Composable
private fun IcrPromptCard(
    card: ChatCard.IcrPrompt,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
) {
    AppCard {
        Text("Insulin-to-carb ratio", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (card.result != null) {
            Text(card.result, style = MaterialTheme.typography.bodyMedium)
            return@AppCard
        }
        AppTextField(
            value = card.icrText,
            onValueChange = onChange,
            label = "Grams of carbs per 1 unit",
            placeholder = "e.g. 10",
            errorText = card.error,
            enabled = !card.saving,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        Text(
            MealInsulinValidation.parse(card.icrText)?.takeIf { it > 0 }?.let { icrLabel(it) }
                ?: "Shown as \"1 unit for every X g of carbs\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(text = "Save ratio", onClick = onSave, loading = card.saving)
        Spacer(Modifier.height(4.dp))
        LinkButton(text = "Skip for now", onClick = onSkip, enabled = !card.saving)
    }
}

/** Small brand avatar so assistant replies are visually attributed. */
@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ChatBubble(turn: ChatTurn) {
    if (turn.fromUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    turn.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AssistantAvatar()
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(
                    if (turn.isBoundary) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (turn.isBoundary) Color.Transparent
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (turn.isBoundary) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Three dots that fade in sequence while the model is working (NFR-03). */
@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AssistantAvatar()
        Spacer(Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 180),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$index",
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            CircleShape,
                        )
                )
                if (index < 2) Spacer(Modifier.width(6.dp))
            }
        }
    }
}

/** Pill input with a circular send button, so the composer reads as one control. */
@Composable
private fun Composer(
    draft: String,
    placeholder: String,
    canSend: Boolean,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                placeholder = { Text(placeholder) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

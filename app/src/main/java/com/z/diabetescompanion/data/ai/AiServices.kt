package com.z.diabetescompanion.data.ai

import com.z.diabetescompanion.data.model.CarbEstimate
import com.z.diabetescompanion.data.model.RequestContext
import com.z.diabetescompanion.domain.DoseGuard

/** Outcome of a carb request, already parsed and safety-checked. */
sealed interface CarbEstimateResult {
    data class Estimate(val estimate: CarbEstimate) : CarbEstimateResult
    data class Clarify(val question: String) : CarbEstimateResult
    data class Failed(val message: String, val retryable: Boolean = true) : CarbEstimateResult
}

/**
 * Patient-facing general-guidance chatbot (FR-16, S2-05) and meal carb estimates.
 *
 * Dose questions are answered on-device before any network call. Every AI reply also passes
 * through [DoseGuard], so text stating an insulin amount is never shown. The AI only estimates
 * carbs; the dose itself is calculated by domain/InsulinCalculator.kt.
 */
class ChatbotService(private val aiClient: AiClient) {

    val isConfigured: Boolean get() = aiClient.isConfigured

    /** Non-null when the message was handled locally and no AI call is needed. */
    fun localBoundaryReply(message: String): String? =
        if (AiPrompts.looksLikeDoseQuestion(message)) AiPrompts.DOSE_BOUNDARY_REPLY else null

    suspend fun reply(history: List<AiMessage>, message: String): AiResult {
        localBoundaryReply(message)?.let {
            return AiResult.Success(it, ON_DEVICE)
        }
        val messages = buildList {
            add(AiMessage.system(AiPrompts.CHATBOT_SYSTEM))
            // Only the recent turns are sent, which keeps the prompt small and bounded.
            addAll(history.takeLast(MAX_HISTORY))
            add(AiMessage.user(message))
        }
        return guard(aiClient.complete(messages, maxTokens = 400, reasoningEffort = "low"))
    }

    /**
     * Sends ONLY the meal text (plus our clarifying questions and the answers). No chat
     * history, name, user id, email or health record goes to the provider (NFR-09).
     */
    suspend fun estimateCarbs(
        mealText: String,
        clarifications: List<Pair<String, String>> = emptyList(),
    ): CarbEstimateResult {
        val result = aiClient.complete(
            messages = listOf(
                AiMessage.system(AiPrompts.CARB_ESTIMATE_SYSTEM),
                AiMessage.user(AiPrompts.carbEstimateRequest(mealText, clarifications)),
            ),
            maxTokens = 600,
            reasoningEffort = "low",
            jsonOutput = true,
        )
        return when (result) {
            is AiResult.Error -> CarbEstimateResult.Failed(result.message, result.retryable)
            is AiResult.Success -> when (val parsed = CarbEstimateParser.parse(result.text)) {
                is CarbParseResult.Estimate -> CarbEstimateResult.Estimate(parsed.estimate)
                is CarbParseResult.Clarify -> CarbEstimateResult.Clarify(parsed.question)
                is CarbParseResult.Invalid -> CarbEstimateResult.Failed(CARB_RETRY_MESSAGE, retryable = true)
            }
        }
    }

    /** Replaces any AI text that states an insulin amount with the fixed boundary reply. */
    private fun guard(result: AiResult): AiResult =
        if (result is AiResult.Success && DoseGuard.containsInsulinAmount(result.text)) {
            AiResult.Success(AiPrompts.DOSE_BOUNDARY_REPLY, ON_DEVICE)
        } else {
            result
        }

    companion object {
        private const val MAX_HISTORY = 8
        const val ON_DEVICE = "on-device guard"
        const val CARB_RETRY_MESSAGE =
            "I couldn't read a carb estimate for that meal. Please try again, or describe the meal a little differently."
    }
}

/**
 * Doctor-only draft for an insulin-advice request (FR-18, S2-07).
 * The result is written to a subdocument that the rules hide from the patient.
 */
class InsulinDraftService(private val aiClient: AiClient) {

    val isConfigured: Boolean get() = aiClient.isConfigured
    val modelName: String get() = aiClient.modelName

    suspend fun draft(question: String, context: RequestContext): AiResult = aiClient.complete(
        messages = listOf(
            AiMessage.system(AiPrompts.DRAFT_SYSTEM),
            AiMessage.user(AiPrompts.draftContext(question, context)),
        ),
        maxTokens = 700,
        reasoningEffort = "medium",
    )
}

/** Plain-language overview attached to a weekly summary (FR-20, S2-09). */
class SummaryNarrativeService(private val aiClient: AiClient) {

    val isConfigured: Boolean get() = aiClient.isConfigured

    suspend fun narrate(statsBlock: String): AiResult = aiClient.complete(
        messages = listOf(
            AiMessage.system(AiPrompts.SUMMARY_SYSTEM),
            AiMessage.user(statsBlock),
        ),
        maxTokens = 300,
        reasoningEffort = "low",
    )
}

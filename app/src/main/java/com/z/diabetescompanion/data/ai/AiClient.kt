package com.z.diabetescompanion.data.ai

/** One turn in a conversation sent to the model. */
data class AiMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String) = AiMessage("system", content)
        fun user(content: String) = AiMessage("user", content)
        fun assistant(content: String) = AiMessage("assistant", content)
    }
}

sealed interface AiResult {
    data class Success(val text: String, val model: String) : AiResult
    /** [retryable] separates a transient network blip from a misconfiguration. */
    data class Error(val message: String, val retryable: Boolean = true) : AiResult
}

/**
 * Text generation behind an interface so the UI never touches a vendor SDK and the
 * provider can be swapped without changing a single screen (NFR-12).
 */
interface AiClient {
    /** False when no API key was supplied; screens then offer the manual path instead. */
    val isConfigured: Boolean
    val modelName: String

    /** [jsonOutput] asks the provider for a single JSON object (used for carb estimates). */
    suspend fun complete(
        messages: List<AiMessage>,
        maxTokens: Int = 600,
        reasoningEffort: String = "low",
        jsonOutput: Boolean = false,
    ): AiResult
}

/** Stand-in used when no key is present, so the app still runs and explains itself. */
class UnconfiguredAiClient(override val modelName: String) : AiClient {
    override val isConfigured = false

    override suspend fun complete(
        messages: List<AiMessage>,
        maxTokens: Int,
        reasoningEffort: String,
        jsonOutput: Boolean,
    ): AiResult = AiResult.Error(NOT_CONFIGURED, retryable = false)

    companion object {
        const val NOT_CONFIGURED =
            "AI is not configured. Add OPENAI_API_KEY to the .env file in the project root and rebuild."
    }
}

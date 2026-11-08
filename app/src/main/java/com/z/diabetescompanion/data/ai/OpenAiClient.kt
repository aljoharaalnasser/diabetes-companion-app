package com.z.diabetescompanion.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI Chat Completions client for gpt-6-luna.
 *
 * The key is read from BuildConfig, which is populated from the git-ignored .env file.
 * Calling OpenAI straight from the app is acceptable for an academic prototype but means
 * the key ships inside the APK; a real deployment would proxy these calls through a server.
 */
class OpenAiClient(
    private val apiKey: String,
    override val modelName: String,
) : AiClient {

    override val isConfigured: Boolean = apiKey.isNotBlank()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(
        messages: List<AiMessage>,
        maxTokens: Int,
        reasoningEffort: String,
        jsonOutput: Boolean,
    ): AiResult {
        if (!isConfigured) return AiResult.Error(UnconfiguredAiClient.NOT_CONFIGURED, retryable = false)

        val body = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
            })
            // gpt-6 reasoning models reject the older max_tokens in favour of this.
            put("max_completion_tokens", maxTokens)
            put("reasoning_effort", reasoningEffort)
            // JSON mode: the model must return one JSON object. The prompt also says "JSON",
            // which this mode requires.
            if (jsonOutput) put("response_format", JSONObject().put("type", "json_object"))
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "OpenAI ${response.code}: ${payload.take(400)}")
                        return@use AiResult.Error(
                            message = errorMessage(response.code, payload),
                            retryable = response.code == 429 || response.code >= 500,
                        )
                    }
                    parse(payload)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AiResult.Error("Couldn't reach the AI service. Check your connection and try again.")
            } catch (e: Exception) {
                Log.w(TAG, "OpenAI call failed", e)
                AiResult.Error("The AI service returned something unexpected. Please try again.")
            }
        }
    }

    private fun parse(payload: String): AiResult {
        val choices = JSONObject(payload).optJSONArray("choices")
        val text = choices?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            .orEmpty()
        return if (text.isEmpty()) {
            AiResult.Error("The AI service returned an empty response. Please try again.")
        } else {
            AiResult.Success(text, modelName)
        }
    }

    private fun errorMessage(code: Int, payload: String): String {
        val apiMessage = runCatching {
            JSONObject(payload).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            401, 403 -> "The AI key was rejected. Check OPENAI_API_KEY in the .env file."
            404 -> "Model \"$modelName\" is not available to this key."
            429 -> "The AI service is rate limited or out of quota. Try again shortly."
            in 500..599 -> "The AI service is temporarily unavailable. Try again shortly."
            else -> apiMessage.ifBlank { "The AI service refused the request (HTTP $code)." }
        }
    }

    private companion object {
        const val TAG = "OpenAiClient"
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

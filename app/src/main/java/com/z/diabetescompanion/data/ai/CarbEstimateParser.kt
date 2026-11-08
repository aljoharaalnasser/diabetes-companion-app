package com.z.diabetescompanion.data.ai

import com.z.diabetescompanion.data.model.CarbEstimate
import com.z.diabetescompanion.data.model.CarbItem
import com.z.diabetescompanion.domain.DoseGuard
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Outcome of parsing the AI's carb JSON. */
sealed interface CarbParseResult {
    data class Estimate(val estimate: CarbEstimate) : CarbParseResult
    data class Clarify(val question: String) : CarbParseResult
    data class Invalid(val reason: String) : CarbParseResult
}

/**
 * Parses the structured carb estimate:
 * { "items": [ { "name", "assumedPortion", "carbsGrams" } ], "totalCarbsGrams", "clarifyingQuestion" }
 *
 * Anything malformed is rejected rather than guessed at, so the chat can offer a retry.
 * Every string that will be drawn on screen is also passed through [DoseGuard].
 */
object CarbEstimateParser {

    private const val MAX_ITEMS = 30
    private const val MAX_ITEM_GRAMS = 500.0

    fun parse(raw: String?): CarbParseResult {
        val json = extractObject(raw) ?: return CarbParseResult.Invalid("No JSON object in the response.")
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return CarbParseResult.Invalid("The response was not valid JSON.")
        }

        val question = root.nullableString("clarifyingQuestion")
        if (!question.isNullOrBlank()) {
            if (DoseGuard.containsInsulinAmount(question)) {
                return CarbParseResult.Invalid("The clarifying question mentioned an insulin amount.")
            }
            return CarbParseResult.Clarify(question.trim().take(300))
        }

        val itemsJson = root.opt("items") as? JSONArray
            ?: return CarbParseResult.Invalid("The response had no items list.")
        if (itemsJson.length() == 0) return CarbParseResult.Invalid("The response listed no foods.")
        if (itemsJson.length() > MAX_ITEMS) return CarbParseResult.Invalid("The response listed too many foods.")

        val items = mutableListOf<CarbItem>()
        for (i in 0 until itemsJson.length()) {
            val obj = itemsJson.opt(i) as? JSONObject ?: return CarbParseResult.Invalid("Item ${i + 1} was not an object.")
            val name = obj.nullableString("name")?.trim()
            if (name.isNullOrEmpty()) return CarbParseResult.Invalid("Item ${i + 1} had no name.")
            val portion = obj.nullableString("assumedPortion")?.trim().orEmpty()
            val grams = obj.number("carbsGrams") ?: return CarbParseResult.Invalid("Item ${i + 1} had no carb amount.")
            if (grams < 0 || grams > MAX_ITEM_GRAMS) return CarbParseResult.Invalid("Item ${i + 1} had an impossible carb amount.")
            if (DoseGuard.containsInsulinAmount(name) || DoseGuard.containsInsulinAmount(portion)) {
                return CarbParseResult.Invalid("An item mentioned an insulin amount.")
            }
            items += CarbItem(name = name.take(80), assumedPortion = portion.take(80), carbsGrams = grams)
        }

        val total = root.number("totalCarbsGrams") ?: return CarbParseResult.Invalid("The response had no total.")
        if (total <= 0) return CarbParseResult.Invalid("The total carbs must be greater than zero.")

        return CarbParseResult.Estimate(CarbEstimate(items = items, totalCarbsGrams = total, clarifyingQuestion = null))
    }

    /** Tolerates ```json fences or a sentence around the object; takes the outermost braces. */
    private fun extractObject(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
    }

    /** optString() turns JSON null into the text "null", so read the raw value instead. */
    private fun JSONObject.nullableString(key: String): String? = when (val value = opt(key)) {
        null, JSONObject.NULL -> null
        is String -> value
        else -> null
    }

    private fun JSONObject.number(key: String): Double? = when (val value = opt(key)) {
        is Number -> value.toDouble().takeIf { !it.isNaN() && !it.isInfinite() }
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }
}

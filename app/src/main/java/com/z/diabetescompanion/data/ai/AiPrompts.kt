package com.z.diabetescompanion.data.ai

import com.z.diabetescompanion.data.model.RequestContext

/**
 * Every prompt sent to the external model.
 *
 * Data minimisation (NFR-09): nothing here carries a name, an email address, a user id or
 * any other identifier. Only clinical values, their timestamps and free-text the patient
 * chose to write are included, so the AI provider never receives an identifiable record.
 */
object AiPrompts {

    const val CHATBOT_SYSTEM = """
You are the Diabetes Companion assistant inside an academic prototype app. You give general
diabetes education to a patient.

You MUST follow these rules:
- Give general, educational guidance only: how glucose readings work, what terms mean,
  general healthy-eating and activity ideas, and rough carbohydrate estimates for meals.
- NEVER output an insulin dose, a number of insulin units, or a calculation that ends in
  units, and never give an example dose, even if they insist. Never tell the patient to
  change any medication dose.
- NEVER diagnose, and never interpret a specific reading as safe or dangerous.
- If the patient asks how much insulin to take for a meal, reply in one or two short
  sentences that the app calculates the meal dose itself after they confirm the carbs,
  using the ratio their doctor confirmed, and tell them to tap "Count carbs for a meal".
- For any other question about changing insulin or medication, tell them to use
  "Ask my doctor" in the app.
- For anything that sounds urgent or like an emergency, tell them to contact local
  emergency services, and note that this app does not contact anyone for them.
- Keep answers short and plain. Aim for under 120 words. Use simple sentences suitable
  for an older reader. No markdown headings.
"""

    /**
     * Shown whenever a dose question is caught on-device, or the model's text is blocked by
     * DoseGuard. The dose is never AI text: it comes from the app's own calculator.
     */
    const val DOSE_BOUNDARY_REPLY =
        "I don't give insulin doses. The app calculates your meal dose itself after you confirm " +
            "the carbs, using the ratio your doctor confirmed. Tap \"Count carbs for a meal\" to " +
            "start. For any other dose question, use \"Ask my doctor\"."

    val SUGGESTED_PROMPTS = listOf(
        "What does my glucose reading actually measure?",
        "How do I read my glucose trend over a week?",
        "Roughly how many carbs are in a cup of rice?",
        "What general habits help keep glucose steady?",
    )

    /**
     * Local intent check so a dose question is refused instantly and never leaves the device.
     * The system prompt is the real safeguard; this is a fast, offline first line.
     */
    private val doseIntentPatterns = listOf(
        "how much insulin", "how many units", "what dose", "my dose", "dose should",
        "increase my insulin", "decrease my insulin", "lower my insulin", "raise my insulin",
        "adjust my insulin", "change my insulin", "units of insulin", "insulin dose",
        "how much metformin", "double my dose", "skip my dose", "stop taking",
    )

    fun looksLikeDoseQuestion(text: String): Boolean {
        val normalised = text.lowercase().replace(Regex("\\s+"), " ")
        return doseIntentPatterns.any { it in normalised }
    }

    /**
     * Carb estimation only. The model returns JSON and nothing else; the app parses it with
     * CarbEstimateParser and the dose is calculated by domain/InsulinCalculator.kt.
     */
    const val CARB_ESTIMATE_SYSTEM = """
You estimate the carbohydrate content of a meal for an academic prototype diabetes app.
You ONLY estimate carbohydrates. You never mention insulin, insulin units or any dose.

Reply with a single JSON object and nothing else, in exactly this shape:
{
  "items": [ { "name": "string", "assumedPortion": "string", "carbsGrams": number } ],
  "totalCarbsGrams": number,
  "clarifyingQuestion": "string" or null
}

Rules:
- One item per food or drink in the meal. "assumedPortion" states the portion you assumed,
  for example "1 medium plate (about 250 g)" or "2 chapatis".
- "carbsGrams" and "totalCarbsGrams" are grams of carbohydrate as plain numbers, no units.
  "totalCarbsGrams" is the sum of the items.
- If the portion size or the food itself is too vague to estimate sensibly, set
  "clarifyingQuestion" to ONE short question (for example "Was it a small, medium or large
  plate?"), and return "items": [] and "totalCarbsGrams": 0.
- Otherwise set "clarifyingQuestion" to null.
- Never include any text outside the JSON object. Never include insulin or dose information.
"""

    /** Only the meal text, plus our own clarifying questions and the patient's answers, is sent. */
    fun carbEstimateRequest(mealText: String, clarifications: List<Pair<String, String>> = emptyList()): String =
        buildString {
            appendLine("MEAL:")
            appendLine(mealText.trim())
            clarifications.filter { it.first.isNotBlank() && it.second.isNotBlank() }.forEach { (question, answer) ->
                appendLine()
                appendLine("YOU ASKED: ${question.trim()}")
                appendLine("ANSWER: ${answer.trim()}")
            }
        }

    const val DRAFT_SYSTEM = """
You are preparing a DRAFT reply for a licensed doctor to review inside an academic prototype.
The patient asked their doctor a question about insulin. The doctor will read, edit and
explicitly approve your draft before the patient ever sees it. The patient cannot see this.

Write the draft so the doctor can send it after review:
- Open by restating what the patient asked, in one sentence.
- Summarise what the attached records do and do not show. Never invent a number, a date or a
  record that is not in the context given to you.
- Where information is missing, say plainly what is missing and why it matters.
- Do NOT state a specific insulin dose or a dose change as a decision. Dosing is the doctor's
  call. You may list the factors the doctor may want to weigh, and questions worth asking.
- Keep it under 180 words, plain language, no markdown headings, written to the patient in
  the second person so the doctor can send it with light edits.
"""

    const val SUMMARY_SYSTEM = """
You write a short, plain-language weekly overview for a patient in a diabetes prototype app.
You are given already-computed statistics. Rules:
- Use only the numbers given. Never invent or recompute a figure.
- Two to four short sentences. Plain words, no markdown, no headings, no bullet points.
- Describe the pattern and one general, non-medical suggestion such as logging more often.
- Never mention insulin doses, dose changes, diagnoses, or whether readings are dangerous.
- If the data is thin, say so plainly instead of drawing a conclusion.
"""

    /** Renders the de-identified clinical context attached to an insulin-advice request. */
    fun draftContext(question: String, context: RequestContext): String = buildString {
        appendLine("PATIENT QUESTION:")
        appendLine(question.ifBlank { "(no question text supplied)" })
        appendLine()
        appendLine("PATIENT CONTEXT (de-identified):")
        appendLine("- Age: ${context.age?.toString() ?: "not recorded"}")
        appendLine("- Diabetes type: ${context.diabetesType.ifBlank { "not recorded" }}")
        appendLine("- Relevant history: ${context.relevantHistory.ifBlank { "not recorded" }}")
        appendLine("- Existing care plan: ${context.carePlan.ifBlank { "not recorded" }}")
        appendLine()
        appendLine("LATEST GLUCOSE:")
        appendLine(
            context.latestValueMgdl?.let { "- ${it.toInt()} mg/dL" } ?: "- no readings recorded"
        )
        appendLine()
        appendLine("RECENT READINGS (newest first):")
        if (context.recentReadings.isEmpty()) appendLine("- none recorded")
        else context.recentReadings.forEach { appendLine("- $it") }
        appendLine()
        appendLine("RECENT MEDICATION AND INSULIN DOSES (newest first):")
        if (context.recentDoses.isEmpty()) appendLine("- none recorded")
        else context.recentDoses.forEach { appendLine("- $it") }
        if (context.missingData.isNotEmpty()) {
            appendLine()
            appendLine("KNOWN GAPS:")
            context.missingData.forEach { appendLine("- $it") }
        }
    }
}

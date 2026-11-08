package com.z.diabetescompanion.domain

/**
 * Code-side safety net behind the system prompt: detects AI text that states an insulin
 * amount ("take 6 units", "a 6-unit bolus", "6 I.U.", "five units", "your dose is 7",
 * "7.5 of rapid insulin"). Any AI text that trips this is never shown; the caller replaces it
 * with a fixed message. Deliberately conservative: a false positive only costs one replaced
 * answer, a false negative could look like dosing advice.
 */
object DoseGuard {

    /** 6, 7.5, 7,5, .5, and vulgar fractions (U+00BC..U+00BE) alone or after digits. */
    private const val NUMBER =
        """(?:\d+(?:[.,]\d+)?(?:\s*[\u00BC-\u00BE])?|[\u00BC-\u00BE]|\d*[.,]\d+)"""
    private const val WORD_NUMBER =
        """(?:half|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|""" +
            """sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|a couple of|a few|several)"""
    /** "unit(s)", "u", "iu", "IU", "I.U.", "I.U" - followed by a non-word character or the end. */
    private const val UNIT_WORD = """(?:units?|u|iu|i\.u\.?)(?!\w)"""
    /** Quantities that are clearly not insulin units. */
    private const val NOT_UNITS =
        """(?!\s*(?:g|grams?|gm|kg|mg/dl|mg|mmol(?:/l)?|%|minutes?|mins?|hours?|hrs?|days?|times?|slices?|cups?|pieces?|steps?|diabetes|years?)\b)"""

    private val unitPatterns = listOf(
        // "6 units", "6u", "4-6 units", "4 to 6 IU", "6-unit bolus", "6 I.U."
        Regex("""(?<![\w.])$NUMBER[-\s]*(?:(?:-|to|or)\s*$NUMBER[-\s]*)?$UNIT_WORD""", RegexOption.IGNORE_CASE),
        // "five units", "half a unit", "a few units"
        Regex("""\b$WORD_NUMBER(?:\s+an?)?[-\s]+$UNIT_WORD""", RegexOption.IGNORE_CASE),
        // "7.5 of rapid insulin", "4 of insulin", "6 rapid-acting insulin" (a small fixed vocabulary,
        // so "type 1 diabetes needs insulin" is not caught)
        Regex(
            """(?<![\w.])(?<!type\s)$NUMBER\s+(?:of\s+)?(?:(?:your|rapid|fast|quick|short|long|mealtime|meal|bolus|basal)[-\s]+)?(?:acting[-\s]+)?insulin\b""",
            RegexOption.IGNORE_CASE,
        ),
    )

    /** "inject 6", "bolus of 4", "dose is 7", "dose: 6", "your insulin dose would be 6", "give yourself 4". */
    private val verbPattern = Regex(
        """\b(?:inject(?:ing|ion)?|bolus(?:ing)?|dose|dosage|take|give(?:\s+yourself)?)\b[^.\n]{0,30}?(?<![\w.])(?<!type\s)$NUMBER\b$NOT_UNITS""",
        RegexOption.IGNORE_CASE,
    )

    private val insulinContext = Regex("""\b(?:insulin|bolus|inject\w*|units?|dose|dosage)\b""", RegexOption.IGNORE_CASE)

    fun containsInsulinAmount(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val normalised = text.replace('\u00A0', ' ')
        if (unitPatterns.any { it.containsMatchIn(normalised) }) return true
        // The verb pattern only counts when the text is about insulin, so "take 2 slices" passes.
        return insulinContext.containsMatchIn(normalised) && verbPattern.containsMatchIn(normalised)
    }
}

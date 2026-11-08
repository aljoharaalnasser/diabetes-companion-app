package com.z.diabetescompanion.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Configuration for the meal insulin calculator. Kept in one place so the values can be
 * changed without touching the calculation, the UI or the tests.
 */
object MealInsulinConfig {
    /** Suggested doses are rounded DOWN to this step (units). */
    const val ROUNDING_STEP_UNITS = 0.5

    // TODO: range to be confirmed with supervisor.
    // Keep in sync with the icrSettings bounds in firestore.rules.
    const val ICR_MIN_GRAMS_PER_UNIT = 1.0
    const val ICR_MAX_GRAMS_PER_UNIT = 100.0

    // TODO: range to be confirmed with supervisor.
    const val CARBS_MAX_GRAMS = 500.0
}

/** Result of a dose calculation. [Invalid] never carries a number that could be read as a dose. */
sealed interface DoseCalculation {
    data class Ok(
        val confirmedCarbs: Double,
        val icrGramsPerUnit: Double,
        val roundingStep: Double,
        /** confirmedCarbs / icr before rounding, for the "working" line. */
        val exactUnits: Double,
        /** Rounded down to [roundingStep]. This is the suggested dose. */
        val units: Double,
    ) : DoseCalculation {
        val wasRounded: Boolean get() = exactUnits != units

        /** e.g. "75 g / ICR 10 = 7.5 units" or "64 g / ICR 10 = 6.4, rounded down to 6.0 units". */
        val working: String
            get() {
                val head = "${formatGrams(confirmedCarbs)} g / ICR ${formatGrams(icrGramsPerUnit)}"
                return if (wasRounded) {
                    "$head = ${formatExact(exactUnits)}, rounded down to ${formatUnits(units)} units"
                } else {
                    "$head = ${formatUnits(units)} units"
                }
            }
    }

    data class Invalid(val reason: String) : DoseCalculation
}

/**
 * Pure meal-insulin calculation: units = confirmedCarbs / icrGramsPerUnit, rounded DOWN to
 * [MealInsulinConfig.ROUNDING_STEP_UNITS]. No AI is involved; the carbs passed in must be the
 * value the patient confirmed (possibly edited), never the raw AI estimate.
 *
 * BigDecimal is used so that, for example, 75 / 10 is exactly 7.5 and never 7.4999999.
 */
object InsulinCalculator {

    fun calculate(
        confirmedCarbsGrams: Double?,
        icrGramsPerUnit: Double?,
        roundingStep: Double = MealInsulinConfig.ROUNDING_STEP_UNITS,
    ): DoseCalculation {
        MealInsulinValidation.carbsValueError(confirmedCarbsGrams)?.let { return DoseCalculation.Invalid(it) }
        MealInsulinValidation.icrValueError(icrGramsPerUnit)?.let { return DoseCalculation.Invalid(it) }
        if (!(roundingStep > 0.0) || roundingStep.isInfinite()) {
            return DoseCalculation.Invalid("Rounding step must be greater than zero.")
        }
        val carbs = BigDecimal(confirmedCarbsGrams!!.toString())
        val icr = BigDecimal(icrGramsPerUnit!!.toString())
        val step = BigDecimal(roundingStep.toString())

        val exact = carbs.divide(icr, 6, RoundingMode.FLOOR)
        val rounded = exact.divide(step, 0, RoundingMode.FLOOR).multiply(step)

        return DoseCalculation.Ok(
            confirmedCarbs = confirmedCarbsGrams,
            icrGramsPerUnit = icrGramsPerUnit,
            roundingStep = roundingStep,
            exactUnits = exact.stripTrailingZeros().toDouble(),
            units = rounded.toDouble(),
        )
    }
}

/** Input rules for carbs, ICR and units typed by the patient or doctor. Null = valid. */
object MealInsulinValidation {

    fun parse(raw: String?): Double? = raw?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    fun carbsError(raw: String?): String? {
        if (raw.isNullOrBlank()) return "Enter the total carbs in grams."
        val value = parse(raw) ?: return "Enter a number."
        return carbsValueError(value) ?: decimalsError(raw)
    }

    fun carbsValueError(value: Double?): String? = when {
        value == null || value.isNaN() || value.isInfinite() -> "Enter the total carbs in grams."
        value <= 0.0 -> "Carbs must be greater than zero."
        value > MealInsulinConfig.CARBS_MAX_GRAMS ->
            "Carbs can't be more than ${formatGrams(MealInsulinConfig.CARBS_MAX_GRAMS)} g for one meal."
        else -> null
    }

    fun icrError(raw: String?): String? {
        if (raw.isNullOrBlank()) return "Enter the grams of carbs covered by 1 unit."
        val value = parse(raw) ?: return "Enter a number."
        return icrValueError(value) ?: decimalsError(raw)
    }

    fun icrValueError(value: Double?): String? = when {
        value == null || value.isNaN() || value.isInfinite() -> "No insulin-to-carb ratio is set."
        value <= 0.0 -> "The ratio must be greater than zero."
        value < MealInsulinConfig.ICR_MIN_GRAMS_PER_UNIT || value > MealInsulinConfig.ICR_MAX_GRAMS_PER_UNIT ->
            "The ratio must be between ${formatGrams(MealInsulinConfig.ICR_MIN_GRAMS_PER_UNIT)} and " +
                "${formatGrams(MealInsulinConfig.ICR_MAX_GRAMS_PER_UNIT)} g per unit."
        else -> null
    }

    /** One decimal place at most, so stored values and the rules-side check stay exact. */
    private fun decimalsError(raw: String): String? {
        val decimals = raw.trim().replace(',', '.').substringAfter('.', "")
        return if (decimals.length > 1) "Use at most one decimal place." else null
    }
}

/** "1 unit for every 10 g of carbs". */
fun icrLabel(gramsPerUnit: Double): String = "1 unit for every ${formatGrams(gramsPerUnit)} g of carbs"

/** Whole numbers without a decimal, otherwise one decimal: 75 -> "75", 62.5 -> "62.5". */
fun formatGrams(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

/** Units always with one decimal, so 6 reads as "6.0" and the 0.5 step is obvious. */
fun formatUnits(value: Double): String = String.format(Locale.US, "%.1f", value)

/** Exact quotient for the working line, up to two decimals: 6.4, 6.25. */
private fun formatExact(value: Double): String =
    BigDecimal(value.toString()).setScale(2, RoundingMode.FLOOR).stripTrailingZeros().toPlainString()

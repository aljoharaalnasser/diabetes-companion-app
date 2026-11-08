package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.CarbEstimate
import com.z.diabetescompanion.data.model.DoseSuggestion
import com.z.diabetescompanion.data.model.DoseSuggestionStatus
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.MedicationKind
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.MedicationSource
import java.util.Date

/** Which ICR version applies. Versions are append-only, so "current" is simply the newest. */
object IcrPolicy {

    fun newest(versions: List<IcrSetting>): IcrSetting? =
        versions.maxWithOrNull(compareBy<IcrSetting> { it.version }.thenBy { it.effectiveFrom })

    /**
     * The version a dose may be calculated from: the newest one, and only if a doctor confirmed
     * it. A newer patient-entered value blocks dosing until the doctor confirms it, rather than
     * silently falling back to an older ratio the patient has said is out of date.
     */
    fun usableForDose(versions: List<IcrSetting>): IcrSetting? = newest(versions)?.takeIf { it.isConfirmed }

    /** The newest patient-entered version still waiting for the doctor, if any. */
    fun awaitingConfirmation(versions: List<IcrSetting>): IcrSetting? = newest(versions)?.takeIf { !it.isConfirmed }

    fun nextVersion(versions: List<IcrSetting>): Int = (versions.maxOfOrNull { it.version } ?: 0) + 1
}

/** What the chat shows after the patient confirms the carbs. */
sealed interface MealDoseOutcome {
    /** A dose card can be shown. */
    data class DoseReady(val calculation: DoseCalculation.Ok, val icr: IcrSetting) : MealDoseOutcome

    /** An ICR exists but the newest version is not confirmed by the doctor. No dose card. */
    data class NeedsDoctorConfirmation(val pending: IcrSetting) : MealDoseOutcome

    /** No ICR at all. No dose card; the chat asks for one. */
    data object NoIcr : MealDoseOutcome

    /** The confirmed carbs failed validation. */
    data class InvalidCarbs(val message: String) : MealDoseOutcome

    /** The dose rounds down to 0 units, so no dose card (there is nothing to take or log). */
    data class BelowMinimumDose(val calculation: DoseCalculation.Ok) : MealDoseOutcome
}

object MealInsulinFlow {

    const val NEEDS_CONFIRMATION_MESSAGE =
        "Your ICR needs your doctor's confirmation before a dose can be calculated."

    fun belowMinimumMessage(calc: DoseCalculation.Ok): String =
        "${calc.working}. That is less than one ${formatUnits(calc.roundingStep)}-unit step, so no dose is suggested."

    const val ICR_NOT_LOADED_MESSAGE =
        "Your insulin-to-carb ratio hasn't loaded yet. Check your connection and try again."

    const val DOSE_DISCLAIMER =
        "Academic prototype. Not a certified medical device. Follow your doctor's advice."

    /**
     * Decides the outcome from the carbs the patient CONFIRMED (typed or edited in the carb
     * card), never from the AI estimate, plus the patient's ICR versions.
     */
    fun evaluate(
        confirmedCarbsText: String,
        icrVersions: List<IcrSetting>,
        roundingStep: Double = MealInsulinConfig.ROUNDING_STEP_UNITS,
    ): MealDoseOutcome {
        MealInsulinValidation.carbsError(confirmedCarbsText)?.let { return MealDoseOutcome.InvalidCarbs(it) }
        val carbs = MealInsulinValidation.parse(confirmedCarbsText)!!

        if (icrVersions.isEmpty()) return MealDoseOutcome.NoIcr
        val icr = IcrPolicy.usableForDose(icrVersions)
            ?: return MealDoseOutcome.NeedsDoctorConfirmation(IcrPolicy.awaitingConfirmation(icrVersions)!!)

        return when (val calc = InsulinCalculator.calculate(carbs, icr.gramsPerUnit, roundingStep)) {
            is DoseCalculation.Ok ->
                if (calc.units <= 0.0) MealDoseOutcome.BelowMinimumDose(calc) else MealDoseOutcome.DoseReady(calc, icr)
            is DoseCalculation.Invalid -> MealDoseOutcome.InvalidCarbs(calc.reason)
        }
    }
}

/** Builders for the records the meal flow writes, kept pure so they can be unit tested. */
object MealInsulinRecords {

    const val MEDICATION_NAME = "Meal insulin"
    const val MEDICATION_UNIT = "units"

    fun newSuggestion(
        suggestionId: String,
        patientId: String,
        mealText: String,
        estimate: CarbEstimate,
        ready: MealDoseOutcome.DoseReady,
        now: Date = Date(),
    ): DoseSuggestion = DoseSuggestion(
        suggestionId = suggestionId,
        patientId = patientId,
        mealText = mealText,
        aiItems = estimate.items,
        aiCarbs = estimate.totalCarbsGrams,
        confirmedCarbs = ready.calculation.confirmedCarbs,
        icrId = ready.icr.icrId,
        icrVersion = ready.icr.version,
        icrValue = ready.icr.gramsPerUnit,
        roundingStep = ready.calculation.roundingStep,
        suggestedUnits = ready.calculation.units,
        status = DoseSuggestionStatus.Suggested,
        createdAt = now,
        updatedAt = now,
    )

    /** Deterministic so a double tap on "I took" writes the same log entry, not two. */
    fun medicationLogIdFor(suggestionId: String): String = "meal_$suggestionId"

    /** The insulin entry "I took" writes to the existing medication log. */
    fun takenLog(suggestion: DoseSuggestion, takenUnits: Double, takenAt: Date = Date()): MedicationLog = MedicationLog(
        id = medicationLogIdFor(suggestion.suggestionId),
        name = MEDICATION_NAME,
        dose = takenUnits,
        unit = MEDICATION_UNIT,
        kind = MedicationKind.Insulin,
        takenAt = takenAt,
        note = "Meal insulin calculator: ${formatGrams(suggestion.confirmedCarbs)} g / ICR " +
            "${formatGrams(suggestion.icrValue)}, suggested ${formatUnits(suggestion.suggestedUnits)} units",
        source = MedicationSource.CalculatorSuggestion,
        suggestionId = suggestion.suggestionId,
    )

    fun markTaken(suggestion: DoseSuggestion, takenUnits: Double, medicationLogId: String, now: Date = Date()) =
        suggestion.copy(
            status = DoseSuggestionStatus.Taken,
            takenUnits = takenUnits,
            medicationLogId = medicationLogId,
            updatedAt = now,
        )

    fun markSkipped(suggestion: DoseSuggestion, now: Date = Date()) =
        suggestion.copy(status = DoseSuggestionStatus.Skipped, updatedAt = now)
}

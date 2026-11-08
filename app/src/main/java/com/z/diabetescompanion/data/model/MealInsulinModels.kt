package com.z.diabetescompanion.data.model

import java.util.Date

// ---------------------------------------------------------------------------
// Meal insulin calculator: AI carb estimates, the patient's insulin-to-carb ratio (ICR),
// and the dose suggestions the app calculates from them. The AI only ever estimates carbs;
// the dose is always calculated on-device by domain/InsulinCalculator.kt.
// Firestore mapping lives in MealInsulinMappers.kt so these stay free of Firebase types.
// ---------------------------------------------------------------------------

/** One food line in the AI carb estimate. */
data class CarbItem(
    val name: String,
    val assumedPortion: String,
    val carbsGrams: Double,
)

/** Parsed AI response for a carb request. Never contains a dose. */
data class CarbEstimate(
    val items: List<CarbItem>,
    val totalCarbsGrams: Double,
    val clarifyingQuestion: String? = null,
)

enum class IcrStatus(val label: String, val storageKey: String) {
    Unconfirmed("Awaiting doctor confirmation", "UNCONFIRMED"),
    Confirmed("Confirmed by doctor", "CONFIRMED");

    companion object {
        fun fromKey(key: String?): IcrStatus = entries.firstOrNull { it.storageKey == key } ?: Unconfirmed
    }
}

enum class IcrEnteredBy(val label: String, val storageKey: String) {
    Patient("Patient", "PATIENT"),
    Doctor("Doctor", "DOCTOR");

    companion object {
        fun fromKey(key: String?): IcrEnteredBy = entries.firstOrNull { it.storageKey == key } ?: Patient
    }
}

/**
 * One version of the patient's insulin-to-carb ratio: "1 unit for every [gramsPerUnit] g".
 * Versions are append-only. A new value, or a doctor confirming a patient value, always
 * creates a new version, so a past dose suggestion can always be traced to the exact ratio used.
 */
data class IcrSetting(
    val icrId: String,
    val patientId: String,
    val gramsPerUnit: Double,
    val status: IcrStatus,
    val enteredBy: IcrEnteredBy,
    /** 1, 2, 3 ... per patient. Higher is newer. */
    val version: Int,
    val confirmedByDoctorId: String = "",
    val confirmedByDoctorName: String = "",
    /** Set when a doctor confirms a patient-entered version: the version that was confirmed. */
    val confirmsIcrId: String = "",
    val effectiveFrom: Date,
    val createdAt: Date? = null,
) {
    val isConfirmed: Boolean get() = status == IcrStatus.Confirmed
}

enum class DoseSuggestionStatus(val label: String, val storageKey: String) {
    Suggested("Pending", "SUGGESTED"),
    Taken("Taken", "TAKEN"),
    Skipped("Not now", "SKIPPED");

    companion object {
        fun fromKey(key: String?): DoseSuggestionStatus = entries.firstOrNull { it.storageKey == key } ?: Suggested
    }
}

/**
 * A meal dose the app calculated. [icrValue] and [icrVersion] are copied from the ICR version
 * used at the time, so later ICR changes never alter what this record says.
 */
data class DoseSuggestion(
    val suggestionId: String,
    val patientId: String,
    val mealText: String,
    val aiItems: List<CarbItem>,
    val aiCarbs: Double,
    val confirmedCarbs: Double,
    val icrId: String,
    val icrVersion: Int,
    val icrValue: Double,
    val roundingStep: Double,
    val suggestedUnits: Double,
    val takenUnits: Double? = null,
    val status: DoseSuggestionStatus = DoseSuggestionStatus.Suggested,
    /** Medication log entry written by "I took", if any. */
    val medicationLogId: String = "",
    val createdAt: Date,
    val updatedAt: Date? = null,
)

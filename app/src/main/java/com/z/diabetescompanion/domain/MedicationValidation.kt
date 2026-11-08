package com.z.diabetescompanion.domain

import java.util.Date
import java.util.concurrent.TimeUnit

/** Rules for FR-06 medication logs and FR-07 reminders. */
object MedicationValidation {

    val doseUnits = listOf("mg", "units", "mL", "tablet(s)")

    fun nameError(name: String): String? = when {
        name.isBlank() -> "Enter the medication name."
        name.trim().length < 2 -> "Name is too short."
        else -> null
    }

    fun doseError(raw: String): String? {
        if (raw.isBlank()) return "Enter the dose."
        val dose = raw.replace(',', '.').toDoubleOrNull() ?: return "Enter a number."
        return when {
            dose <= 0 -> "Dose must be greater than zero."
            dose > 10_000 -> "That dose looks too large."
            else -> null
        }
    }

    fun takenAtError(takenAt: Date, now: Date = Date()): String? = when {
        takenAt.after(Date(now.time + TimeUnit.MINUTES.toMillis(5))) -> "Time can't be in the future."
        takenAt.before(Date(now.time - TimeUnit.DAYS.toMillis(366))) -> "Time can't be more than a year ago."
        else -> null
    }
}

object ThresholdValidation {
    private const val MIN = 20
    private const val MAX = 600

    fun fieldError(raw: String): String? {
        if (raw.isBlank()) return "Required."
        val value = raw.toIntOrNull() ?: return "Whole number in mg/dL."
        return if (value < MIN || value > MAX) "Must be between $MIN and $MAX." else null
    }

    /** Ordering rule from S1-05: criticalLow < targetLow < targetHigh < criticalHigh. */
    fun orderingError(criticalLow: Int, targetLow: Int, targetHigh: Int, criticalHigh: Int): String? = when {
        criticalLow >= targetLow -> "Critical low must be below the target low."
        targetLow >= targetHigh -> "Target low must be below the target high."
        targetHigh >= criticalHigh -> "Critical high must be above the target high."
        else -> null
    }
}

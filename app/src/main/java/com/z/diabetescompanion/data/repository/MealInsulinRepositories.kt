package com.z.diabetescompanion.data.repository

import com.z.diabetescompanion.data.model.DoseSuggestion
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow

/** Versioned insulin-to-carb ratios. Append-only: there is no update or delete. */
interface IcrRepository {
    /** Every version for the patient, newest first. */
    fun versions(patientUid: String): Flow<List<IcrSetting>>
    suspend fun listOnce(patientUid: String): List<IcrSetting>
    suspend fun create(setting: IcrSetting)
}

/** Meal dose suggestions calculated by the app (never by the AI). */
interface DoseSuggestionRepository {
    /** Newest first. */
    fun suggestions(patientUid: String, limit: Long = 100): Flow<List<DoseSuggestion>>
    suspend fun create(suggestion: DoseSuggestion)

    /** Writes the medication log entry and marks the suggestion TAKEN in one atomic batch. */
    suspend fun recordTaken(patientUid: String, suggestionId: String, takenUnits: Double, log: MedicationLog)
    suspend fun markSkipped(patientUid: String, suggestionId: String)
}

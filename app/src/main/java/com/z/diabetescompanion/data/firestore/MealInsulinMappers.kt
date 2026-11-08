package com.z.diabetescompanion.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.z.diabetescompanion.data.model.CarbItem
import com.z.diabetescompanion.data.model.DoseSuggestion
import com.z.diabetescompanion.data.model.DoseSuggestionStatus
import com.z.diabetescompanion.data.model.IcrEnteredBy
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.IcrStatus
import java.util.Date

object MealInsulinSchema {
    /** users/{uid}/icrSettings/{icrId} - append-only ICR versions. */
    const val ICR_SETTINGS = "icrSettings"
    /** users/{uid}/doseSuggestions/{suggestionId} - calculator output and its outcome. */
    const val DOSE_SUGGESTIONS = "doseSuggestions"
}

private fun DocumentSnapshot.date(field: String): Date? = getTimestamp(field)?.toDate()
private fun DocumentSnapshot.str(field: String): String = getString(field).orEmpty()
private fun DocumentSnapshot.num(field: String): Double? = (get(field) as? Number)?.toDouble()

// --- IcrSetting ------------------------------------------------------------

fun IcrSetting.toMap(): Map<String, Any?> = mapOf(
    "icrId" to icrId,
    "patientId" to patientId,
    "gramsPerUnit" to gramsPerUnit,
    "status" to status.storageKey,
    "enteredBy" to enteredBy.storageKey,
    "version" to version,
    "confirmedByDoctorId" to confirmedByDoctorId,
    "confirmedByDoctorName" to confirmedByDoctorName,
    "confirmsIcrId" to confirmsIcrId,
    "effectiveFrom" to Timestamp(effectiveFrom),
    "createdAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toIcrSetting(): IcrSetting? {
    if (!exists()) return null
    val grams = num("gramsPerUnit") ?: return null
    return IcrSetting(
        icrId = id,
        patientId = str("patientId"),
        gramsPerUnit = grams,
        status = IcrStatus.fromKey(getString("status")),
        enteredBy = IcrEnteredBy.fromKey(getString("enteredBy")),
        version = getLong("version")?.toInt() ?: 0,
        confirmedByDoctorId = str("confirmedByDoctorId"),
        confirmedByDoctorName = str("confirmedByDoctorName"),
        confirmsIcrId = str("confirmsIcrId"),
        effectiveFrom = date("effectiveFrom") ?: date("createdAt") ?: Date(0),
        createdAt = date("createdAt"),
    )
}

// --- DoseSuggestion --------------------------------------------------------

private fun CarbItem.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "assumedPortion" to assumedPortion,
    "carbsGrams" to carbsGrams,
)

@Suppress("UNCHECKED_CAST")
private fun Any?.toCarbItems(): List<CarbItem> =
    (this as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
        val grams = (m["carbsGrams"] as? Number)?.toDouble() ?: return@mapNotNull null
        CarbItem(
            name = m["name"] as? String ?: "",
            assumedPortion = m["assumedPortion"] as? String ?: "",
            carbsGrams = grams,
        )
    }

/** createdAt is the client time of the meal so the list orders correctly even offline. */
fun DoseSuggestion.toMap(): Map<String, Any?> = mapOf(
    "suggestionId" to suggestionId,
    "patientId" to patientId,
    "mealText" to mealText,
    "aiItems" to aiItems.map { it.toMap() },
    "aiCarbs" to aiCarbs,
    "confirmedCarbs" to confirmedCarbs,
    "icrId" to icrId,
    "icrVersion" to icrVersion,
    "icrValue" to icrValue,
    "roundingStep" to roundingStep,
    "suggestedUnits" to suggestedUnits,
    "takenUnits" to takenUnits,
    "status" to status.storageKey,
    "medicationLogId" to medicationLogId,
    "createdAt" to Timestamp(createdAt),
    "updatedAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toDoseSuggestion(): DoseSuggestion? {
    if (!exists()) return null
    return DoseSuggestion(
        suggestionId = id,
        patientId = str("patientId"),
        mealText = str("mealText"),
        aiItems = get("aiItems").toCarbItems(),
        aiCarbs = num("aiCarbs") ?: 0.0,
        confirmedCarbs = num("confirmedCarbs") ?: return null,
        icrId = str("icrId"),
        icrVersion = getLong("icrVersion")?.toInt() ?: 0,
        icrValue = num("icrValue") ?: return null,
        roundingStep = num("roundingStep") ?: 0.0,
        suggestedUnits = num("suggestedUnits") ?: return null,
        takenUnits = num("takenUnits"),
        status = DoseSuggestionStatus.fromKey(getString("status")),
        medicationLogId = str("medicationLogId"),
        createdAt = date("createdAt") ?: Date(0),
        updatedAt = date("updatedAt"),
    )
}

package com.z.diabetescompanion.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.MedicationKind
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.ReadingSource
import com.z.diabetescompanion.data.model.Reminder
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import java.util.Date

/**
 * Hand-written mappers instead of reflection-based toObject(): explicit field names,
 * enum keys that never drift, and no ProGuard surprises.
 */
object FirestoreSchema {
    const val USERS = "users"
    const val READINGS = "readings"
    const val MEDICATIONS = "medications"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val THRESHOLDS_DOC = "thresholds"
    const val CARE_LINKS = "careLinks"
    const val DELETION_REQUESTS = "deletionRequests"
}

private fun DocumentSnapshot.date(field: String): Date? = getTimestamp(field)?.toDate()
private fun DocumentSnapshot.int(field: String): Int? = getLong(field)?.toInt()
private fun DocumentSnapshot.str(field: String): String = getString(field).orEmpty()

// --- UserProfile -----------------------------------------------------------

fun UserProfile.toMap(isNew: Boolean): Map<String, Any?> = buildMap {
    put("role", role.storageKey)
    put("displayName", displayName)
    put("email", email)
    put("age", age)
    put("diabetesType", diabetesType)
    put("relevantHistory", relevantHistory)
    put("carePlan", carePlan)
    put("updatedAt", FieldValue.serverTimestamp())
    if (isNew) put("createdAt", FieldValue.serverTimestamp())
}

fun DocumentSnapshot.toUserProfile(): UserProfile? {
    if (!exists()) return null
    val role = UserRole.fromKey(getString("role")) ?: return null
    return UserProfile(
        uid = id,
        role = role,
        displayName = str("displayName"),
        email = str("email"),
        age = int("age"),
        diabetesType = str("diabetesType"),
        relevantHistory = str("relevantHistory"),
        carePlan = str("carePlan"),
        createdAt = date("createdAt"),
        updatedAt = date("updatedAt"),
    )
}

// --- GlucoseReading --------------------------------------------------------

fun GlucoseReading.toMap(): Map<String, Any?> = mapOf(
    "valueMgdl" to valueMgdl,
    "enteredValue" to enteredValue,
    "enteredUnit" to enteredUnit.storageKey,
    "source" to source.storageKey,
    "recordedAt" to Timestamp(recordedAt),
    "note" to note,
    "createdAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toGlucoseReading(): GlucoseReading? {
    val value = getDouble("valueMgdl") ?: return null
    val recordedAt = date("recordedAt") ?: return null
    return GlucoseReading(
        id = id,
        valueMgdl = value,
        enteredValue = getDouble("enteredValue") ?: value,
        enteredUnit = GlucoseUnit.fromKey(getString("enteredUnit")),
        source = ReadingSource.fromKey(getString("source")),
        recordedAt = recordedAt,
        note = str("note"),
        createdAt = date("createdAt"),
    )
}

// --- MedicationLog ---------------------------------------------------------

fun MedicationLog.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "dose" to dose,
    "unit" to unit,
    "kind" to kind.storageKey,
    "takenAt" to Timestamp(takenAt),
    "note" to note,
    "createdAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toMedicationLog(): MedicationLog? {
    val takenAt = date("takenAt") ?: return null
    return MedicationLog(
        id = id,
        name = str("name"),
        dose = getDouble("dose") ?: 0.0,
        unit = str("unit"),
        kind = MedicationKind.fromKey(getString("kind")),
        takenAt = takenAt,
        note = str("note"),
        createdAt = date("createdAt"),
    )
}

// --- Reminder --------------------------------------------------------------

fun Reminder.toMap(isNew: Boolean): Map<String, Any?> = buildMap {
    put("medicationName", medicationName)
    put("hour", hour)
    put("minute", minute)
    put("days", days)
    put("enabled", enabled)
    if (isNew) put("createdAt", FieldValue.serverTimestamp())
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toReminder(): Reminder? {
    val hour = int("hour") ?: return null
    val minute = int("minute") ?: return null
    val days = (get("days") as? List<Number>)?.map { it.toInt() } ?: emptyList()
    return Reminder(
        id = id,
        medicationName = str("medicationName"),
        hour = hour,
        minute = minute,
        days = days,
        enabled = getBoolean("enabled") ?: true,
        createdAt = date("createdAt"),
    )
}

// --- Thresholds ------------------------------------------------------------

fun Thresholds.toMap(): Map<String, Any?> = mapOf(
    "targetLow" to targetLow,
    "targetHigh" to targetHigh,
    "criticalLow" to criticalLow,
    "criticalHigh" to criticalHigh,
    "updatedByUid" to updatedByUid,
    "updatedByName" to updatedByName,
    "updatedAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toThresholds(): Thresholds? {
    if (!exists()) return null
    return Thresholds(
        targetLow = int("targetLow") ?: return null,
        targetHigh = int("targetHigh") ?: return null,
        criticalLow = int("criticalLow") ?: return null,
        criticalHigh = int("criticalHigh") ?: return null,
        updatedByUid = str("updatedByUid"),
        updatedByName = str("updatedByName"),
        updatedAt = date("updatedAt"),
    )
}

// --- CareLink --------------------------------------------------------------

fun CareLink.toMap(): Map<String, Any?> = mapOf(
    "patientUid" to patientUid,
    "patientName" to patientName,
    "memberUid" to memberUid,
    "memberName" to memberName,
    "memberRole" to memberRole.storageKey,
    "status" to status.storageKey,
    "grantedAt" to FieldValue.serverTimestamp(),
    "revokedAt" to null,
)

fun DocumentSnapshot.toCareLink(): CareLink? {
    if (!exists()) return null
    return CareLink(
        id = id,
        patientUid = str("patientUid"),
        patientName = str("patientName"),
        memberUid = str("memberUid"),
        memberName = str("memberName"),
        memberRole = UserRole.fromKey(getString("memberRole")) ?: return null,
        status = CareLinkStatus.fromKey(getString("status")),
        grantedAt = date("grantedAt"),
        revokedAt = date("revokedAt"),
    )
}

fun DocumentSnapshot.toDeletionRequest(): DeletionRequest? {
    if (!exists()) return null
    return DeletionRequest(
        id = id,
        uid = str("uid"),
        requestedAt = date("requestedAt"),
        status = str("status").ifEmpty { "pending" },
    )
}

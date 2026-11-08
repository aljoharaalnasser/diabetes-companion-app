package com.z.diabetescompanion.data.model

import java.util.Date

// ---------------------------------------------------------------------------
// Shared domain model for Sprint 1. Firestore mapping lives in FirestoreMappers.kt
// so these classes stay free of Firebase types.
// ---------------------------------------------------------------------------

enum class UserRole(val label: String, val storageKey: String) {
    Patient("Patient", "patient"),
    Doctor("Doctor", "doctor"),
    Caregiver("Caregiver", "caregiver"),
    Admin("Admin", "admin");

    companion object {
        fun fromKey(key: String?): UserRole? = entries.firstOrNull { it.storageKey == key }
    }
}

data class UserProfile(
    val uid: String,
    val role: UserRole,
    val displayName: String,
    val email: String,
    /** Patient-only fields (FR-02). */
    val age: Int? = null,
    val diabetesType: String = "",
    val relevantHistory: String = "",
    val carePlan: String = "",
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
) {
    val initials: String
        get() = displayName.split(' ').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }.ifEmpty { "?" }

    val firstName: String get() = displayName.trim().substringBefore(' ').ifBlank { "there" }
}

enum class GlucoseUnit(val label: String, val storageKey: String) {
    MgDl("mg/dL", "mg_dl"),
    MmolL("mmol/L", "mmol_l");

    fun toMgdl(value: Double): Double = if (this == MgDl) value else value * MMOL_TO_MGDL
    fun fromMgdl(mgdl: Double): Double = if (this == MgDl) mgdl else mgdl / MMOL_TO_MGDL

    companion object {
        const val MMOL_TO_MGDL = 18.0182
        fun fromKey(key: String?): GlucoseUnit = entries.firstOrNull { it.storageKey == key } ?: MgDl
    }
}

enum class ReadingSource(val label: String, val storageKey: String) {
    Manual("Manual entry", "manual"),
    Demo("Demo reading", "demo");

    companion object {
        fun fromKey(key: String?): ReadingSource = entries.firstOrNull { it.storageKey == key } ?: Manual
    }
}

data class GlucoseReading(
    val id: String,
    val valueMgdl: Double,
    /** What the user typed, in [enteredUnit], preserved for display fidelity. */
    val enteredValue: Double,
    val enteredUnit: GlucoseUnit,
    val source: ReadingSource,
    val recordedAt: Date,
    val note: String = "",
    val createdAt: Date? = null,
) {
    val valueMgdlRounded: Int get() = valueMgdl.toInt()
}

enum class MedicationKind(val label: String, val storageKey: String) {
    Medication("Medication", "medication"),
    Insulin("Insulin", "insulin");

    companion object {
        fun fromKey(key: String?): MedicationKind = entries.firstOrNull { it.storageKey == key } ?: Medication
    }
}

data class MedicationLog(
    val id: String,
    val name: String,
    val dose: Double,
    val unit: String,
    val kind: MedicationKind,
    val takenAt: Date,
    val note: String = "",
    val createdAt: Date? = null,
)

data class Reminder(
    val id: String,
    val medicationName: String,
    val hour: Int,
    val minute: Int,
    /** java.util.Calendar day constants (1 = Sunday ... 7 = Saturday). Empty = every day. */
    val days: List<Int> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: Date? = null,
) {
    val isDaily: Boolean get() = days.isEmpty() || days.size == 7
}

/** Doctor-configured ranges (FR-05). Absent until the assigned doctor sets them. */
data class Thresholds(
    val targetLow: Int,
    val targetHigh: Int,
    val criticalLow: Int,
    val criticalHigh: Int,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val updatedAt: Date? = null,
)

enum class CareLinkStatus(val storageKey: String) {
    Active("active"),
    Revoked("revoked");

    companion object {
        fun fromKey(key: String?): CareLinkStatus = entries.firstOrNull { it.storageKey == key } ?: Revoked
    }
}

/** Consent record linking a patient with one care-team member (FR-21). */
data class CareLink(
    val id: String,
    val patientUid: String,
    val patientName: String,
    val memberUid: String,
    val memberName: String,
    val memberRole: UserRole,
    val status: CareLinkStatus,
    val grantedAt: Date? = null,
    val revokedAt: Date? = null,
) {
    companion object {
        fun idFor(patientUid: String, memberUid: String) = "${patientUid}_$memberUid"
    }
}

data class DeletionRequest(
    val id: String,
    val uid: String,
    val requestedAt: Date? = null,
    val status: String = "pending",
)

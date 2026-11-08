package com.z.diabetescompanion.data.model

import java.util.Date

// ---------------------------------------------------------------------------
// Sprint 2: alerts and escalation, care-team messaging, insulin-advice requests.
// Firestore mapping lives in FirestoreMappers.kt so these stay free of Firebase types.
// ---------------------------------------------------------------------------

/** Warning = outside the target range. Critical = past a critical threshold (FR-08). */
enum class AlertSeverity(val label: String, val storageKey: String) {
    Warning("Warning", "warning"),
    Critical("Critical", "critical");

    companion object {
        fun fromKey(key: String?): AlertSeverity = entries.firstOrNull { it.storageKey == key } ?: Warning
    }
}

/**
 * How far an alert has travelled through the escalation chain (FR-09).
 * The stage only ever moves forward, and [Resolved] is terminal.
 */
enum class AlertStage(val label: String, val storageKey: String, val order: Int) {
    Created("Created", "created", 0),
    CaregiverNotified("Caregiver notified", "caregiver_notified", 1),
    DoctorNotified("Doctor notified", "doctor_notified", 2),
    Resolved("Resolved", "resolved", 3);

    companion object {
        fun fromKey(key: String?): AlertStage = entries.firstOrNull { it.storageKey == key } ?: Created
    }
}

/** The four actions the charter requires on a warning alert (FR-08). */
enum class PatientResponse(val label: String, val storageKey: String) {
    ImOk("I'm OK", "im_ok"),
    Treating("Treating", "treating"),
    NeedHelp("Need Help", "need_help"),
    ContactDoctor("Contact Doctor", "contact_doctor");

    /** Need Help goes straight to both caregiver and doctor; the calm answers stop the timer. */
    val stopsEscalation: Boolean get() = this == ImOk || this == Treating

    companion object {
        fun fromKey(key: String?): PatientResponse? = entries.firstOrNull { it.storageKey == key }
    }
}

/** One entry in the alert timeline: Created -> Acknowledged -> ... -> Resolved (FR-10). */
data class AlertEvent(
    val type: String,
    val label: String,
    val at: Date,
    val actorName: String = "",
    val note: String = "",
)

data class Alert(
    val id: String,
    val patientUid: String,
    val patientName: String,
    val valueMgdl: Double,
    val recordedAt: Date,
    val severity: AlertSeverity,
    val stage: AlertStage,
    val statusLabel: String,
    val patientResponse: PatientResponse? = null,
    val respondedAt: Date? = null,
    val caregiverNotifiedAt: Date? = null,
    val doctorNotifiedAt: Date? = null,
    val resolvedByUid: String = "",
    val resolvedByName: String = "",
    val resolvedByRole: UserRole? = null,
    val resolutionNote: String = "",
    val resolvedAt: Date? = null,
    val events: List<AlertEvent> = emptyList(),
    val createdAt: Date? = null,
) {
    val isResolved: Boolean get() = stage == AlertStage.Resolved
    val isAcknowledged: Boolean get() = patientResponse != null

    /** Acknowledgment is deliberately NOT resolution (FR-10). */
    val acknowledgedButOpen: Boolean get() = isAcknowledged && !isResolved

    companion object {
        /** Deterministic id from the reading, so one reading can never raise two alerts. */
        fun idForReading(readingId: String) = "alert_$readingId"
    }
}

// ---------------------------------------------------------------------------
// Messaging (FR-15)
// ---------------------------------------------------------------------------

data class Conversation(
    val id: String,
    val patientUid: String,
    val patientName: String,
    val doctorUid: String,
    val doctorName: String,
    /** The non-doctor side: the patient themselves, or their caregiver. */
    val memberUid: String,
    val memberName: String,
    val memberRole: UserRole,
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageAt: Date? = null,
    val lastSenderUid: String = "",
) {
    fun otherPartyName(viewerUid: String): String =
        if (viewerUid == doctorUid) memberName else doctorName

    /** Shown as a subtitle so every conversation states whose records it concerns. */
    val patientContext: String
        get() = if (memberRole == UserRole.Patient) "About your records"
        else "About $patientName"

    companion object {
        fun idFor(patientUid: String, memberUid: String, doctorUid: String) =
            "${patientUid}_${memberUid}_$doctorUid"
    }
}

data class Message(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val senderRole: UserRole,
    val text: String,
    val sentAt: Date,
)

// ---------------------------------------------------------------------------
// Insulin-advice requests (FR-17, FR-18, FR-19)
// ---------------------------------------------------------------------------

enum class RequestStatus(val label: String, val storageKey: String) {
    Pending("Pending doctor review", "pending"),
    Responded("Answered", "responded");

    companion object {
        fun fromKey(key: String?): RequestStatus = entries.firstOrNull { it.storageKey == key } ?: Pending
    }
}

/**
 * The records attached to a request so the doctor reviews it with context (FR-18).
 * Captured at submission time so the doctor sees what the patient saw.
 */
data class RequestContext(
    val age: Int? = null,
    val diabetesType: String = "",
    val carePlan: String = "",
    val relevantHistory: String = "",
    val latestValueMgdl: Double? = null,
    val latestReadingAt: Date? = null,
    val recentReadings: List<String> = emptyList(),
    val recentDoses: List<String> = emptyList(),
) {
    /** Gaps the doctor must see before trusting any draft (FR-18, NFR-15). */
    val missingData: List<String>
        get() = buildList {
            if (age == null) add("Age not recorded")
            if (diabetesType.isBlank()) add("Diabetes type not recorded")
            if (carePlan.isBlank()) add("No care plan on file")
            if (latestValueMgdl == null) add("No glucose readings")
            if (recentDoses.isEmpty()) add("No recent insulin or medication doses")
        }
}

/** The AI draft. Stored in a doctor-only subdocument so patients can never read it. */
data class AiDraft(
    val text: String,
    val model: String,
    val generatedAt: Date? = null,
    val failed: Boolean = false,
    val failureMessage: String = "",
)

data class DoctorResponse(
    val text: String,
    val doctorUid: String,
    val doctorName: String,
    val usedDraft: Boolean,
    val submittedAt: Date? = null,
)

data class InsulinRequest(
    val id: String,
    val patientUid: String,
    val patientName: String,
    val doctorUid: String,
    val doctorName: String,
    val question: String,
    val context: RequestContext,
    val status: RequestStatus,
    val response: DoctorResponse? = null,
    val createdAt: Date? = null,
)

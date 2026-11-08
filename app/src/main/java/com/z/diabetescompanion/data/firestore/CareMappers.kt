package com.z.diabetescompanion.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.z.diabetescompanion.data.model.AiDraft
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertEvent
import com.z.diabetescompanion.data.model.AlertSeverity
import com.z.diabetescompanion.data.model.AlertStage
import com.z.diabetescompanion.data.model.Conversation
import com.z.diabetescompanion.data.model.DoctorResponse
import com.z.diabetescompanion.data.model.InsulinRequest
import com.z.diabetescompanion.data.model.Message
import com.z.diabetescompanion.data.model.PatientResponse
import com.z.diabetescompanion.data.model.RequestContext
import com.z.diabetescompanion.data.model.RequestStatus
import com.z.diabetescompanion.data.model.UserRole
import java.util.Date

object CareSchema {
    const val ALERTS = "alerts"
    const val CONVERSATIONS = "conversations"
    const val MESSAGES = "messages"
    const val REQUESTS = "insulinRequests"
    /** Doctor-only subcollection: rules deny patients, so AI drafts never reach them. */
    const val PRIVATE = "private"
    const val DRAFT_DOC = "draft"
}

private fun DocumentSnapshot.date(field: String): Date? = getTimestamp(field)?.toDate()
private fun DocumentSnapshot.str(field: String): String = getString(field).orEmpty()

// --- Alerts ----------------------------------------------------------------

private fun AlertEvent.toMap(): Map<String, Any?> = mapOf(
    "type" to type,
    "label" to label,
    "at" to Timestamp(at),
    "actorName" to actorName,
    "note" to note,
)

@Suppress("UNCHECKED_CAST")
private fun Any?.toAlertEvents(): List<AlertEvent> =
    (this as? List<Map<String, Any?>>).orEmpty().mapNotNull { raw ->
        val at = (raw["at"] as? Timestamp)?.toDate() ?: return@mapNotNull null
        AlertEvent(
            type = raw["type"] as? String ?: "",
            label = raw["label"] as? String ?: "",
            at = at,
            actorName = raw["actorName"] as? String ?: "",
            note = raw["note"] as? String ?: "",
        )
    }

fun Alert.toMap(isNew: Boolean): Map<String, Any?> = buildMap {
    put("patientUid", patientUid)
    put("patientName", patientName)
    put("valueMgdl", valueMgdl)
    put("recordedAt", Timestamp(recordedAt))
    put("severity", severity.storageKey)
    put("stage", stage.storageKey)
    put("statusLabel", statusLabel)
    put("patientResponse", patientResponse?.storageKey)
    put("respondedAt", respondedAt?.let { Timestamp(it) })
    put("caregiverNotifiedAt", caregiverNotifiedAt?.let { Timestamp(it) })
    put("doctorNotifiedAt", doctorNotifiedAt?.let { Timestamp(it) })
    put("resolvedByUid", resolvedByUid)
    put("resolvedByName", resolvedByName)
    put("resolvedByRole", resolvedByRole?.storageKey)
    put("resolutionNote", resolutionNote)
    put("resolvedAt", resolvedAt?.let { Timestamp(it) })
    put("events", events.map { it.toMap() })
    if (isNew) put("createdAt", FieldValue.serverTimestamp())
}

fun DocumentSnapshot.toAlert(): Alert? {
    if (!exists()) return null
    val recordedAt = date("recordedAt") ?: return null
    return Alert(
        id = id,
        patientUid = str("patientUid"),
        patientName = str("patientName"),
        valueMgdl = getDouble("valueMgdl") ?: return null,
        recordedAt = recordedAt,
        severity = AlertSeverity.fromKey(getString("severity")),
        stage = AlertStage.fromKey(getString("stage")),
        statusLabel = str("statusLabel"),
        patientResponse = PatientResponse.fromKey(getString("patientResponse")),
        respondedAt = date("respondedAt"),
        caregiverNotifiedAt = date("caregiverNotifiedAt"),
        doctorNotifiedAt = date("doctorNotifiedAt"),
        resolvedByUid = str("resolvedByUid"),
        resolvedByName = str("resolvedByName"),
        resolvedByRole = UserRole.fromKey(getString("resolvedByRole")),
        resolutionNote = str("resolutionNote"),
        resolvedAt = date("resolvedAt"),
        events = get("events").toAlertEvents(),
        createdAt = date("createdAt"),
    )
}

// --- Conversations and messages --------------------------------------------

fun Conversation.toMap(): Map<String, Any?> = mapOf(
    "patientUid" to patientUid,
    "patientName" to patientName,
    "doctorUid" to doctorUid,
    "doctorName" to doctorName,
    "memberUid" to memberUid,
    "memberName" to memberName,
    "memberRole" to memberRole.storageKey,
    "participants" to listOf(memberUid, doctorUid).distinct(),
)

fun DocumentSnapshot.toConversation(): Conversation? {
    if (!exists()) return null
    @Suppress("UNCHECKED_CAST")
    val participants = (get("participants") as? List<String>).orEmpty()
    return Conversation(
        id = id,
        patientUid = str("patientUid"),
        patientName = str("patientName"),
        doctorUid = str("doctorUid"),
        doctorName = str("doctorName"),
        memberUid = str("memberUid"),
        memberName = str("memberName"),
        memberRole = UserRole.fromKey(getString("memberRole")) ?: return null,
        participants = participants,
        lastMessage = str("lastMessage"),
        lastMessageAt = date("lastMessageAt"),
        lastSenderUid = str("lastSenderUid"),
    )
}

fun Message.toMap(): Map<String, Any?> = mapOf(
    "senderUid" to senderUid,
    "senderName" to senderName,
    "senderRole" to senderRole.storageKey,
    "text" to text,
    "sentAt" to Timestamp(sentAt),
)

fun DocumentSnapshot.toMessage(): Message? {
    val sentAt = date("sentAt") ?: return null
    return Message(
        id = id,
        senderUid = str("senderUid"),
        senderName = str("senderName"),
        senderRole = UserRole.fromKey(getString("senderRole")) ?: UserRole.Patient,
        text = str("text"),
        sentAt = sentAt,
    )
}

// --- Insulin requests -------------------------------------------------------

private fun RequestContext.toMap(): Map<String, Any?> = mapOf(
    "age" to age,
    "diabetesType" to diabetesType,
    "carePlan" to carePlan,
    "relevantHistory" to relevantHistory,
    "latestValueMgdl" to latestValueMgdl,
    "latestReadingAt" to latestReadingAt?.let { Timestamp(it) },
    "recentReadings" to recentReadings,
    "recentDoses" to recentDoses,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>?.toRequestContext(): RequestContext {
    val raw = this ?: return RequestContext()
    return RequestContext(
        age = (raw["age"] as? Number)?.toInt(),
        diabetesType = raw["diabetesType"] as? String ?: "",
        carePlan = raw["carePlan"] as? String ?: "",
        relevantHistory = raw["relevantHistory"] as? String ?: "",
        latestValueMgdl = (raw["latestValueMgdl"] as? Number)?.toDouble(),
        latestReadingAt = (raw["latestReadingAt"] as? Timestamp)?.toDate(),
        recentReadings = (raw["recentReadings"] as? List<String>).orEmpty(),
        recentDoses = (raw["recentDoses"] as? List<String>).orEmpty(),
    )
}

fun InsulinRequest.toMap(isNew: Boolean): Map<String, Any?> = buildMap {
    put("patientUid", patientUid)
    put("patientName", patientName)
    put("doctorUid", doctorUid)
    put("doctorName", doctorName)
    put("question", question)
    put("context", context.toMap())
    put("status", status.storageKey)
    put("response", response?.let {
        mapOf(
            "text" to it.text,
            "doctorUid" to it.doctorUid,
            "doctorName" to it.doctorName,
            "usedDraft" to it.usedDraft,
            "submittedAt" to FieldValue.serverTimestamp(),
        )
    })
    if (isNew) put("createdAt", FieldValue.serverTimestamp())
}

@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toInsulinRequest(): InsulinRequest? {
    if (!exists()) return null
    val responseMap = get("response") as? Map<String, Any?>
    return InsulinRequest(
        id = id,
        patientUid = str("patientUid"),
        patientName = str("patientName"),
        doctorUid = str("doctorUid"),
        doctorName = str("doctorName"),
        question = str("question"),
        context = (get("context") as? Map<String, Any?>).toRequestContext(),
        status = RequestStatus.fromKey(getString("status")),
        response = responseMap?.let {
            DoctorResponse(
                text = it["text"] as? String ?: "",
                doctorUid = it["doctorUid"] as? String ?: "",
                doctorName = it["doctorName"] as? String ?: "",
                usedDraft = it["usedDraft"] as? Boolean ?: false,
                submittedAt = (it["submittedAt"] as? Timestamp)?.toDate(),
            )
        },
        createdAt = date("createdAt"),
    )
}

fun AiDraft.toMap(): Map<String, Any?> = mapOf(
    "text" to text,
    "model" to model,
    "failed" to failed,
    "failureMessage" to failureMessage,
    "generatedAt" to FieldValue.serverTimestamp(),
)

fun DocumentSnapshot.toAiDraft(): AiDraft? {
    if (!exists()) return null
    return AiDraft(
        text = str("text"),
        model = str("model"),
        generatedAt = date("generatedAt"),
        failed = getBoolean("failed") ?: false,
        failureMessage = str("failureMessage"),
    )
}

package com.z.diabetescompanion.data.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.z.diabetescompanion.data.firestore.CareSchema.ALERTS
import com.z.diabetescompanion.data.firestore.CareSchema.CONVERSATIONS
import com.z.diabetescompanion.data.firestore.CareSchema.DRAFT_DOC
import com.z.diabetescompanion.data.firestore.CareSchema.MESSAGES
import com.z.diabetescompanion.data.firestore.CareSchema.PRIVATE
import com.z.diabetescompanion.data.firestore.CareSchema.REQUESTS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.USERS
import com.z.diabetescompanion.data.model.AiDraft
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.Conversation
import com.z.diabetescompanion.data.model.InsulinRequest
import com.z.diabetescompanion.data.model.Message
import com.z.diabetescompanion.data.repository.AlertRepository
import com.z.diabetescompanion.data.repository.InsulinRequestRepository
import com.z.diabetescompanion.data.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Alerts live under the patient they belong to, which keeps the security rules identical
 * to the ones already protecting readings: the patient owns them, and anyone holding an
 * active consent link may read and act on them.
 */
class FirestoreAlertRepository(private val db: FirebaseFirestore) : AlertRepository {

    private fun col(patientUid: String) =
        db.collection(USERS).document(patientUid).collection(ALERTS)

    override fun alertsForPatient(patientUid: String, limit: Long): Flow<List<Alert>> =
        col(patientUid)
            .orderBy("recordedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toAlert() } }

    override fun alertsForPatients(patientUids: List<String>, limit: Long): Flow<List<Alert>> {
        if (patientUids.isEmpty()) return flowOf(emptyList())
        // One listener per consented patient, merged and re-sorted. Fine at prototype scale,
        // and it keeps every read inside the per-patient rules rather than a collection group.
        return combine(patientUids.map { alertsForPatient(it, limit) }) { lists ->
            lists.toList().flatten().sortedByDescending { it.recordedAt }
        }
    }

    override fun alert(patientUid: String, alertId: String): Flow<Alert?> =
        col(patientUid).document(alertId).snapshotFlow().map { it.toAlert() }

    override suspend fun getAlert(patientUid: String, alertId: String): Alert? =
        col(patientUid).document(alertId).get().await().toAlert()

    override suspend fun save(alert: Alert, isNew: Boolean) {
        col(alert.patientUid).document(alert.id)
            .set(alert.toMap(isNew), SetOptions.merge())
            .awaitOrTimeout()
    }
}

class FirestoreMessageRepository(private val db: FirebaseFirestore) : MessageRepository {

    private val col get() = db.collection(CONVERSATIONS)

    override fun conversationsFor(uid: String): Flow<List<Conversation>> =
        col.whereArrayContains("participants", uid)
            .snapshotFlow()
            .map { snap ->
                snap.documents.mapNotNull { it.toConversation() }
                    .sortedByDescending { it.lastMessageAt ?: java.util.Date(0) }
            }

    override fun conversation(conversationId: String): Flow<Conversation?> =
        col.document(conversationId).snapshotFlow().map { it.toConversation() }

    override fun messages(conversationId: String, limit: Long): Flow<List<Message>> =
        col.document(conversationId).collection(MESSAGES)
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toMessage() } }

    override suspend fun ensureConversation(conversation: Conversation): String {
        // merge() so re-opening a chat never clobbers the running last-message summary.
        col.document(conversation.id).set(conversation.toMap(), SetOptions.merge()).awaitOrTimeout()
        return conversation.id
    }

    override suspend fun send(conversationId: String, message: Message) {
        val doc = col.document(conversationId)
        doc.collection(MESSAGES).document(message.id).set(message.toMap()).awaitOrTimeout()
        doc.set(
            mapOf(
                "lastMessage" to message.text.take(140),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderUid" to message.senderUid,
            ),
            SetOptions.merge(),
        ).awaitOrTimeout()
    }
}

class FirestoreInsulinRequestRepository(private val db: FirebaseFirestore) : InsulinRequestRepository {

    private val col get() = db.collection(REQUESTS)

    override fun requestsForPatient(patientUid: String): Flow<List<InsulinRequest>> =
        col.whereEqualTo("patientUid", patientUid)
            .snapshotFlow()
            .map { snap ->
                snap.documents.mapNotNull { it.toInsulinRequest() }
                    .sortedByDescending { it.createdAt ?: java.util.Date(0) }
            }

    override fun requestsForDoctor(doctorUid: String): Flow<List<InsulinRequest>> =
        col.whereEqualTo("doctorUid", doctorUid)
            .snapshotFlow()
            .map { snap ->
                snap.documents.mapNotNull { it.toInsulinRequest() }
                    .sortedByDescending { it.createdAt ?: java.util.Date(0) }
            }

    override fun request(requestId: String): Flow<InsulinRequest?> =
        col.document(requestId).snapshotFlow().map { it.toInsulinRequest() }

    override suspend fun getRequest(requestId: String): InsulinRequest? =
        col.document(requestId).get().await().toInsulinRequest()

    override suspend fun create(request: InsulinRequest) {
        col.document(request.id).set(request.toMap(isNew = true)).awaitOrTimeout()
    }

    override suspend fun submitResponse(requestId: String, request: InsulinRequest) {
        col.document(requestId).set(request.toMap(isNew = false), SetOptions.merge()).awaitOrTimeout()
    }

    // --- Doctor-only draft --------------------------------------------------

    private fun draftDoc(requestId: String) =
        col.document(requestId).collection(PRIVATE).document(DRAFT_DOC)

    override fun draft(requestId: String): Flow<AiDraft?> =
        draftDoc(requestId).snapshotFlow().map { it.toAiDraft() }

    override suspend fun saveDraft(requestId: String, draft: AiDraft) {
        draftDoc(requestId).set(draft.toMap()).awaitOrTimeout()
    }
}

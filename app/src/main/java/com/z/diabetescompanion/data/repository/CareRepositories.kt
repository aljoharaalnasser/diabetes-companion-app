package com.z.diabetescompanion.data.repository

import com.z.diabetescompanion.data.model.AiDraft
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.Conversation
import com.z.diabetescompanion.data.model.InsulinRequest
import com.z.diabetescompanion.data.model.Message
import kotlinx.coroutines.flow.Flow

/** Alerts and their escalation state (FR-08, FR-09, FR-10). */
interface AlertRepository {
    fun alertsForPatient(patientUid: String, limit: Long = 50): Flow<List<Alert>>
    /** Open alerts across every patient who currently shares with this care-team member. */
    fun alertsForPatients(patientUids: List<String>, limit: Long = 100): Flow<List<Alert>>
    fun alert(patientUid: String, alertId: String): Flow<Alert?>
    suspend fun getAlert(patientUid: String, alertId: String): Alert?
    suspend fun save(alert: Alert, isNew: Boolean)
}

/** Patient-doctor and caregiver-doctor conversations (FR-15). */
interface MessageRepository {
    fun conversationsFor(uid: String): Flow<List<Conversation>>
    fun conversation(conversationId: String): Flow<Conversation?>
    fun messages(conversationId: String, limit: Long = 200): Flow<List<Message>>
    /** Creates the conversation if it does not exist yet, then returns its id. */
    suspend fun ensureConversation(conversation: Conversation): String
    suspend fun send(conversationId: String, message: Message)
}

/** Insulin-advice requests and the doctor-only AI draft (FR-17, FR-18, FR-19). */
interface InsulinRequestRepository {
    fun requestsForPatient(patientUid: String): Flow<List<InsulinRequest>>
    fun requestsForDoctor(doctorUid: String): Flow<List<InsulinRequest>>
    fun request(requestId: String): Flow<InsulinRequest?>
    suspend fun getRequest(requestId: String): InsulinRequest?
    suspend fun create(request: InsulinRequest)
    suspend fun submitResponse(requestId: String, request: InsulinRequest)

    /** Doctor-only. Rules deny every other role, so a patient can never read a draft. */
    fun draft(requestId: String): Flow<AiDraft?>
    suspend fun saveDraft(requestId: String, draft: AiDraft)
}

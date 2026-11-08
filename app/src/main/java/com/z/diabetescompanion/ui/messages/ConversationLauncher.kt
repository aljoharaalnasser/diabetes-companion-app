package com.z.diabetescompanion.ui.messages

import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.Conversation
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.MessageRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** Why a conversation could not be opened, phrased for the person who tapped the button. */
sealed interface ConversationResult {
    data class Opened(val conversationId: String) : ConversationResult
    data class Unavailable(val message: String) : ConversationResult
}

/**
 * Resolves which doctor a message should go to, then creates the conversation if needed.
 *
 * Every care-team conversation is anchored to one patient, so the doctor always knows whose
 * records are being discussed (FR-15). A patient messages their own assigned doctor; a
 * caregiver messages the doctor assigned to the patient they support.
 */
class ConversationLauncher(
    private val careLinkRepository: CareLinkRepository,
    private val messageRepository: MessageRepository,
) {

    suspend fun openForPatient(patient: UserProfile): ConversationResult {
        val doctor = activeDoctorLink(patient.uid)
            ?: return ConversationResult.Unavailable(
                "You have not shared your records with a doctor yet. Open More, then Care-team sharing, to choose one."
            )
        return open(
            Conversation(
                id = Conversation.idFor(patient.uid, patient.uid, doctor.memberUid),
                patientUid = patient.uid,
                patientName = patient.displayName,
                doctorUid = doctor.memberUid,
                doctorName = doctor.memberName,
                memberUid = patient.uid,
                memberName = patient.displayName,
                memberRole = UserRole.Patient,
            )
        )
    }

    suspend fun openForCaregiver(
        caregiver: UserProfile,
        patientUid: String,
        patientName: String,
    ): ConversationResult {
        val doctor = activeDoctorLink(patientUid)
            ?: return ConversationResult.Unavailable(
                "$patientName has not shared their records with a doctor yet, so there is nobody to contact."
            )
        return open(
            Conversation(
                id = Conversation.idFor(patientUid, caregiver.uid, doctor.memberUid),
                patientUid = patientUid,
                patientName = patientName,
                doctorUid = doctor.memberUid,
                doctorName = doctor.memberName,
                memberUid = caregiver.uid,
                memberName = caregiver.displayName,
                memberRole = UserRole.Caregiver,
            )
        )
    }

    /** Used by a doctor opening a chat from an alert or a patient record. */
    suspend fun openForDoctor(
        doctor: UserProfile,
        patientUid: String,
        patientName: String,
    ): ConversationResult = open(
        Conversation(
            id = Conversation.idFor(patientUid, patientUid, doctor.uid),
            patientUid = patientUid,
            patientName = patientName,
            doctorUid = doctor.uid,
            doctorName = doctor.displayName,
            memberUid = patientUid,
            memberName = patientName,
            memberRole = UserRole.Patient,
        )
    )

    private suspend fun activeDoctorLink(patientUid: String): CareLink? =
        careLinkRepository.linksForPatient(patientUid)
            .catch { emit(emptyList()) }
            .first()
            .firstOrNull { it.memberRole == UserRole.Doctor && it.status == CareLinkStatus.Active }

    private suspend fun open(conversation: Conversation): ConversationResult = try {
        ConversationResult.Opened(messageRepository.ensureConversation(conversation))
    } catch (e: Exception) {
        ConversationResult.Unavailable(e.toFirestoreMessage())
    }
}

package com.z.diabetescompanion.data.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.diabetescompanion.data.firestore.FirestoreSchema.CARE_LINKS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.DELETION_REQUESTS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.MEDICATIONS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.READINGS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.REMINDERS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.SETTINGS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.THRESHOLDS_DOC
import com.z.diabetescompanion.data.firestore.FirestoreSchema.USERS
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.Reminder
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.CareLinkRepository
import com.z.diabetescompanion.data.repository.GlucoseRepository
import com.z.diabetescompanion.data.repository.MedicationRepository
import com.z.diabetescompanion.data.repository.ReminderRepository
import com.z.diabetescompanion.data.repository.ThresholdRepository
import com.z.diabetescompanion.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private fun FirebaseFirestore.user(uid: String) = collection(USERS).document(uid)

class FirestoreUserRepository(private val db: FirebaseFirestore) : UserRepository {

    override fun profile(uid: String): Flow<UserProfile?> =
        db.user(uid).snapshotFlow().map { it.toUserProfile() }

    override suspend fun getProfile(uid: String): UserProfile? =
        db.user(uid).get().await().toUserProfile()

    override suspend fun createProfile(profile: UserProfile) {
        db.user(profile.uid).set(profile.toMap(isNew = true)).awaitOrTimeout()
    }

    override suspend fun updateProfile(profile: UserProfile) {
        db.user(profile.uid).set(profile.toMap(isNew = false), com.google.firebase.firestore.SetOptions.merge()).awaitOrTimeout()
    }

    override suspend fun listByRole(role: UserRole): List<UserProfile> =
        db.collection(USERS)
            .whereEqualTo("role", role.storageKey)
            .limit(100)
            .get().await()
            .documents.mapNotNull { it.toUserProfile() }
            .sortedBy { it.displayName.lowercase() }
}

class FirestoreGlucoseRepository(private val db: FirebaseFirestore) : GlucoseRepository {

    private fun col(uid: String) = db.user(uid).collection(READINGS)

    override fun readings(patientUid: String, limit: Long): Flow<List<GlucoseReading>> =
        col(patientUid)
            .orderBy("recordedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toGlucoseReading() } }

    override fun latest(patientUid: String): Flow<GlucoseReading?> =
        readings(patientUid, limit = 1).map { it.firstOrNull() }

    override suspend fun save(patientUid: String, reading: GlucoseReading) {
        // Deterministic id (timestamp + value) makes an accidental double-tap idempotent (NFR-04).
        col(patientUid).document(reading.id).set(reading.toMap()).awaitOrTimeout()
    }

    override suspend fun delete(patientUid: String, readingId: String) {
        col(patientUid).document(readingId).delete().awaitOrTimeout()
    }
}

class FirestoreMedicationRepository(private val db: FirebaseFirestore) : MedicationRepository {

    private fun col(uid: String) = db.user(uid).collection(MEDICATIONS)

    override fun logs(patientUid: String, limit: Long): Flow<List<MedicationLog>> =
        col(patientUid)
            .orderBy("takenAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toMedicationLog() } }

    override suspend fun save(patientUid: String, log: MedicationLog) {
        col(patientUid).document(log.id).set(log.toMap()).awaitOrTimeout()
    }

    override suspend fun delete(patientUid: String, logId: String) {
        col(patientUid).document(logId).delete().awaitOrTimeout()
    }
}

class FirestoreReminderRepository(private val db: FirebaseFirestore) : ReminderRepository {

    private fun col(uid: String) = db.user(uid).collection(REMINDERS)

    override fun reminders(patientUid: String): Flow<List<Reminder>> =
        // Sorted client-side: a two-field orderBy would need a composite index per deployment.
        col(patientUid)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toReminder() }.sortedWith(compareBy({ it.hour }, { it.minute })) }

    override suspend fun listOnce(patientUid: String): List<Reminder> =
        col(patientUid).get().await().documents.mapNotNull { it.toReminder() }

    override suspend fun save(patientUid: String, reminder: Reminder, isNew: Boolean) {
        col(patientUid).document(reminder.id).set(reminder.toMap(isNew), com.google.firebase.firestore.SetOptions.merge()).awaitOrTimeout()
    }

    override suspend fun setEnabled(patientUid: String, reminderId: String, enabled: Boolean) {
        col(patientUid).document(reminderId).update("enabled", enabled).awaitOrTimeout()
    }

    override suspend fun delete(patientUid: String, reminderId: String) {
        col(patientUid).document(reminderId).delete().awaitOrTimeout()
    }
}

class FirestoreThresholdRepository(private val db: FirebaseFirestore) : ThresholdRepository {

    private fun doc(uid: String) = db.user(uid).collection(SETTINGS).document(THRESHOLDS_DOC)

    override fun thresholds(patientUid: String): Flow<Thresholds?> =
        doc(patientUid).snapshotFlow().map { it.toThresholds() }

    override suspend fun save(patientUid: String, thresholds: Thresholds) {
        doc(patientUid).set(thresholds.toMap()).awaitOrTimeout()
    }
}

class FirestoreCareLinkRepository(private val db: FirebaseFirestore) : CareLinkRepository {

    private val col get() = db.collection(CARE_LINKS)

    override fun linksForPatient(patientUid: String): Flow<List<CareLink>> =
        col.whereEqualTo("patientUid", patientUid)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toCareLink() } }

    override fun activeLinksForMember(memberUid: String): Flow<List<CareLink>> =
        col.whereEqualTo("memberUid", memberUid)
            .whereEqualTo("status", CareLinkStatus.Active.storageKey)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toCareLink() } }

    override suspend fun grant(link: CareLink) {
        col.document(link.id).set(link.toMap()).awaitOrTimeout()
    }

    override suspend fun revoke(linkId: String) {
        col.document(linkId).update(
            mapOf(
                "status" to CareLinkStatus.Revoked.storageKey,
                "revokedAt" to FieldValue.serverTimestamp(),
            )
        ).awaitOrTimeout()
    }

    override fun deletionRequest(uid: String): Flow<DeletionRequest?> =
        db.collection(DELETION_REQUESTS).document(uid).snapshotFlow().map { it.toDeletionRequest() }

    override suspend fun requestDeletion(uid: String) {
        db.collection(DELETION_REQUESTS).document(uid).set(
            mapOf(
                "uid" to uid,
                "requestedAt" to FieldValue.serverTimestamp(),
                "status" to "pending",
            )
        ).awaitOrTimeout()
    }
}

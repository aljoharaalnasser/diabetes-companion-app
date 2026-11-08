package com.z.diabetescompanion.data.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.diabetescompanion.data.firestore.FirestoreSchema.MEDICATIONS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.USERS
import com.z.diabetescompanion.data.firestore.MealInsulinSchema.DOSE_SUGGESTIONS
import com.z.diabetescompanion.data.firestore.MealInsulinSchema.ICR_SETTINGS
import com.z.diabetescompanion.data.model.DoseSuggestion
import com.z.diabetescompanion.data.model.DoseSuggestionStatus
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.repository.DoseSuggestionRepository
import com.z.diabetescompanion.data.repository.IcrRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private fun FirebaseFirestore.user(uid: String) = collection(USERS).document(uid)

class FirestoreIcrRepository(private val db: FirebaseFirestore) : IcrRepository {

    private fun col(uid: String) = db.user(uid).collection(ICR_SETTINGS)

    override fun versions(patientUid: String): Flow<List<IcrSetting>> =
        col(patientUid)
            .orderBy("version", Query.Direction.DESCENDING)
            .limit(50)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toIcrSetting() } }

    override suspend fun listOnce(patientUid: String): List<IcrSetting> =
        col(patientUid).orderBy("version", Query.Direction.DESCENDING).limit(50).get().await()
            .documents.mapNotNull { it.toIcrSetting() }

    override suspend fun create(setting: IcrSetting) {
        // set() on a fresh id: rules allow create only, so an existing version can never be overwritten.
        col(setting.patientId).document(setting.icrId).set(setting.toMap()).awaitOrTimeout()
    }
}

class FirestoreDoseSuggestionRepository(private val db: FirebaseFirestore) : DoseSuggestionRepository {

    private fun col(uid: String) = db.user(uid).collection(DOSE_SUGGESTIONS)

    override fun suggestions(patientUid: String, limit: Long): Flow<List<DoseSuggestion>> =
        col(patientUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toDoseSuggestion() } }

    override suspend fun create(suggestion: DoseSuggestion) {
        col(suggestion.patientId).document(suggestion.suggestionId).set(suggestion.toMap()).awaitOrTimeout()
    }

    override suspend fun recordTaken(patientUid: String, suggestionId: String, takenUnits: Double, log: MedicationLog) {
        val batch = db.batch()
        batch.set(db.user(patientUid).collection(MEDICATIONS).document(log.id), log.toMap())
        batch.update(
            col(patientUid).document(suggestionId),
            mapOf(
                "status" to DoseSuggestionStatus.Taken.storageKey,
                "takenUnits" to takenUnits,
                "medicationLogId" to log.id,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().awaitOrTimeout()
    }

    override suspend fun markSkipped(patientUid: String, suggestionId: String) {
        col(patientUid).document(suggestionId).update(
            mapOf(
                "status" to DoseSuggestionStatus.Skipped.storageKey,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).awaitOrTimeout()
    }
}

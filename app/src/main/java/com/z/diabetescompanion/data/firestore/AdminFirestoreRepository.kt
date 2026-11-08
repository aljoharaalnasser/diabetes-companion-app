package com.z.diabetescompanion.data.firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.z.diabetescompanion.data.firestore.CareSchema.ALERTS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.CARE_LINKS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.DELETION_REQUESTS
import com.z.diabetescompanion.data.firestore.FirestoreSchema.USERS
import com.z.diabetescompanion.data.model.AccountStatus
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.AdminRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Platform-wide queries for the admin dashboard.
 *
 * Alerts use a collection-group query because they live under the patient who owns them.
 * The other care-team roles read them per patient through their consent link; the admin has
 * no consent link to anybody, so a single rule on `{path=**}/alerts` grants this one role a
 * cross-patient read and nothing else.
 */
class FirestoreAdminRepository(private val db: FirebaseFirestore) : AdminRepository {

    override fun allUsers(limit: Long): Flow<List<UserProfile>> =
        db.collection(USERS)
            .limit(limit)
            .snapshotFlow()
            .map { snap ->
                // Sorted client-side: displayName has no index and the list is small.
                snap.documents.mapNotNull { it.toUserProfile() }
                    .sortedBy { it.displayName.lowercase() }
            }

    override fun allCareLinks(limit: Long): Flow<List<CareLink>> =
        db.collection(CARE_LINKS)
            .limit(limit)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toCareLink() } }

    override fun allAlerts(limit: Long): Flow<List<Alert>> =
        db.collectionGroup(ALERTS)
            .orderBy("recordedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snap -> snap.documents.mapNotNull { it.toAlert() } }

    override fun allDeletionRequests(limit: Long): Flow<List<DeletionRequest>> =
        db.collection(DELETION_REQUESTS)
            .limit(limit)
            .snapshotFlow()
            .map { snap ->
                snap.documents.mapNotNull { it.toDeletionRequest() }
                    .sortedByDescending { it.requestedAt ?: java.util.Date(0) }
            }

    override suspend fun setRole(uid: String, role: UserRole) {
        db.collection(USERS).document(uid).set(
            mapOf(
                "role" to role.storageKey,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).awaitOrTimeout()
    }

    override suspend fun setAccountStatus(uid: String, status: AccountStatus) {
        db.collection(USERS).document(uid).set(
            mapOf(
                "accountStatus" to status.storageKey,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).awaitOrTimeout()
    }

    override suspend fun completeDeletionRequest(uid: String, adminName: String) {
        db.collection(DELETION_REQUESTS).document(uid).set(
            mapOf(
                "uid" to uid,
                "status" to DeletionRequest.COMPLETED,
                "completedAt" to FieldValue.serverTimestamp(),
                "completedByName" to adminName,
            ),
            SetOptions.merge(),
        ).awaitOrTimeout()
    }
}

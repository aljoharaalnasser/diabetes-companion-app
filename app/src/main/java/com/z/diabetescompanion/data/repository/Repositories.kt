package com.z.diabetescompanion.data.repository

import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.Reminder
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts for Sprint 1 records. All flows are live (Firestore snapshots),
 * all writes are suspend functions that throw on failure so ViewModels map errors once.
 */

interface UserRepository {
    fun profile(uid: String): Flow<UserProfile?>
    suspend fun getProfile(uid: String): UserProfile?
    suspend fun createProfile(profile: UserProfile)
    suspend fun updateProfile(profile: UserProfile)
    /** Directory lookup used by the consent screen to pick a doctor or caregiver. */
    suspend fun listByRole(role: UserRole): List<UserProfile>
}

interface GlucoseRepository {
    fun readings(patientUid: String, limit: Long = 500): Flow<List<GlucoseReading>>
    fun latest(patientUid: String): Flow<GlucoseReading?>
    suspend fun save(patientUid: String, reading: GlucoseReading)
    suspend fun delete(patientUid: String, readingId: String)
}

interface MedicationRepository {
    fun logs(patientUid: String, limit: Long = 300): Flow<List<MedicationLog>>
    suspend fun save(patientUid: String, log: MedicationLog)
    suspend fun delete(patientUid: String, logId: String)
}

interface ReminderRepository {
    fun reminders(patientUid: String): Flow<List<Reminder>>
    suspend fun listOnce(patientUid: String): List<Reminder>
    suspend fun save(patientUid: String, reminder: Reminder, isNew: Boolean)
    suspend fun setEnabled(patientUid: String, reminderId: String, enabled: Boolean)
    suspend fun delete(patientUid: String, reminderId: String)
}

interface ThresholdRepository {
    fun thresholds(patientUid: String): Flow<Thresholds?>
    suspend fun save(patientUid: String, thresholds: Thresholds)
}

interface CareLinkRepository {
    /** Links where the given user is the patient. */
    fun linksForPatient(patientUid: String): Flow<List<CareLink>>
    /** Active links where the given user is the doctor or caregiver. */
    fun activeLinksForMember(memberUid: String): Flow<List<CareLink>>
    suspend fun grant(link: CareLink)
    suspend fun revoke(linkId: String)
    fun deletionRequest(uid: String): Flow<DeletionRequest?>
    suspend fun requestDeletion(uid: String)
}

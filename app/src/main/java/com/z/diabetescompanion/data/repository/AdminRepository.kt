package com.z.diabetescompanion.data.repository

import com.z.diabetescompanion.data.model.AccountStatus
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Platform-wide reads and the account operations behind the admin dashboard (FR-14, S3-01).
 *
 * Every one of these is denied to the other three roles by the Security Rules, so an
 * administrator is the only signed-in user for whom these flows emit anything (NFR-07).
 * The reads cover accounts, consent links and alert status only; patient readings,
 * medication, conversations and insulin requests are deliberately not reachable here.
 */
interface AdminRepository {
    fun allUsers(limit: Long = 300): Flow<List<UserProfile>>
    fun allCareLinks(limit: Long = 500): Flow<List<CareLink>>

    /**
     * Every patient's alerts in one collection-group query, which the admin-only rule on
     * `{path=**}/alerts` permits. No index file is needed: Firestore's automatic
     * single-field indexes already cover collection-group scope, and declaring one
     * explicitly is rejected as unnecessary.
     */
    fun allAlerts(limit: Long = 300): Flow<List<Alert>>

    fun allDeletionRequests(limit: Long = 100): Flow<List<DeletionRequest>>

    /**
     * The one write that can hand out privilege, which is why it is admin-only in the rules
     * and why self-registration can never create an admin in the first place.
     */
    suspend fun setRole(uid: String, role: UserRole)

    suspend fun setAccountStatus(uid: String, status: AccountStatus)

    /** Records that a deletion request was carried out, with who closed it (NFR-10). */
    suspend fun completeDeletionRequest(uid: String, adminName: String)
}

package com.z.diabetescompanion.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.AccountStatus
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.AdminRepository
import com.z.diabetescompanion.domain.AdminMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One account plus the consent links that decide whether it counts as an active case. */
data class ManagedAccount(
    val profile: UserProfile,
    val activeLinks: List<CareLink>,
) {
    val hasCareTeam: Boolean get() = activeLinks.isNotEmpty()

    val careTeamSummary: String
        get() = if (activeLinks.isEmpty()) "No care team" else activeLinks
            .sortedBy { it.memberRole.ordinal }
            .joinToString(" · ") { "${it.memberRole.label}: ${it.memberName}" }
}

data class AdminUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val metrics: AdminMetrics = AdminMetrics.EMPTY,
    val accounts: List<ManagedAccount> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val deletionRequests: List<DeletionRequest> = emptyList(),
    /**
     * Set when the cross-patient alert query itself failed. Kept separate from [error] so the
     * rest of the dashboard still works, and shown rather than swallowed: an empty list and a
     * failed query look identical to the reader otherwise, and only one of them means
     * "nothing has happened" (NFR-15).
     */
    val alertsError: String? = null,
)

/**
 * Backs all four admin tabs from one set of live queries (FR-14, S3-01).
 *
 * The figures come from [AdminMetrics], which is a pure function of these same lists, so
 * what the overview counts and what the lists show can never disagree.
 */
class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val adminName: String,
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    /** Result of the last account operation, shown as a snackbar. */
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _pendingUid = MutableStateFlow<String?>(null)
    /** The account currently being written, so its row can disable its own controls. */
    val pendingUid: StateFlow<String?> = _pendingUid.asStateFlow()

    val uiState: StateFlow<AdminUiState> = combine(
        adminRepository.allUsers(),
        adminRepository.allCareLinks(),
        // The alert query can fail on its own (it is the only collection-group query in the
        // app), so it degrades to an empty list that carries its reason with it.
        adminRepository.allAlerts()
            .map { it to null as String? }
            .catch { emit(emptyList<Alert>() to it.toFirestoreMessage()) },
        adminRepository.allDeletionRequests().catch { emit(emptyList()) },
    ) { users, links, alertsResult, deletions ->
        val (alerts, alertsError) = alertsResult
        val activeByPatient = links
            .filter { it.status == CareLinkStatus.Active }
            .groupBy { it.patientUid }
        AdminUiState(
            isLoading = false,
            metrics = AdminMetrics.from(users, links, alerts, deletions),
            accounts = users.map { ManagedAccount(it, activeByPatient[it.uid].orEmpty()) },
            alerts = alerts,
            deletionRequests = deletions,
            alertsError = alertsError,
        )
    }
        .catch { emit(AdminUiState(isLoading = false, error = it.toFirestoreMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setRole(account: ManagedAccount, role: UserRole) {
        if (role == account.profile.role) return
        runAccountOperation(
            account.profile.uid,
            success = "${account.profile.displayName} is now a ${role.label}.",
        ) {
            adminRepository.setRole(account.profile.uid, role)
        }
    }

    fun setStatus(account: ManagedAccount, status: AccountStatus) {
        if (status == account.profile.accountStatus) return
        val verb = if (status == AccountStatus.Suspended) "suspended" else "reactivated"
        runAccountOperation(
            account.profile.uid,
            success = "${account.profile.displayName} was $verb.",
        ) {
            adminRepository.setAccountStatus(account.profile.uid, status)
        }
    }

    fun completeDeletionRequest(request: DeletionRequest) {
        runAccountOperation(request.uid, success = "Deletion request marked as completed.") {
            adminRepository.completeDeletionRequest(request.uid, adminName)
        }
    }

    private fun runAccountOperation(uid: String, success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _pendingUid.value = uid
            try {
                block()
                _actionMessage.value = success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = e.toFirestoreMessage()
            } finally {
                _pendingUid.update { if (it == uid) null else it }
            }
        }
    }
}

/** Filters for the account list. */
enum class AccountFilter(val label: String) {
    All("All"),
    Patients("Patients"),
    Doctors("Doctors"),
    Caregivers("Caregivers"),
    Admins("Admins"),
    Suspended("Suspended");

    fun matches(account: ManagedAccount): Boolean = when (this) {
        All -> true
        Patients -> account.profile.role == UserRole.Patient
        Doctors -> account.profile.role == UserRole.Doctor
        Caregivers -> account.profile.role == UserRole.Caregiver
        Admins -> account.profile.role == UserRole.Admin
        Suspended -> account.profile.isSuspended
    }
}

/** Filters for the operational alert list. */
enum class AlertFilter(val label: String) {
    Open("Open"),
    Escalated("Escalated"),
    Resolved("Resolved"),
    All("All");

    fun matches(alert: Alert): Boolean = when (this) {
        Open -> !alert.isResolved
        Escalated -> alert.caregiverNotifiedAt != null || alert.doctorNotifiedAt != null
        Resolved -> alert.isResolved
        All -> true
    }
}

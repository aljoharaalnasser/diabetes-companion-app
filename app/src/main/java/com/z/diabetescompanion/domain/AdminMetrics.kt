package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.AccountStatus
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertSeverity
import com.z.diabetescompanion.data.model.AlertStage
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Administrator reporting figures (FR-14, S3-01).
 *
 * A pure function of the records so the numbers are unit-testable against known fixtures,
 * which is what the "counts match records" acceptance criterion asks for. Built the same way
 * as [WeeklySummary]: the screen renders, this counts, nothing here reaches the network.
 *
 * Deliberately operational. It counts accounts, consent links and alert status, and never
 * reads glucose values, medication, messages or insulin requests, so the admin view stays
 * within the data it actually needs (NFR-09).
 */
data class AdminMetrics(
    val totalAccounts: Int,
    val accountsByRole: Map<UserRole, Int>,
    val suspendedAccounts: Int,
    /** Patients with at least one active care link: the charter's "active cases". */
    val activeCases: Int,
    val patientsWithoutCareTeam: Int,
    val activeDoctorLinks: Int,
    val activeCaregiverLinks: Int,
    val revokedLinks: Int,
    val totalAlerts: Int,
    val openAlerts: Int,
    val resolvedAlerts: Int,
    val openCriticalAlerts: Int,
    val alertsByStage: Map<AlertStage, Int>,
    val acknowledgedNotResolved: Int,
    val escalatedBeyondPatient: Int,
    /** Null when no alert has ever been raised, so the screen says so instead of showing 0%. */
    val resolutionRatePercent: Int?,
    /** Mean minutes from the reading to its recorded resolution; null when none are resolved. */
    val averageMinutesToResolve: Int?,
    val pendingDeletionRequests: Int,
    val completedDeletionRequests: Int,
) {
    val totalPatients: Int get() = accountsByRole[UserRole.Patient] ?: 0
    val totalDoctors: Int get() = accountsByRole[UserRole.Doctor] ?: 0
    val totalCaregivers: Int get() = accountsByRole[UserRole.Caregiver] ?: 0
    val totalAdmins: Int get() = accountsByRole[UserRole.Admin] ?: 0

    fun stageCount(stage: AlertStage): Int = alertsByStage[stage] ?: 0

    companion object {
        val EMPTY = from(emptyList(), emptyList(), emptyList(), emptyList())

        fun from(
            users: List<UserProfile>,
            careLinks: List<CareLink>,
            alerts: List<Alert>,
            deletionRequests: List<DeletionRequest>,
        ): AdminMetrics {
            val activeLinks = careLinks.filter { it.status == CareLinkStatus.Active }
            val patients = users.filter { it.role == UserRole.Patient }
            val patientsWithTeam = activeLinks.map { it.patientUid }.toSet()
            val resolved = alerts.filter { it.isResolved }

            val resolutionMinutes = resolved.mapNotNull { alert ->
                alert.resolvedAt?.let { resolvedAt ->
                    TimeUnit.MILLISECONDS.toMinutes(resolvedAt.time - alert.recordedAt.time)
                }
            }.filter { it >= 0 }

            return AdminMetrics(
                totalAccounts = users.size,
                accountsByRole = UserRole.entries.associateWith { role ->
                    users.count { it.role == role }
                },
                suspendedAccounts = users.count { it.accountStatus == AccountStatus.Suspended },
                activeCases = patients.count { it.uid in patientsWithTeam },
                patientsWithoutCareTeam = patients.count { it.uid !in patientsWithTeam },
                activeDoctorLinks = activeLinks.count { it.memberRole == UserRole.Doctor },
                activeCaregiverLinks = activeLinks.count { it.memberRole == UserRole.Caregiver },
                revokedLinks = careLinks.count { it.status == CareLinkStatus.Revoked },
                totalAlerts = alerts.size,
                openAlerts = alerts.count { !it.isResolved },
                resolvedAlerts = resolved.size,
                openCriticalAlerts = alerts.count { !it.isResolved && it.severity == AlertSeverity.Critical },
                alertsByStage = AlertStage.entries.associateWith { stage ->
                    alerts.count { it.stage == stage }
                },
                acknowledgedNotResolved = alerts.count { it.acknowledgedButOpen },
                escalatedBeyondPatient = alerts.count {
                    it.caregiverNotifiedAt != null || it.doctorNotifiedAt != null
                },
                resolutionRatePercent = if (alerts.isEmpty()) null
                else (resolved.size * 100.0 / alerts.size).roundToInt(),
                averageMinutesToResolve = resolutionMinutes.takeIf { it.isNotEmpty() }
                    ?.average()?.roundToInt(),
                pendingDeletionRequests = deletionRequests.count { it.isPending },
                completedDeletionRequests = deletionRequests.count { !it.isPending },
            )
        }
    }
}

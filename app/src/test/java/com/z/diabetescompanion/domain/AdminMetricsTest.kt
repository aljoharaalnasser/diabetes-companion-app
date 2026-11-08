package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.AccountStatus
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertSeverity
import com.z.diabetescompanion.data.model.AlertStage
import com.z.diabetescompanion.data.model.CareLink
import com.z.diabetescompanion.data.model.CareLinkStatus
import com.z.diabetescompanion.data.model.DeletionRequest
import com.z.diabetescompanion.data.model.PatientResponse
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The admin dashboard's "counts match records" criterion (US-19), checked against a fixture
 * small enough to count by hand.
 */
class AdminMetricsTest {

    private val now = Date()

    private fun user(
        uid: String,
        role: UserRole,
        status: AccountStatus = AccountStatus.Active,
    ) = UserProfile(
        uid = uid,
        role = role,
        displayName = uid,
        email = "$uid@example.com",
        accountStatus = status,
    )

    private fun link(
        patientUid: String,
        memberUid: String,
        memberRole: UserRole,
        status: CareLinkStatus = CareLinkStatus.Active,
    ) = CareLink(
        id = CareLink.idFor(patientUid, memberUid),
        patientUid = patientUid,
        patientName = patientUid,
        memberUid = memberUid,
        memberName = memberUid,
        memberRole = memberRole,
        status = status,
    )

    private fun alert(
        id: String,
        patientUid: String = "p1",
        severity: AlertSeverity = AlertSeverity.Warning,
        stage: AlertStage = AlertStage.Created,
        response: PatientResponse? = null,
        caregiverNotified: Boolean = false,
        doctorNotified: Boolean = false,
        minutesAgo: Long = 0,
        resolvedAfterMinutes: Long? = null,
    ) = Alert(
        id = id,
        patientUid = patientUid,
        patientName = patientUid,
        valueMgdl = 250.0,
        recordedAt = Date(now.time - TimeUnit.MINUTES.toMillis(minutesAgo)),
        severity = severity,
        stage = stage,
        statusLabel = "High",
        patientResponse = response,
        caregiverNotifiedAt = if (caregiverNotified) now else null,
        doctorNotifiedAt = if (doctorNotified) now else null,
        resolvedAt = resolvedAfterMinutes?.let {
            Date(now.time - TimeUnit.MINUTES.toMillis(minutesAgo) + TimeUnit.MINUTES.toMillis(it))
        },
    )

    // --- Accounts ----------------------------------------------------------

    @Test
    fun `counts accounts by role`() {
        val metrics = AdminMetrics.from(
            users = listOf(
                user("p1", UserRole.Patient),
                user("p2", UserRole.Patient),
                user("p3", UserRole.Patient),
                user("d1", UserRole.Doctor),
                user("c1", UserRole.Caregiver),
                user("a1", UserRole.Admin),
            ),
            careLinks = emptyList(),
            alerts = emptyList(),
            deletionRequests = emptyList(),
        )

        assertEquals(6, metrics.totalAccounts)
        assertEquals(3, metrics.totalPatients)
        assertEquals(1, metrics.totalDoctors)
        assertEquals(1, metrics.totalCaregivers)
        assertEquals(1, metrics.totalAdmins)
    }

    @Test
    fun `counts suspended accounts`() {
        val metrics = AdminMetrics.from(
            users = listOf(
                user("p1", UserRole.Patient),
                user("p2", UserRole.Patient, AccountStatus.Suspended),
                user("d1", UserRole.Doctor, AccountStatus.Suspended),
            ),
            careLinks = emptyList(),
            alerts = emptyList(),
            deletionRequests = emptyList(),
        )

        assertEquals(2, metrics.suspendedAccounts)
    }

    // --- Care-team coverage ------------------------------------------------

    @Test
    fun `a patient counts as an active case only with an active link`() {
        val metrics = AdminMetrics.from(
            users = listOf(
                user("p1", UserRole.Patient),
                user("p2", UserRole.Patient),
                user("p3", UserRole.Patient),
                user("d1", UserRole.Doctor),
            ),
            careLinks = listOf(
                link("p1", "d1", UserRole.Doctor),
                // p2's only link was revoked, so p2 is not an active case.
                link("p2", "d1", UserRole.Doctor, CareLinkStatus.Revoked),
            ),
            alerts = emptyList(),
            deletionRequests = emptyList(),
        )

        assertEquals(1, metrics.activeCases)
        assertEquals(2, metrics.patientsWithoutCareTeam)
        assertEquals(1, metrics.activeDoctorLinks)
        assertEquals(1, metrics.revokedLinks)
    }

    @Test
    fun `two links for one patient still count as one active case`() {
        val metrics = AdminMetrics.from(
            users = listOf(user("p1", UserRole.Patient)),
            careLinks = listOf(
                link("p1", "d1", UserRole.Doctor),
                link("p1", "c1", UserRole.Caregiver),
            ),
            alerts = emptyList(),
            deletionRequests = emptyList(),
        )

        assertEquals(1, metrics.activeCases)
        assertEquals(1, metrics.activeDoctorLinks)
        assertEquals(1, metrics.activeCaregiverLinks)
    }

    @Test
    fun `care-team accounts are never counted as cases`() {
        val metrics = AdminMetrics.from(
            users = listOf(user("d1", UserRole.Doctor), user("c1", UserRole.Caregiver)),
            careLinks = listOf(link("p1", "d1", UserRole.Doctor)),
            alerts = emptyList(),
            deletionRequests = emptyList(),
        )

        assertEquals(0, metrics.activeCases)
        assertEquals(0, metrics.patientsWithoutCareTeam)
    }

    // --- Alerts ------------------------------------------------------------

    @Test
    fun `splits alerts into open and resolved`() {
        val metrics = AdminMetrics.from(
            users = emptyList(),
            careLinks = emptyList(),
            alerts = listOf(
                alert("a", stage = AlertStage.Created),
                alert("b", stage = AlertStage.CaregiverNotified, caregiverNotified = true),
                alert("c", stage = AlertStage.Resolved, resolvedAfterMinutes = 10),
            ),
            deletionRequests = emptyList(),
        )

        assertEquals(3, metrics.totalAlerts)
        assertEquals(2, metrics.openAlerts)
        assertEquals(1, metrics.resolvedAlerts)
        assertEquals(33, metrics.resolutionRatePercent)
    }

    @Test
    fun `counts open critical alerts only while they are open`() {
        val metrics = AdminMetrics.from(
            users = emptyList(),
            careLinks = emptyList(),
            alerts = listOf(
                alert("a", severity = AlertSeverity.Critical),
                alert("b", severity = AlertSeverity.Critical, stage = AlertStage.Resolved, resolvedAfterMinutes = 5),
                alert("c", severity = AlertSeverity.Warning),
            ),
            deletionRequests = emptyList(),
        )

        assertEquals(1, metrics.openCriticalAlerts)
    }

    @Test
    fun `counts each escalation stage`() {
        val metrics = AdminMetrics.from(
            users = emptyList(),
            careLinks = emptyList(),
            alerts = listOf(
                alert("a", stage = AlertStage.Created),
                alert("b", stage = AlertStage.Created),
                alert("c", stage = AlertStage.CaregiverNotified, caregiverNotified = true),
                alert("d", stage = AlertStage.DoctorNotified, caregiverNotified = true, doctorNotified = true),
                alert("e", stage = AlertStage.Resolved, resolvedAfterMinutes = 1),
            ),
            deletionRequests = emptyList(),
        )

        assertEquals(2, metrics.stageCount(AlertStage.Created))
        assertEquals(1, metrics.stageCount(AlertStage.CaregiverNotified))
        assertEquals(1, metrics.stageCount(AlertStage.DoctorNotified))
        assertEquals(1, metrics.stageCount(AlertStage.Resolved))
        assertEquals(2, metrics.escalatedBeyondPatient)
    }

    @Test
    fun `an acknowledged alert is not a resolved one`() {
        val metrics = AdminMetrics.from(
            users = emptyList(),
            careLinks = emptyList(),
            alerts = listOf(
                alert("a", response = PatientResponse.Treating),
                alert("b", response = PatientResponse.ImOk),
                // Answered and then resolved: acknowledged, but no longer open.
                alert("c", response = PatientResponse.Treating, stage = AlertStage.Resolved, resolvedAfterMinutes = 3),
                alert("d"),
            ),
            deletionRequests = emptyList(),
        )

        assertEquals(2, metrics.acknowledgedNotResolved)
        assertEquals(3, metrics.openAlerts)
    }

    @Test
    fun `averages the minutes from reading to resolution`() {
        val metrics = AdminMetrics.from(
            users = emptyList(),
            careLinks = emptyList(),
            alerts = listOf(
                alert("a", stage = AlertStage.Resolved, minutesAgo = 60, resolvedAfterMinutes = 10),
                alert("b", stage = AlertStage.Resolved, minutesAgo = 30, resolvedAfterMinutes = 20),
                alert("c", stage = AlertStage.Created),
            ),
            deletionRequests = emptyList(),
        )

        assertEquals(15, metrics.averageMinutesToResolve)
    }

    @Test
    fun `reports no rate rather than zero percent when nothing was ever raised`() {
        val metrics = AdminMetrics.from(emptyList(), emptyList(), emptyList(), emptyList())

        assertNull(metrics.resolutionRatePercent)
        assertNull(metrics.averageMinutesToResolve)
        assertEquals(0, metrics.totalAlerts)
    }

    @Test
    fun `an unresolved alert never contributes to the resolution time`() {
        val metrics = AdminMetrics.from(
            users = emptyList(),
            careLinks = emptyList(),
            alerts = listOf(alert("a", minutesAgo = 120), alert("b", minutesAgo = 5)),
            deletionRequests = emptyList(),
        )

        assertNull(metrics.averageMinutesToResolve)
        assertEquals(0, metrics.resolutionRatePercent)
    }

    // --- Deletion requests -------------------------------------------------

    @Test
    fun `separates pending deletion requests from completed ones`() {
        val metrics = AdminMetrics.from(
            users = emptyList(),
            careLinks = emptyList(),
            alerts = emptyList(),
            deletionRequests = listOf(
                DeletionRequest(id = "p1", uid = "p1", requestedAt = now),
                DeletionRequest(id = "p2", uid = "p2", requestedAt = now, status = DeletionRequest.COMPLETED),
                DeletionRequest(id = "p3", uid = "p3", requestedAt = now),
            ),
        )

        assertEquals(2, metrics.pendingDeletionRequests)
        assertEquals(1, metrics.completedDeletionRequests)
    }

    @Test
    fun `EMPTY is all zeroes so a loading dashboard shows no invented figures`() {
        val metrics = AdminMetrics.EMPTY

        assertEquals(0, metrics.totalAccounts)
        assertEquals(0, metrics.activeCases)
        assertEquals(0, metrics.totalAlerts)
        assertEquals(0, metrics.pendingDeletionRequests)
        assertNull(metrics.resolutionRatePercent)
    }
}

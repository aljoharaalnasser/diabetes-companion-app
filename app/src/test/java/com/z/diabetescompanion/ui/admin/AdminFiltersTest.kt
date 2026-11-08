package com.z.diabetescompanion.ui.admin

import com.z.diabetescompanion.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/** US-19: filters must not misclassify roles, suspension or acknowledged alerts. */
class AdminFiltersTest {
    private fun account(role: UserRole, status: AccountStatus = AccountStatus.Active) =
        ManagedAccount(UserProfile("u", role, "Fictional", "fictional@example.invalid", accountStatus = status), emptyList())

    private fun alert(
        stage: AlertStage = AlertStage.Created,
        response: PatientResponse? = null,
        caregiver: Date? = null,
        doctor: Date? = null,
    ) = Alert("a", "p", "Fictional", 250.0, Date(0),
        AlertSeverity.Warning, stage, "High", patientResponse = response,
        caregiverNotifiedAt = caregiver, doctorNotifiedAt = doctor)

    @Test fun eachRoleFilterMatchesOnlyItsRole() {
        val filters = mapOf(
            UserRole.Patient to AccountFilter.Patients,
            UserRole.Doctor to AccountFilter.Doctors,
            UserRole.Caregiver to AccountFilter.Caregivers,
            UserRole.Admin to AccountFilter.Admins,
        )
        filters.forEach { (expectedRole, filter) ->
            UserRole.entries.forEach { actualRole ->
                assertEquals(expectedRole == actualRole, filter.matches(account(actualRole)))
            }
        }
    }

    @Test fun suspendedFilterAppliesAcrossEveryRole() {
        UserRole.entries.forEach {
            assertTrue(AccountFilter.Suspended.matches(account(it, AccountStatus.Suspended)))
            assertFalse(AccountFilter.Suspended.matches(account(it)))
        }
    }

    @Test fun allAccountsIncludesSuspendedAndActiveAccounts() {
        UserRole.entries.forEach { role ->
            AccountStatus.entries.forEach { status ->
                assertTrue(AccountFilter.All.matches(account(role, status)))
            }
        }
    }

    @Test fun acknowledgementDoesNotMoveAlertToResolvedFilter() {
        val acknowledged = alert(response = PatientResponse.Treating)
        assertTrue(AlertFilter.Open.matches(acknowledged))
        assertFalse(AlertFilter.Resolved.matches(acknowledged))
    }

    @Test fun resolvedAndOpenFiltersAreMutuallyExclusiveForEveryStage() {
        AlertStage.entries.forEach { stage ->
            val item = alert(stage)
            assertEquals(stage == AlertStage.Resolved, AlertFilter.Resolved.matches(item))
            assertEquals(stage != AlertStage.Resolved, AlertFilter.Open.matches(item))
        }
    }

    @Test fun escalationFilterRequiresAnActualNotificationTimestamp() {
        assertFalse(AlertFilter.Escalated.matches(alert()))
        assertTrue(AlertFilter.Escalated.matches(alert(caregiver = Date(0))))
        assertTrue(AlertFilter.Escalated.matches(alert(doctor = Date(0))))
    }

    @Test fun resolvedAlertKeepsEscalationHistory() {
        val resolved = alert(AlertStage.Resolved, doctor = Date(0))
        assertTrue(AlertFilter.Escalated.matches(resolved))
        assertTrue(AlertFilter.Resolved.matches(resolved))
        assertTrue(AlertFilter.All.matches(resolved))
    }
}

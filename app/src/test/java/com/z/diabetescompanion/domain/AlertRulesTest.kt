package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.AlertSeverity
import com.z.diabetescompanion.data.model.AlertStage
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.PatientResponse
import com.z.diabetescompanion.data.model.ReadingSource
import com.z.diabetescompanion.data.model.Thresholds
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

/** Covers the five-minute escalation policy in FR-09 and the lifecycle rules in FR-10. */
class AlertRulesTest {

    private val thresholds = Thresholds(targetLow = 70, targetHigh = 180, criticalLow = 54, criticalHigh = 250)
    private val patient = UserProfile(
        uid = "p1", role = UserRole.Patient, displayName = "Test Patient", email = "p@example.com",
    )
    private val doctor = UserProfile(
        uid = "d1", role = UserRole.Doctor, displayName = "Dr Who", email = "d@example.com",
    )
    private val now = Date(1_800_000_000_000L)

    private fun reading(value: Double) = GlucoseReading(
        id = "r1", valueMgdl = value, enteredValue = value, enteredUnit = GlucoseUnit.MgDl,
        source = ReadingSource.Manual, recordedAt = now,
    )

    private fun alert(value: Double) =
        AlertRules.alertFor(reading(value), thresholds, patient, now)!!.copy(createdAt = now)

    // --- Raising ------------------------------------------------------------

    @Test
    fun `in-range reading raises no alert`() {
        assertNull(AlertRules.alertFor(reading(120.0), thresholds, patient, now))
    }

    @Test
    fun `no alert without configured thresholds`() {
        assertNull(AlertRules.alertFor(reading(400.0), null, patient, now))
    }

    @Test
    fun `out-of-range reading raises a warning that starts at Created`() {
        val a = alert(200.0)
        assertEquals(AlertSeverity.Warning, a.severity)
        assertEquals(AlertStage.Created, a.stage)
        assertNull(a.caregiverNotifiedAt)
    }

    @Test
    fun `critical reading notifies the caregiver immediately`() {
        val a = alert(300.0)
        assertEquals(AlertSeverity.Critical, a.severity)
        assertEquals(AlertStage.CaregiverNotified, a.stage)
        assertNotNull(a.caregiverNotifiedAt)
    }

    @Test
    fun `one reading can only ever raise one alert`() {
        assertEquals(alert(200.0).id, alert(210.0).id)
    }

    // --- Timed escalation ---------------------------------------------------

    @Test
    fun `unanswered warning reaches the caregiver after five minutes`() {
        val a = alert(200.0)
        assertEquals(
            Date(now.time + TimeUnit.MINUTES.toMillis(5)),
            AlertRules.nextEscalationAt(a, now),
        )
        val escalated = AlertRules.applyEscalation(a)!!
        assertEquals(AlertStage.CaregiverNotified, escalated.stage)
    }

    @Test
    fun `caregiver stage escalates to the doctor five minutes later`() {
        val caregiverStage = AlertRules.applyEscalation(alert(200.0))!!
        val doctorStage = AlertRules.applyEscalation(caregiverStage)!!
        assertEquals(AlertStage.DoctorNotified, doctorStage.stage)
        assertNotNull(doctorStage.doctorNotifiedAt)
        // Doctor stage is the end of the automatic chain.
        assertNull(AlertRules.applyEscalation(doctorStage))
    }

    @Test
    fun `answering with I'm OK or Treating stops the countdown without resolving`() {
        listOf(PatientResponse.ImOk, PatientResponse.Treating).forEach { response ->
            val answered = AlertRules.applyResponse(alert(200.0), response, now)
            assertNull(AlertRules.nextStageOnTimeout(answered))
            assertNull(AlertRules.secondsUntilEscalation(answered, now))
            assertTrue(answered.acknowledgedButOpen)
            assertEquals(AlertStage.Created, answered.stage)
        }
    }

    @Test
    fun `Need Help notifies caregiver and doctor immediately`() {
        val a = AlertRules.applyResponse(alert(200.0), PatientResponse.NeedHelp, now)
        assertEquals(AlertStage.DoctorNotified, a.stage)
        assertNotNull(a.caregiverNotifiedAt)
        assertNotNull(a.doctorNotifiedAt)
    }

    @Test
    fun `Contact Doctor notifies the doctor and never moves the stage backwards`() {
        val critical = alert(300.0)
        val a = AlertRules.applyResponse(critical, PatientResponse.ContactDoctor, now)
        assertEquals(AlertStage.DoctorNotified, a.stage)
        assertNotNull(a.doctorNotifiedAt)
    }

    @Test
    fun `countdown counts down and floors at zero`() {
        val a = alert(200.0)
        assertEquals(300L, AlertRules.secondsUntilEscalation(a, now))
        assertEquals(120L, AlertRules.secondsUntilEscalation(a, Date(now.time + 180_000)))
        assertEquals(0L, AlertRules.secondsUntilEscalation(a, Date(now.time + 900_000)))
    }

    // --- Resolution ---------------------------------------------------------

    @Test
    fun `resolution records who closed it with a note and stops escalation`() {
        val resolved = AlertRules.applyResolution(alert(200.0), doctor, "Called the patient", now)
        assertEquals(AlertStage.Resolved, resolved.stage)
        assertEquals("Dr Who", resolved.resolvedByName)
        assertEquals("Called the patient", resolved.resolutionNote)
        assertNull(AlertRules.nextStageOnTimeout(resolved))
        assertNull(AlertRules.applyEscalation(resolved))
    }

    @Test
    fun `a resolution note is required`() {
        assertNotNull(AlertRules.resolutionNoteError(""))
        assertNotNull(AlertRules.resolutionNoteError("   "))
        assertNotNull(AlertRules.resolutionNoteError("ok"))
        assertNull(AlertRules.resolutionNoteError("Spoke with the patient"))
    }

    @Test
    fun `the timeline records every stage in order`() {
        val answered = AlertRules.applyResponse(alert(200.0), PatientResponse.Treating, now)
        val escalatedToDoctor = AlertRules.applyEscalation(
            AlertRules.applyEscalation(answered.copy(patientResponse = null)) ?: error("no escalation")
        )!!
        val resolved = AlertRules.applyResolution(escalatedToDoctor, doctor, "Followed up", now)
        val types = resolved.events.map { it.type }
        assertTrue(types.contains("created"))
        assertTrue(types.contains("acknowledged"))
        assertTrue(types.contains("caregiver_notified"))
        assertTrue(types.contains("doctor_notified"))
        assertEquals("resolved", types.last())
    }
}

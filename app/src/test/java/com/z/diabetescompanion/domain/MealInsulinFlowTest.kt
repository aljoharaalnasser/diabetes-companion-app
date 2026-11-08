package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.CarbEstimate
import com.z.diabetescompanion.data.model.CarbItem
import com.z.diabetescompanion.data.model.DoseSuggestionStatus
import com.z.diabetescompanion.data.model.IcrEnteredBy
import com.z.diabetescompanion.data.model.IcrSetting
import com.z.diabetescompanion.data.model.IcrStatus
import com.z.diabetescompanion.data.model.MedicationKind
import com.z.diabetescompanion.data.model.MedicationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MealInsulinFlowTest {

    private val now = Date(1_790_000_000_000)

    private fun icr(version: Int, grams: Double, status: IcrStatus) = IcrSetting(
        icrId = "icr$version",
        patientId = "p1",
        gramsPerUnit = grams,
        status = status,
        enteredBy = if (status == IcrStatus.Confirmed) IcrEnteredBy.Doctor else IcrEnteredBy.Patient,
        version = version,
        confirmedByDoctorId = if (status == IcrStatus.Confirmed) "d1" else "",
        effectiveFrom = Date(now.time + version * 1000L),
    )

    private val estimate = CarbEstimate(
        items = listOf(CarbItem("Rice", "1 cup", 45.0), CarbItem("Dal", "1 bowl", 35.0)),
        totalCarbsGrams = 80.0,
    )

    private fun ready(carbsText: String, versions: List<IcrSetting>): MealDoseOutcome.DoseReady {
        val outcome = MealInsulinFlow.evaluate(carbsText, versions)
        assertTrue("expected DoseReady but was $outcome", outcome is MealDoseOutcome.DoseReady)
        return outcome as MealDoseOutcome.DoseReady
    }

    @Test
    fun `edited carbs are used instead of the AI estimate`() {
        val versions = listOf(icr(1, 10.0, IcrStatus.Confirmed))
        // AI said 80 g; the patient edited the total to 75 g.
        val outcome = ready("75", versions)
        assertEquals(7.5, outcome.calculation.units, 0.0)

        val suggestion = MealInsulinRecords.newSuggestion("s1", "p1", "rice and dal", estimate, outcome, now)
        assertEquals(80.0, suggestion.aiCarbs, 0.0)
        assertEquals(75.0, suggestion.confirmedCarbs, 0.0)
        assertEquals(7.5, suggestion.suggestedUnits, 0.0)
        assertEquals(DoseSuggestionStatus.Suggested, suggestion.status)
        assertNull(suggestion.takenUnits)
    }

    @Test
    fun `no ICR means no dose card`() {
        assertEquals(MealDoseOutcome.NoIcr, MealInsulinFlow.evaluate("75", emptyList()))
    }

    @Test
    fun `unconfirmed ICR means no dose card`() {
        val outcome = MealInsulinFlow.evaluate("75", listOf(icr(1, 10.0, IcrStatus.Unconfirmed)))
        assertTrue(outcome is MealDoseOutcome.NeedsDoctorConfirmation)
    }

    @Test
    fun `a newer patient-entered ICR blocks the dose until the doctor confirms it`() {
        val versions = listOf(icr(1, 10.0, IcrStatus.Confirmed), icr(2, 12.0, IcrStatus.Unconfirmed))
        val outcome = MealInsulinFlow.evaluate("75", versions)
        assertTrue(outcome is MealDoseOutcome.NeedsDoctorConfirmation)
        assertEquals(2, (outcome as MealDoseOutcome.NeedsDoctorConfirmation).pending.version)
    }

    @Test
    fun `the newest confirmed version is used`() {
        val versions = listOf(icr(2, 15.0, IcrStatus.Confirmed), icr(1, 10.0, IcrStatus.Confirmed))
        val outcome = ready("75", versions)
        assertEquals(15.0, outcome.icr.gramsPerUnit, 0.0)
        assertEquals(5.0, outcome.calculation.units, 0.0)
    }

    @Test
    fun `a dose that rounds down to zero shows no dose card`() {
        val outcome = MealInsulinFlow.evaluate("4", listOf(icr(1, 10.0, IcrStatus.Confirmed)))
        assertTrue("was $outcome", outcome is MealDoseOutcome.BelowMinimumDose)
        assertEquals(0.0, (outcome as MealDoseOutcome.BelowMinimumDose).calculation.units, 0.0)
    }

    @Test
    fun `invalid confirmed carbs never produce a dose`() {
        val versions = listOf(icr(1, 10.0, IcrStatus.Confirmed))
        listOf("", "0", "-10", "abc").forEach {
            assertTrue(it, MealInsulinFlow.evaluate(it, versions) is MealDoseOutcome.InvalidCarbs)
        }
    }

    @Test
    fun `a new ICR version does not change an old suggestion`() {
        val v1 = icr(1, 10.0, IcrStatus.Confirmed)
        val old = MealInsulinRecords.newSuggestion("s1", "p1", "meal", estimate, ready("75", listOf(v1)), now)

        val v2 = icr(IcrPolicy.nextVersion(listOf(v1)), 15.0, IcrStatus.Confirmed)
        assertEquals(2, v2.version)
        val newer = MealInsulinRecords.newSuggestion("s2", "p1", "meal", estimate, ready("75", listOf(v1, v2)), now)

        assertEquals(10.0, old.icrValue, 0.0)
        assertEquals(1, old.icrVersion)
        assertEquals("icr1", old.icrId)
        assertEquals(7.5, old.suggestedUnits, 0.0)
        assertEquals(15.0, newer.icrValue, 0.0)
        assertEquals(2, newer.icrVersion)
    }

    @Test
    fun `I took creates an insulin log entry linked to the suggestion`() {
        val suggestion = MealInsulinRecords.newSuggestion(
            "s9", "p1", "meal", estimate, ready("75", listOf(icr(1, 10.0, IcrStatus.Confirmed))), now,
        )
        val log = MealInsulinRecords.takenLog(suggestion, takenUnits = 7.0, takenAt = now)

        assertEquals(MedicationSource.CalculatorSuggestion, log.source)
        assertEquals("CALCULATOR_SUGGESTION", log.source.storageKey)
        assertEquals("s9", log.suggestionId)
        assertEquals(MedicationKind.Insulin, log.kind)
        assertEquals(7.0, log.dose, 0.0)
        assertEquals("units", log.unit)
        assertEquals(now, log.takenAt)
        assertEquals(MealInsulinRecords.medicationLogIdFor("s9"), log.id)

        val taken = MealInsulinRecords.markTaken(suggestion, 7.0, log.id, now)
        assertEquals(DoseSuggestionStatus.Taken, taken.status)
        assertEquals(7.0, taken.takenUnits!!, 0.0)
        assertEquals(log.id, taken.medicationLogId)
        // The suggestion keeps what the app calculated, even though the patient took less.
        assertEquals(7.5, taken.suggestedUnits, 0.0)
    }

    @Test
    fun `Not now marks the suggestion skipped`() {
        val suggestion = MealInsulinRecords.newSuggestion(
            "s3", "p1", "meal", estimate, ready("75", listOf(icr(1, 10.0, IcrStatus.Confirmed))), now,
        )
        val skipped = MealInsulinRecords.markSkipped(suggestion, now)
        assertEquals(DoseSuggestionStatus.Skipped, skipped.status)
        assertNull(skipped.takenUnits)
    }
}

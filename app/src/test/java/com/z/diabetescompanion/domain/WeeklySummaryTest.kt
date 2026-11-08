package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.ai.AiPrompts
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.MedicationKind
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.ReadingSource
import com.z.diabetescompanion.data.model.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class WeeklySummaryTest {

    private val thresholds = Thresholds(targetLow = 70, targetHigh = 180, criticalLow = 54, criticalHigh = 250)

    /** Wednesday of a known week, so the Monday boundary is unambiguous. */
    private val wednesday: Date = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.SEPTEMBER, 23, 12, 0, 0)
    }.time

    private fun reading(value: Double, daysBefore: Int) = GlucoseReading(
        id = "r$value-$daysBefore", valueMgdl = value, enteredValue = value,
        enteredUnit = GlucoseUnit.MgDl, source = ReadingSource.Manual,
        recordedAt = Date(wednesday.time - TimeUnit.DAYS.toMillis(daysBefore.toLong())),
    )

    private fun dose(daysBefore: Int) = MedicationLog(
        id = "m$daysBefore", name = "Metformin", dose = 500.0, unit = "mg",
        kind = MedicationKind.Medication,
        takenAt = Date(wednesday.time - TimeUnit.DAYS.toMillis(daysBefore.toLong())),
    )

    @Test
    fun `this week includes only readings since Monday`() {
        val summary = WeeklySummary.build(
            readings = listOf(reading(100.0, 0), reading(120.0, 1), reading(140.0, 5)),
            medications = emptyList(),
            thresholds = thresholds,
            now = wednesday,
        )
        // Five days before Wednesday falls in the previous week.
        assertEquals(2, summary.readingCount)
    }

    @Test
    fun `statistics use only recorded readings`() {
        val summary = WeeklySummary.build(
            readings = listOf(reading(100.0, 0), reading(200.0, 1), reading(60.0, 2)),
            medications = listOf(dose(0), dose(1)),
            thresholds = thresholds,
            now = wednesday,
        )
        assertEquals(120, summary.averageMgdl)
        assertEquals(60, summary.lowestMgdl)
        assertEquals(200, summary.highestMgdl)
        assertEquals(1, summary.highEvents)
        assertEquals(1, summary.lowEvents)
        assertEquals(33, summary.timeInRangePercent)
        assertEquals(2, summary.doseCount)
    }

    @Test
    fun `time in range is unavailable without a doctor-set target`() {
        val summary = WeeklySummary.build(
            readings = listOf(reading(100.0, 0)),
            medications = emptyList(),
            thresholds = null,
            now = wednesday,
        )
        assertNull(summary.timeInRangePercent)
    }

    @Test
    fun `insufficient data is reported rather than guessed`() {
        val thin = WeeklySummary.build(listOf(reading(100.0, 0)), emptyList(), thresholds, now = wednesday)
        assertFalse(thin.hasEnoughData)
        val enough = WeeklySummary.build(
            listOf(reading(100.0, 0), reading(110.0, 1), reading(120.0, 2)),
            emptyList(), thresholds, now = wednesday,
        )
        assertTrue(enough.hasEnoughData)
    }

    @Test
    fun `the AI stats block carries figures but no identifiers`() {
        val block = WeeklySummary.build(
            listOf(reading(100.0, 0)), listOf(dose(0)), thresholds, now = wednesday,
        ).toStatsBlock()
        assertTrue(block.contains("Readings recorded: 1"))
        assertFalse(block.contains("@"))
        assertFalse(block.lowercase().contains("uid"))
    }

    @Test
    fun `previous week is a separate window`() {
        val lastWeek = WeeklySummary.build(
            readings = listOf(reading(100.0, 0), reading(140.0, 5)),
            medications = emptyList(), thresholds = thresholds, weeksAgo = 1, now = wednesday,
        )
        assertEquals(1, lastWeek.readingCount)
        assertEquals(140, lastWeek.averageMgdl)
    }
}

/** The chatbot must refuse dose questions before anything leaves the device (FR-16). */
class ChatbotGuardTest {

    @Test
    fun `dose questions are detected`() {
        listOf(
            "How much insulin should I take?",
            "how many units do I need tonight",
            "Should I increase my insulin?",
            "what dose of metformin is right",
            "Can I skip my dose today?",
        ).forEach { assertTrue(it, AiPrompts.looksLikeDoseQuestion(it)) }
    }

    @Test
    fun `general education questions are allowed through`() {
        listOf(
            "What does HbA1c mean?",
            "How many carbs are in a banana?",
            "Why does my glucose rise after breakfast?",
            "What is a normal trend over a week?",
        ).forEach { assertFalse(it, AiPrompts.looksLikeDoseQuestion(it)) }
    }
}

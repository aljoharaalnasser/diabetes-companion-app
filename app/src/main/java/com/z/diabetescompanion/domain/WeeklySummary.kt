package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.MedicationLog
import com.z.diabetescompanion.data.model.Thresholds
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Weekly glucose and medication statistics (FR-20, S2-09).
 *
 * Everything here is computed from stored records, never from the AI. The screen labels these
 * figures as recorded data and keeps the AI narrative visually separate, as the charter requires.
 * These aggregates are deliberately reusable for the Sprint 3 admin reporting.
 */
data class WeeklySummary(
    val start: Date,
    val end: Date,
    val readings: List<GlucoseReading>,
    val medications: List<MedicationLog>,
    val thresholds: Thresholds?,
) {
    val readingCount: Int get() = readings.size
    val doseCount: Int get() = medications.size

    /** The charter requires insufficient data to be stated plainly, not hidden. */
    val hasEnoughData: Boolean get() = readingCount >= MIN_READINGS

    val averageMgdl: Int? get() = readings.takeIf { it.isNotEmpty() }
        ?.map { it.valueMgdl }?.average()?.roundToInt()

    val lowestMgdl: Int? get() = readings.minOfOrNull { it.valueMgdl }?.roundToInt()
    val highestMgdl: Int? get() = readings.maxOfOrNull { it.valueMgdl }?.roundToInt()

    private val statuses: List<GlucoseStatus>
        get() = readings.map { GlucoseStatus.of(it.valueMgdl, thresholds) }

    val inRangeCount: Int get() = statuses.count { it == GlucoseStatus.Ideal }
    val lowEvents: Int get() = statuses.count { it == GlucoseStatus.Low || it == GlucoseStatus.CriticalLow }
    val highEvents: Int get() = statuses.count { it == GlucoseStatus.High || it == GlucoseStatus.CriticalHigh }

    /** Null when the doctor has not set a target range, since there is nothing to measure against. */
    val timeInRangePercent: Int?
        get() = if (thresholds == null || readings.isEmpty()) null
        else (inRangeCount * 100.0 / readings.size).roundToInt()

    val rangeLabel: String
        get() {
            val format = SimpleDateFormat("d MMM", Locale.getDefault())
            return "${format.format(start)} - ${format.format(end)}"
        }

    /** De-identified block handed to the AI for the plain-language overview (NFR-09). */
    fun toStatsBlock(): String = buildString {
        appendLine("Week: $rangeLabel")
        appendLine("Readings recorded: $readingCount")
        appendLine("Average glucose: ${averageMgdl?.let { "$it mg/dL" } ?: "not available"}")
        appendLine("Lowest: ${lowestMgdl?.let { "$it mg/dL" } ?: "not available"}")
        appendLine("Highest: ${highestMgdl?.let { "$it mg/dL" } ?: "not available"}")
        appendLine("Time in target range: ${timeInRangePercent?.let { "$it%" } ?: "no target range set"}")
        appendLine("Readings below target: $lowEvents")
        appendLine("Readings above target: $highEvents")
        appendLine("Medication or insulin doses logged: $doseCount")
    }

    companion object {
        const val MIN_READINGS = 3

        /** Week [weeksAgo] back, Monday 00:00 through the following Monday. */
        fun build(
            readings: List<GlucoseReading>,
            medications: List<MedicationLog>,
            thresholds: Thresholds?,
            weeksAgo: Int = 0,
            now: Date = Date(),
        ): WeeklySummary {
            val start = startOfWeek(now, weeksAgo)
            val end = Date(start.time + TimeUnit.DAYS.toMillis(7))
            return WeeklySummary(
                start = start,
                end = end,
                readings = readings.filter { it.recordedAt >= start && it.recordedAt < end },
                medications = medications.filter { it.takenAt >= start && it.takenAt < end },
                thresholds = thresholds,
            )
        }

        private fun startOfWeek(now: Date, weeksAgo: Int): Date = Calendar.getInstance().apply {
            time = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Walk back to Monday, then back the requested number of whole weeks.
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
            add(Calendar.WEEK_OF_YEAR, -weeksAgo)
        }.time
    }
}

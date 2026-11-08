package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.ReadingSource
import com.z.diabetescompanion.data.model.Thresholds
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Status against doctor-configured thresholds (FR-05). Labels are the ones the
 * design brief uses. [NoThresholds] is deliberate: the charter forbids inventing
 * clinical ranges, so until a doctor sets them we say so rather than guess.
 */
enum class GlucoseStatus(val label: String, val description: String) {
    CriticalLow("Critical low", "Below the critical-low threshold set by your doctor."),
    Low("Low", "Below your target range."),
    Ideal("Ideal", "Within your target range."),
    High("High", "Above your target range."),
    CriticalHigh("Critical high", "Above the critical-high threshold set by your doctor."),
    NoThresholds("No target set", "Your doctor hasn't configured a target range yet.");

    val isOutOfRange: Boolean get() = this != Ideal && this != NoThresholds

    companion object {
        fun of(valueMgdl: Double, thresholds: Thresholds?): GlucoseStatus {
            if (thresholds == null) return NoThresholds
            return when {
                valueMgdl < thresholds.criticalLow -> CriticalLow
                valueMgdl < thresholds.targetLow -> Low
                valueMgdl > thresholds.criticalHigh -> CriticalHigh
                valueMgdl > thresholds.targetHigh -> High
                else -> Ideal
            }
        }
    }
}

enum class Trend(val label: String) {
    Rising("Rising"),
    Falling("Falling"),
    Stable("Stable"),
    Unavailable("Trend unavailable");
}

object TrendCalculator {
    const val MIN_READINGS = 3
    private const val WINDOW_HOURS = 48L
    private const val DELTA_MGDL = 15.0

    /** [readingsNewestFirst] as returned by the repository. */
    fun of(readingsNewestFirst: List<GlucoseReading>): Trend {
        if (readingsNewestFirst.size < MIN_READINGS) return Trend.Unavailable
        val recent = readingsNewestFirst.take(MIN_READINGS)
        val spanMs = recent.first().recordedAt.time - recent.last().recordedAt.time
        if (spanMs > TimeUnit.HOURS.toMillis(WINDOW_HOURS)) return Trend.Unavailable
        val delta = recent.first().valueMgdl - recent.last().valueMgdl
        return when {
            delta >= DELTA_MGDL -> Trend.Rising
            delta <= -DELTA_MGDL -> Trend.Falling
            else -> Trend.Stable
        }
    }
}

/** Input rules for FR-03 / NFR-05. Returns a message or null when valid. */
object GlucoseValidation {
    private const val MIN_MGDL = 20.0
    private const val MAX_MGDL = 600.0

    fun valueError(raw: String, unit: GlucoseUnit): String? {
        if (raw.isBlank()) return "Enter a glucose value."
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return "Enter a number."
        val mgdl = unit.toMgdl(value)
        val min = unit.fromMgdl(MIN_MGDL)
        val max = unit.fromMgdl(MAX_MGDL)
        return when {
            mgdl < MIN_MGDL || mgdl > MAX_MGDL ->
                "Value must be between ${min.format(unit)} and ${max.format(unit)} ${unit.label}."
            else -> null
        }
    }

    fun timeError(recordedAt: Date, now: Date = Date()): String? = when {
        recordedAt.after(Date(now.time + TimeUnit.MINUTES.toMillis(5))) -> "Time can't be in the future."
        recordedAt.before(Date(now.time - TimeUnit.DAYS.toMillis(366))) -> "Time can't be more than a year ago."
        else -> null
    }

    private fun Double.format(unit: GlucoseUnit): String =
        if (unit == GlucoseUnit.MgDl) roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", this)
}

/**
 * In-app stand-in for the charter's Python data-generation pipeline (FR-03).
 * Produces a plausible value that drifts from the last reading so demos look realistic.
 */
object DemoReadingGenerator {
    fun next(lastMgdl: Double?, random: Random = Random.Default): GlucoseReading {
        val base = lastMgdl ?: random.nextDouble(90.0, 160.0)
        val drift = random.nextDouble(-35.0, 35.0)
        val value = (base + drift).coerceIn(55.0, 290.0).roundToInt().toDouble()
        val now = Date()
        return GlucoseReading(
            id = readingId(now, value),
            valueMgdl = value,
            enteredValue = value,
            enteredUnit = GlucoseUnit.MgDl,
            source = ReadingSource.Demo,
            recordedAt = now,
        )
    }
}

/** Stable id so a double-tap on Save writes the same document twice instead of two docs. */
fun readingId(recordedAt: Date, valueMgdl: Double): String =
    "${recordedAt.time / 1000}_${abs(valueMgdl).roundToInt()}"

fun Double.formatGlucose(unit: GlucoseUnit): String =
    if (unit == GlucoseUnit.MgDl) roundToInt().toString()
    else String.format(java.util.Locale.US, "%.1f", this)

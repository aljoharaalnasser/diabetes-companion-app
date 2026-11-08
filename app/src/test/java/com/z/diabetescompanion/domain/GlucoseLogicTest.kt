package com.z.diabetescompanion.domain

import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.data.model.ReadingSource
import com.z.diabetescompanion.data.model.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class GlucoseLogicTest {

    private val thresholds = Thresholds(targetLow = 70, targetHigh = 180, criticalLow = 54, criticalHigh = 250)

    private fun reading(value: Double, minutesAgo: Long) = GlucoseReading(
        id = readingId(Date(), value),
        valueMgdl = value,
        enteredValue = value,
        enteredUnit = GlucoseUnit.MgDl,
        source = ReadingSource.Manual,
        recordedAt = Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutesAgo)),
    )

    // --- GlucoseStatus -----------------------------------------------------

    @Test
    fun `status is NoThresholds when doctor has not configured any`() {
        assertEquals(GlucoseStatus.NoThresholds, GlucoseStatus.of(120.0, null))
    }

    @Test
    fun `status bands follow threshold ordering`() {
        assertEquals(GlucoseStatus.CriticalLow, GlucoseStatus.of(50.0, thresholds))
        assertEquals(GlucoseStatus.Low, GlucoseStatus.of(65.0, thresholds))
        assertEquals(GlucoseStatus.Ideal, GlucoseStatus.of(70.0, thresholds))
        assertEquals(GlucoseStatus.Ideal, GlucoseStatus.of(180.0, thresholds))
        assertEquals(GlucoseStatus.High, GlucoseStatus.of(181.0, thresholds))
        assertEquals(GlucoseStatus.CriticalHigh, GlucoseStatus.of(251.0, thresholds))
    }

    // --- TrendCalculator ---------------------------------------------------

    @Test
    fun `trend is unavailable with fewer than three readings`() {
        assertEquals(Trend.Unavailable, TrendCalculator.of(listOf(reading(100.0, 0), reading(120.0, 60))))
    }

    @Test
    fun `trend is unavailable when readings span more than 48 hours`() {
        val readings = listOf(reading(100.0, 0), reading(110.0, 60), reading(140.0, 60 * 50))
        assertEquals(Trend.Unavailable, TrendCalculator.of(readings))
    }

    @Test
    fun `trend rising, falling and stable use a 15 mgdl delta`() {
        assertEquals(Trend.Rising, TrendCalculator.of(listOf(reading(140.0, 0), reading(120.0, 60), reading(100.0, 120))))
        assertEquals(Trend.Falling, TrendCalculator.of(listOf(reading(100.0, 0), reading(120.0, 60), reading(140.0, 120))))
        assertEquals(Trend.Stable, TrendCalculator.of(listOf(reading(110.0, 0), reading(120.0, 60), reading(100.0, 120))))
    }

    // --- GlucoseValidation -------------------------------------------------

    @Test
    fun `value validation rejects blank, non-numeric and out of range`() {
        assertNotNull(GlucoseValidation.valueError("", GlucoseUnit.MgDl))
        assertNotNull(GlucoseValidation.valueError("abc", GlucoseUnit.MgDl))
        assertNotNull(GlucoseValidation.valueError("10", GlucoseUnit.MgDl))
        assertNotNull(GlucoseValidation.valueError("700", GlucoseUnit.MgDl))
        assertNull(GlucoseValidation.valueError("120", GlucoseUnit.MgDl))
    }

    @Test
    fun `mmol values are validated after conversion`() {
        assertNull(GlucoseValidation.valueError("6,5", GlucoseUnit.MmolL))
        assertNotNull(GlucoseValidation.valueError("40", GlucoseUnit.MmolL))
    }

    @Test
    fun `time validation rejects future and very old timestamps`() {
        val now = Date()
        assertNotNull(GlucoseValidation.timeError(Date(now.time + TimeUnit.HOURS.toMillis(1)), now))
        assertNotNull(GlucoseValidation.timeError(Date(now.time - TimeUnit.DAYS.toMillis(400)), now))
        assertNull(GlucoseValidation.timeError(Date(now.time - TimeUnit.HOURS.toMillis(2)), now))
    }

    // --- Unit conversion and ids -------------------------------------------

    @Test
    fun `unit conversion round trips`() {
        val mgdl = GlucoseUnit.MmolL.toMgdl(5.5)
        assertEquals(5.5, GlucoseUnit.MmolL.fromMgdl(mgdl), 0.0001)
        assertEquals(99.1, mgdl, 0.1)
    }

    @Test
    fun `reading id is deterministic per second and value so double taps dedupe`() {
        val at = Date(1_700_000_000_123L)
        assertEquals(readingId(at, 112.0), readingId(Date(1_700_000_000_900L), 112.4))
    }

    @Test
    fun `demo generator stays within plausible bounds and is labelled demo`() {
        repeat(200) {
            val r = DemoReadingGenerator.next(lastMgdl = 300.0)
            assertTrue(r.valueMgdl in 55.0..290.0)
            assertEquals(ReadingSource.Demo, r.source)
        }
    }
}

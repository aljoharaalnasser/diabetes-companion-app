package com.z.diabetescompanion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsulinCalculatorTest {

    private fun ok(carbs: Double?, icr: Double?, step: Double = MealInsulinConfig.ROUNDING_STEP_UNITS): DoseCalculation.Ok {
        val result = InsulinCalculator.calculate(carbs, icr, step)
        assertTrue("expected Ok but was $result", result is DoseCalculation.Ok)
        return result as DoseCalculation.Ok
    }

    private fun assertInvalid(carbs: Double?, icr: Double?) {
        val result = InsulinCalculator.calculate(carbs, icr)
        assertTrue("expected Invalid for carbs=$carbs icr=$icr but was $result", result is DoseCalculation.Invalid)
    }

    @Test
    fun `75 g with ICR 10 is 7_5 units`() {
        val calc = ok(75.0, 10.0)
        assertEquals(7.5, calc.units, 0.0)
        assertFalse(calc.wasRounded)
        assertEquals("75 g / ICR 10 = 7.5 units", calc.working)
    }

    @Test
    fun `64 g with ICR 10 rounds down to 6_0 units`() {
        val calc = ok(64.0, 10.0)
        assertEquals(6.0, calc.units, 0.0)
        assertTrue(calc.wasRounded)
        assertEquals("64 g / ICR 10 = 6.4, rounded down to 6.0 units", calc.working)
    }

    @Test
    fun `always rounds down, never up`() {
        assertEquals(6.5, ok(69.9, 10.0).units, 0.0)
        assertEquals(3.0, ok(10.0, 3.0).units, 0.0)
        assertEquals(0.0, ok(4.0, 10.0).units, 0.0)
        assertEquals(3.0, ok(45.0, 15.0).units, 0.0)
    }

    @Test
    fun `rounding step comes from config and can change`() {
        assertEquals(0.5, MealInsulinConfig.ROUNDING_STEP_UNITS, 0.0)
        assertEquals(6.0, ok(69.0, 10.0, step = 1.0).units, 0.0)
        assertEquals(6.5, ok(69.0, 10.0, step = 0.5).units, 0.0)
        assertTrue(InsulinCalculator.calculate(69.0, 10.0, 0.0) is DoseCalculation.Invalid)
    }

    @Test
    fun `zero, negative or missing carbs are rejected`() {
        assertInvalid(0.0, 10.0)
        assertInvalid(-20.0, 10.0)
        assertInvalid(null, 10.0)
        assertInvalid(Double.NaN, 10.0)
        assertNotNull(MealInsulinValidation.carbsError(""))
        assertNotNull(MealInsulinValidation.carbsError("   "))
        assertNotNull(MealInsulinValidation.carbsError("0"))
        assertNotNull(MealInsulinValidation.carbsError("-5"))
        assertNotNull(MealInsulinValidation.carbsError("abc"))
        assertNull(MealInsulinValidation.carbsError("75"))
        assertNull(MealInsulinValidation.carbsError("62,5"))
    }

    @Test
    fun `zero or negative ICR is rejected`() {
        assertInvalid(75.0, 0.0)
        assertInvalid(75.0, -10.0)
        assertInvalid(75.0, null)
        assertNotNull(MealInsulinValidation.icrError("0"))
        assertNotNull(MealInsulinValidation.icrError("-10"))
        assertNotNull(MealInsulinValidation.icrError(""))
        assertNull(MealInsulinValidation.icrError("10"))
    }

    @Test
    fun `ICR outside the configured range is rejected`() {
        assertNotNull(MealInsulinValidation.icrError((MealInsulinConfig.ICR_MAX_GRAMS_PER_UNIT + 1).toString()))
        assertNotNull(MealInsulinValidation.icrError("0.5"))
        assertNotNull(MealInsulinValidation.icrError("10.25"))
    }

    @Test
    fun `icr label reads as grams per unit`() {
        assertEquals("1 unit for every 10 g of carbs", icrLabel(10.0))
        assertEquals("1 unit for every 12.5 g of carbs", icrLabel(12.5))
    }
}

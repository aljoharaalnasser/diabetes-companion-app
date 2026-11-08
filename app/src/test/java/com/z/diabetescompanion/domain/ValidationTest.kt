package com.z.diabetescompanion.domain

import com.z.diabetescompanion.ui.auth.AuthValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {

    @Test
    fun `email validation`() {
        assertNotNull(AuthValidation.emailError(""))
        assertNotNull(AuthValidation.emailError("not-an-email"))
        assertNotNull(AuthValidation.emailError("a@b"))
        assertNull(AuthValidation.emailError("zarrar@crewlogix.com"))
        assertNull(AuthValidation.emailError("  first.last+tag@sub.example.co.uk "))
    }

    @Test
    fun `new password needs 8 chars with a letter and a digit`() {
        assertNotNull(AuthValidation.newPasswordError(""))
        assertNotNull(AuthValidation.newPasswordError("short1"))
        assertNotNull(AuthValidation.newPasswordError("lettersonly"))
        assertNotNull(AuthValidation.newPasswordError("12345678"))
        assertNull(AuthValidation.newPasswordError("Testpass123"))
    }

    @Test
    fun `password strength scales`() {
        assertEquals(0, AuthValidation.passwordStrength(""))
        assertEquals(1, AuthValidation.passwordStrength("abcdefgh"))
        assertEquals(2, AuthValidation.passwordStrength("abcdefg1"))
        assertEquals(4, AuthValidation.passwordStrength("Abcdefghijk1!"))
    }

    @Test
    fun `medication validation`() {
        assertNotNull(MedicationValidation.nameError(""))
        assertNotNull(MedicationValidation.nameError("M"))
        assertNull(MedicationValidation.nameError("Metformin"))
        assertNotNull(MedicationValidation.doseError(""))
        assertNotNull(MedicationValidation.doseError("0"))
        assertNotNull(MedicationValidation.doseError("-5"))
        assertNull(MedicationValidation.doseError("2,5"))
    }

    @Test
    fun `threshold ordering rule`() {
        assertNull(ThresholdValidation.orderingError(54, 70, 180, 250))
        assertNotNull(ThresholdValidation.orderingError(70, 70, 180, 250))
        assertNotNull(ThresholdValidation.orderingError(54, 180, 180, 250))
        assertNotNull(ThresholdValidation.orderingError(54, 70, 250, 250))
        assertNotNull(ThresholdValidation.fieldError("abc"))
        assertNotNull(ThresholdValidation.fieldError("5"))
        assertNull(ThresholdValidation.fieldError("120"))
    }
}

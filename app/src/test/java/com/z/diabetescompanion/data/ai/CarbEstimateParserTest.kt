package com.z.diabetescompanion.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbEstimateParserTest {

    private fun estimate(raw: String): CarbParseResult.Estimate {
        val result = CarbEstimateParser.parse(raw)
        assertTrue("expected Estimate but was $result", result is CarbParseResult.Estimate)
        return result as CarbParseResult.Estimate
    }

    private fun assertInvalid(raw: String?) {
        val result = CarbEstimateParser.parse(raw)
        assertTrue("expected Invalid for <$raw> but was $result", result is CarbParseResult.Invalid)
    }

    @Test
    fun `valid JSON is parsed into items and a total`() {
        val parsed = estimate(
            """
            {"items":[{"name":"Chapati","assumedPortion":"2 medium","carbsGrams":30},
                      {"name":"Chicken curry","assumedPortion":"1 bowl","carbsGrams":12.5}],
             "totalCarbsGrams":42.5,"clarifyingQuestion":null}
            """.trimIndent()
        ).estimate
        assertEquals(2, parsed.items.size)
        assertEquals("Chapati", parsed.items[0].name)
        assertEquals("2 medium", parsed.items[0].assumedPortion)
        assertEquals(30.0, parsed.items[0].carbsGrams, 0.0)
        assertEquals(42.5, parsed.totalCarbsGrams, 0.0)
        assertNull(parsed.clarifyingQuestion)
    }

    @Test
    fun `JSON wrapped in a code fence is accepted`() {
        val parsed = estimate(
            "```json\n{\"items\":[{\"name\":\"Rice\",\"assumedPortion\":\"1 cup\",\"carbsGrams\":45}],\"totalCarbsGrams\":45,\"clarifyingQuestion\":null}\n```"
        )
        assertEquals(45.0, parsed.estimate.totalCarbsGrams, 0.0)
    }

    @Test
    fun `a clarifying question is returned instead of an estimate`() {
        val result = CarbEstimateParser.parse(
            """{"items":[],"totalCarbsGrams":0,"clarifyingQuestion":"Was it a small, medium or large plate?"}"""
        )
        assertTrue(result is CarbParseResult.Clarify)
        assertEquals("Was it a small, medium or large plate?", (result as CarbParseResult.Clarify).question)
    }

    @Test
    fun `invalid or incomplete JSON is rejected`() {
        assertInvalid(null)
        assertInvalid("")
        assertInvalid("Sorry, I can't help with that.")
        assertInvalid("""{"items":[{"name":"Rice","carbsGrams":45}""")
        assertInvalid("""{"items":[],"totalCarbsGrams":0,"clarifyingQuestion":null}""")
        assertInvalid("""{"totalCarbsGrams":45,"clarifyingQuestion":null}""")
        assertInvalid("""{"items":[{"name":"Rice","assumedPortion":"1 cup"}],"totalCarbsGrams":45,"clarifyingQuestion":null}""")
        assertInvalid("""{"items":[{"name":"Rice","assumedPortion":"1 cup","carbsGrams":-5}],"totalCarbsGrams":45,"clarifyingQuestion":null}""")
        assertInvalid("""{"items":[{"name":"Rice","assumedPortion":"1 cup","carbsGrams":45}],"totalCarbsGrams":0,"clarifyingQuestion":null}""")
        assertInvalid("""{"items":[{"name":"","assumedPortion":"1 cup","carbsGrams":45}],"totalCarbsGrams":45,"clarifyingQuestion":null}""")
    }

    @Test
    fun `text that states an insulin amount is rejected`() {
        assertInvalid("""{"items":[],"totalCarbsGrams":0,"clarifyingQuestion":"Did you already take 4 units?"}""")
        assertInvalid("""{"items":[{"name":"Rice (take 6 units)","assumedPortion":"1 cup","carbsGrams":45}],"totalCarbsGrams":45,"clarifyingQuestion":null}""")
    }
}

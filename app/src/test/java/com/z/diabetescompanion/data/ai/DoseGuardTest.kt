package com.z.diabetescompanion.data.ai

import com.z.diabetescompanion.domain.DoseGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class DoseGuardTest {

    /** Scripted client: returns [reply] and records exactly what would be sent to the provider. */
    private class FakeAiClient(private val reply: AiResult) : AiClient {
        override val isConfigured = true
        override val modelName = "fake"
        val sent = mutableListOf<List<AiMessage>>()
        var lastJsonOutput = false

        override suspend fun complete(
            messages: List<AiMessage>,
            maxTokens: Int,
            reasoningEffort: String,
            jsonOutput: Boolean,
        ): AiResult {
            sent += messages
            lastJsonOutput = jsonOutput
            return reply
        }
    }

    /** The fake never suspends, so this runs the call to completion without kotlinx.coroutines. */
    private fun <T> runSync(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return outcome!!.getOrThrow()
    }

    @Test
    fun `text with an insulin amount is detected`() {
        listOf(
            "You should take 6 units of insulin.",
            "Around 4-6 units would cover that.",
            "Try 7.5u before the meal.",
            "Inject five units.",
            "A bolus of 3 is typical.",
            "Give half a unit more.",
            "Take 2 IU with breakfast.",
            "Take 6 I.U. now",
            "Take 6 I.U",
            "Use a 6-unit bolus.",
            "That is a 7.5-unit dose.",
            "Your insulin dose would be 6.",
            "Insulin dose: 6",
            "Your dose is 7 for this meal.",
            "Give yourself 4 of insulin.",
            "75 g / 10 = 7.5, so you'd need 7.5 of rapid insulin.",
            "About 5 rapid-acting insulin before eating.",
        ).forEach { assertTrue(it, DoseGuard.containsInsulinAmount(it)) }
    }

    @Test
    fun `ordinary guidance and carb text is allowed`() {
        listOf(
            "A cup of cooked rice has roughly 45 g of carbohydrate.",
            "Walking for 20 minutes after meals can help.",
            "Your reading of 180 mg/dL is above your target range.",
            "Take 2 slices of bread instead of 3.",
            "The app calculates the meal dose after you confirm the carbs.",
            "People with type 1 diabetes need insulin every day.",
            "Type 2 diabetes is sometimes treated with insulin.",
            "Your doctor may adjust the dose for type 2 diabetes over time.",
            "Insulin works best when you take it 15 minutes before eating.",
            "There are 2 main kinds of insulin: rapid-acting and long-acting.",
        ).forEach { assertFalse(it, DoseGuard.containsInsulinAmount(it)) }
    }

    @Test
    fun `AI chat text with a dose is never shown as a dose`() {
        val client = FakeAiClient(AiResult.Success("For 60 g of carbs, take 6 units of rapid insulin.", "fake"))
        val result = runSync { ChatbotService(client).reply(emptyList(), "I had a big lunch, what now?") }

        assertTrue(result is AiResult.Success)
        val text = (result as AiResult.Success).text
        assertEquals(AiPrompts.DOSE_BOUNDARY_REPLY, text)
        assertFalse(DoseGuard.containsInsulinAmount(text))
    }

    @Test
    fun `safe AI chat text is passed through unchanged`() {
        val client = FakeAiClient(AiResult.Success("Fibre slows how quickly carbs raise glucose.", "fake"))
        val result = runSync { ChatbotService(client).reply(emptyList(), "Why does fibre matter?") }
        assertEquals("Fibre slows how quickly carbs raise glucose.", (result as AiResult.Success).text)
    }

    @Test
    fun `dose questions in free chat are answered on-device and point to the carb card`() {
        val client = FakeAiClient(AiResult.Success("should not be called", "fake"))
        val result = runSync { ChatbotService(client).reply(emptyList(), "How much insulin should I take for this?") }
        assertEquals(AiPrompts.DOSE_BOUNDARY_REPLY, (result as AiResult.Success).text)
        assertTrue(AiPrompts.DOSE_BOUNDARY_REPLY.contains("Count carbs for a meal"))
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun `carb requests send only the meal text, in JSON mode`() {
        val client = FakeAiClient(
            AiResult.Success("""{"items":[{"name":"Rice","assumedPortion":"1 cup","carbsGrams":45}],"totalCarbsGrams":45,"clarifyingQuestion":null}""", "fake")
        )
        val result = runSync { ChatbotService(client).estimateCarbs("1 cup of rice") }

        assertTrue(result is CarbEstimateResult.Estimate)
        assertTrue(client.lastJsonOutput)
        val messages = client.sent.single()
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals(AiPrompts.CARB_ESTIMATE_SYSTEM, messages[0].content)
        assertEquals("MEAL:\n1 cup of rice\n", messages[1].content)
    }

    @Test
    fun `follow-up carb requests keep every earlier answer`() {
        val client = FakeAiClient(AiResult.Success("""{"items":[],"totalCarbsGrams":0,"clarifyingQuestion":"Brown or white rice?"}""", "fake"))
        val answered = listOf("Small, medium or large plate?" to "large", "Any bread?" to "two chapatis")
        val result = runSync { ChatbotService(client).estimateCarbs("rice and curry", answered) }

        assertTrue(result is CarbEstimateResult.Clarify)
        val sent = client.sent.single()[1].content
        assertTrue(sent.contains("MEAL:\nrice and curry"))
        assertTrue(sent.contains("YOU ASKED: Small, medium or large plate?\nANSWER: large"))
        assertTrue(sent.contains("YOU ASKED: Any bread?\nANSWER: two chapatis"))
    }

    @Test
    fun `failed or invalid carb responses ask the patient to retry`() {
        val broken = runSync { ChatbotService(FakeAiClient(AiResult.Success("not json", "fake"))).estimateCarbs("rice") }
        assertTrue(broken is CarbEstimateResult.Failed)
        assertEquals(ChatbotService.CARB_RETRY_MESSAGE, (broken as CarbEstimateResult.Failed).message)
        assertTrue(broken.retryable)

        val offline = runSync { ChatbotService(FakeAiClient(AiResult.Error("Couldn't reach the AI service."))).estimateCarbs("rice") }
        assertTrue(offline is CarbEstimateResult.Failed)
    }
}

package com.z.diabetescompanion

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.z.diabetescompanion.data.ai.AiMessage
import com.z.diabetescompanion.data.ai.AiResult
import com.z.diabetescompanion.data.ai.OpenAiClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** HTTPS mock server only. No real OpenAI key or network request is used. */
@RunWith(AndroidJUnit4::class)
class Sprint3AiMockIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiClient

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val http = OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/v1/chat/completions")).build())
            }
            .build()
        client = OpenAiClient("emulator-only-dummy-key", "mock-model")
        // Test-only injection keeps the production endpoint and constructor unchanged.
        OpenAiClient::class.java.getDeclaredField("http").apply { isAccessible = true }.set(client, http)
    }

    @After fun close() { if (::server.isInitialized) server.shutdown() }

    @Test fun IT_REG_101_successSerializesRequestAndParsesResponse() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Fictional education response"}}]}"""))
            val result = client.complete(listOf(AiMessage.user("Explain glucose records")), 600, "low", true)
            assertTrue(result is AiResult.Success)
            assertEquals("Fictional education response", (result as AiResult.Success).text)
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val body = JSONObject(request.body.readUtf8())
            assertEquals("mock-model", body.getString("model"))
            assertEquals(600, body.getInt("max_completion_tokens"))
            assertFalse(body.has("max_tokens"))
            assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        }
    }

    @Test fun IT_REG_102_rateLimitProducesRetryableError() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"Mock rate limit"}}"""))
            val result = client.complete(listOf(AiMessage.user("Fictional question")), 600, "low", false)
            assertTrue(result is AiResult.Error)
            assertTrue((result as AiResult.Error).retryable)
            assertTrue(result.message.contains("rate limited"))
        }
    }

    @Test fun IT_REG_103_rejectedKeyProducesNonretryableError() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Mock rejection"}}"""))
            val result = client.complete(listOf(AiMessage.user("Fictional question")), 600, "low", false)
            assertTrue(result is AiResult.Error)
            assertFalse((result as AiResult.Error).retryable)
            assertTrue(result.message.contains("key was rejected"))
        }
    }

    @Test fun IT_REG_104_malformedResponseProducesReadableError() {
        runBlocking {
            server.enqueue(MockResponse().setBody("not JSON"))
            val result = client.complete(listOf(AiMessage.user("Fictional question")), 600, "low", false)
            assertTrue(result is AiResult.Error)
            assertTrue((result as AiResult.Error).message.contains("unexpected"))
        }
    }
}

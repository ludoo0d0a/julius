package fr.geoking.julius.agents

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import fr.geoking.julius.shared.platform.getCurrentTimeMillis

class ApiFreeLLMAgentRateLimitTest {

    private fun createMockClient(responseBody: String): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Test
    fun testApiFreeLLMAgent_RateLimitEnforcedShortDelay(): Unit = runBlocking {
        val client = createMockClient("""{"success": true, "response": "Hello"}""")
        val delayMs = 100L
        val agent = ApiFreeLLMAgent(client, apiKey = "test-key", rateLimitDelayMs = delayMs)

        // First call sets lastRequestStartTime
        val start1 = getCurrentTimeMillis()
        agent.process("First")

        // Immediate second call should wait inside process()
        val start2Attempt = getCurrentTimeMillis()
        agent.process("Second")
        val end2 = getCurrentTimeMillis()

        // The delay is applied BEFORE the request starts in the second process call.
        // lastRequestStartTime is updated AFTER the delay.
        // So the second request should end at least delayMs after the first request started.
        // Actually, more precisely, the second request should start at least delayMs after the first.

        // Since we can't easily measure when the second request *starts* inside process(),
        // we measure when it *ends*.
        val timeBetweenFirstStartAndSecondEnd = end2 - start1
        assertTrue(timeBetweenFirstStartAndSecondEnd >= delayMs, "Second request should have completed at least $delayMs ms after first started, took $timeBetweenFirstStartAndSecondEnd ms")
    }

    @Test
    fun testApiFreeLLMAgent_RequestSerialization(): Unit = runBlocking {
        val client = createMockClient("""{"success": true, "response": "Hello"}""")
        val agent = ApiFreeLLMAgent(client, apiKey = "test-key", rateLimitDelayMs = 0)

        val deferred1 = async { agent.process("Parallel 1") }
        val deferred2 = async { agent.process("Parallel 2") }

        deferred1.await()
        deferred2.await()
    }
}

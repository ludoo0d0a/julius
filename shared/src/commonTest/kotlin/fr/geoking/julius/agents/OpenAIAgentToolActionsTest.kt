package fr.geoking.julius.agents

import fr.geoking.julius.shared.action.ActionType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAIAgentToolActionsTest {
    private fun createMockClient(): HttpClient {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/chat/completions") -> {
                    respond(
                        content = """
                            {
                              "choices": [
                                {
                                  "message": {
                                    "role": "assistant",
                                    "content": "I can help with nearby stations.",
                                    "tool_calls": [
                                      {
                                        "id": "tc-battery",
                                        "type": "function",
                                        "function": {
                                          "name": "get_battery_level",
                                          "arguments": "{}"
                                        }
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }

                request.url.encodedPath.endsWith("/audio/speech") -> {
                    respond(
                        content = byteArrayOf(1, 2, 3),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "audio/mpeg")
                    )
                }

                else -> error("Unexpected URL in test: ${request.url}")
            }
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Test
    fun testOpenAiBatteryToolCallsToActionTypes(): Unit = runBlocking {
        val agent = OpenAIAgent(
            client = createMockClient(),
            apiKey = "test-key",
            toolsEnabled = true
        )

        val response = agent.process("What's my battery level?")
        val toolCalls = response.toolCalls

        assertNotNull(toolCalls)
        assertEquals(1, toolCalls.size)
        assertTrue(toolCalls.any { it.action.type == ActionType.GET_BATTERY_LEVEL })
    }
}

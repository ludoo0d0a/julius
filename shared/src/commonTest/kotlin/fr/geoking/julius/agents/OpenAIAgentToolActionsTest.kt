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
                                        "id": "tc-electric",
                                        "type": "function",
                                        "function": {
                                          "name": "find_electric_stations_nearby",
                                          "arguments": "{}"
                                        }
                                      },
                                      {
                                        "id": "tc-hybrid",
                                        "type": "function",
                                        "function": {
                                          "name": "find_hybrid_stations_nearby",
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
    fun testOpenAiMapsElectricAndHybridToolCallsToActionTypes(): Unit = runBlocking {
        val agent = OpenAIAgent(
            client = createMockClient(),
            apiKey = "test-key",
            toolsEnabled = true
        )

        val response = agent.process("Find charging and hybrid stations nearby")
        val toolCalls = response.toolCalls

        assertNotNull(toolCalls)
        assertEquals(2, toolCalls.size)
        assertTrue(toolCalls.any { it.action.type == ActionType.FIND_ELECTRIC_STATIONS })
        assertTrue(toolCalls.any { it.action.type == ActionType.FIND_HYBRID_STATIONS })
    }
}

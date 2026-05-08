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

class GeminiAgentToolActionsTest {
    private fun createMockClient(): HttpClient {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith(":generateContent") -> {
                    respond(
                        content = """
                            {
                              "candidates": [
                                {
                                  "content": {
                                    "parts": [
                                      {
                                        "functionCall": {
                                          "name": "get_battery_level",
                                          "args": {}
                                        }
                                      },
                                      {
                                        "functionCall": {
                                          "name": "get_volume_levels",
                                          "args": {}
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
    fun testGeminiBatteryAndVolumeToolCallsToActionTypes(): Unit = runBlocking {
        val agent = GeminiAgent(
            client = createMockClient(),
            apiKey = "test-key",
            toolsEnabled = true
        )

        val response = agent.process("What's my battery and volume?")
        val toolCalls = response.toolCalls

        assertNotNull(toolCalls)
        assertEquals(2, toolCalls.size)
        assertTrue(toolCalls.any { it.action.type == ActionType.GET_BATTERY_LEVEL })
        assertTrue(toolCalls.any { it.action.type == ActionType.GET_VOLUME_LEVEL })
    }
}

package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DeepgramRealApiTests : RealApiTestBase() {

    @Test
    fun testDeepgramAgent_ChatCompletions() {
        runBlocking {
            withApiKey("deepgram.key", "Deepgram") { apiKey ->
                withAgent(createAgent = { client -> DeepgramAgent(client, apiKey) }) { agent ->
                testAgent(
                    agent = agent,
                    agentName = "Deepgram",
                    additionalAssertions = { response ->
                        assertTrue(
                            !response.text.contains("Error connecting"),
                            "Should not return connection error: ${response.text}"
                        )
                        assertTrue(
                            !response.text.contains("API key is required"),
                            "Should not return API key error"
                        )
                    }
                )
                }
            } ?: Unit
        }
    }

    @Test
    fun testDeepgramAgent_EmptyKey() {
        runBlocking {
        try {
            withAgent(createAgent = { client -> DeepgramAgent(client, "") }) { agent ->
                agent.process("Test prompt")
            }
            throw AssertionError("Expected NetworkException was not thrown.")
        } catch (e: NetworkException) {
            assertTrue(
                e.message?.contains("API key", ignoreCase = true) == true,
                "Exception message should indicate API key is required: ${e.message}"
            )
            println("✅ Deepgram empty key test passed")
        }
        }
    }
}

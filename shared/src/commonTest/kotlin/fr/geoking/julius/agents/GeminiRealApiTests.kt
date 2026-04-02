package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class GeminiRealApiTests : RealApiTestBase() {

    @Test
    fun testListModels() {
        runBlocking {
            withApiKey("GEMINI_KEY", "Gemini") { apiKey ->
                withGeminiAgent(apiKey) { agent ->
                    testListModels(agent, "Gemini")
                }
            } ?: Unit
        }
    }

    @Test
    fun testGeminiAgent_SimplePrompt() {
        runBlocking {
            withApiKey("GEMINI_KEY", "Gemini") { apiKey ->
                withGeminiAgent(apiKey) { agent ->
                    testAgent(
                        agent = agent,
                        agentName = "Gemini",
                        additionalAssertions = { response ->
                            assertTrue(
                                !response.text.startsWith("Error connecting"),
                                "Should not return connection error: ${response.text}"
                            )
                        }
                    )
                }
            } ?: Unit
        }
    }

    @Test
    fun testGeminiAgent_EmptyKey() {
        runBlocking {
        try {
            withGeminiAgent("") { agent ->
                agent.process("Test prompt")
            }
            throw AssertionError("Expected NetworkException was not thrown.")
        } catch (e: NetworkException) {
            assertTrue(
                e.message?.contains("API key", ignoreCase = true) == true,
                "Exception message should indicate API key is required: ${e.message}"
            )
            println("✅ Gemini empty key test passed")
        }
        }
    }
}

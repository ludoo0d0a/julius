package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class PerplexityRealApiTests : RealApiTestBase() {

    @Test
    fun testPerplexityAgent_SimplePrompt() {
        runBlocking {
            withApiKey("PERPLEXITY_KEY", "Perplexity") { apiKey ->
                withPerplexityAgent(apiKey) { agent ->
                    testAgent(
                        agent = agent,
                        agentName = "Perplexity",
                        additionalAssertions = { response ->
                            assertTrue(response.text != "No response", "Should not return 'No response' error")
                        }
                    )
                }
            } ?: Unit
        }
    }

    @Test
    fun testPerplexityAgent_EmptyKey() {
        runBlocking {
        try {
            withPerplexityAgent("", model = "llama-3.1-sonar-small-128k-online") { agent ->
                agent.process("Test prompt")
            }
            throw AssertionError("Expected NetworkException was not thrown.")
        } catch (e: NetworkException) {
            assertTrue(
                e.message?.contains("API key", ignoreCase = true) == true,
                "Exception message should indicate API key is required: ${e.message}"
            )
            println("✅ Perplexity empty key test passed")
        }
        }
    }
}

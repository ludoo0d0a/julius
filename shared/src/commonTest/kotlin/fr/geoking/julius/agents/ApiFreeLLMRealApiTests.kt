package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class ApiFreeLLMRealApiTests : RealApiTestBase() {

    @Test
    fun testApiFreeLLMAgent_SimplePrompt() {
        runBlocking {
            withApiKey("APIFREELLM_KEY", "ApiFreeLLM") { apiKey ->
                withAgent(createAgent = { client -> ApiFreeLLMAgent(client, apiKey = apiKey) }) { agent ->
                    testAgent(agent = agent, agentName = "ApiFreeLLM")
                }
            } ?: Unit
        }
    }

    @Test
    fun testApiFreeLLMAgent_EmptyKey() {
        runBlocking {
            try {
                withAgent(createAgent = { client -> ApiFreeLLMAgent(client, apiKey = "") }) { agent ->
                    agent.process("Test prompt")
                }
                throw AssertionError("Expected NetworkException was not thrown.")
            } catch (e: NetworkException) {
                assertTrue(
                    e.message?.contains("API key", ignoreCase = true) == true,
                    "Exception should indicate API key is required: ${e.message}"
                )
            }
        }
    }
}

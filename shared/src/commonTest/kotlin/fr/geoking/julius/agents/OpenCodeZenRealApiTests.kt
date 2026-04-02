package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenCodeZenRealApiTests : RealApiTestBase() {

    @Test
    fun testOpenCodeZenAgent_SimplePrompt() {
        runBlocking {
            withApiKey("OPENCODE_ZEN_KEY", "OpenCodeZen") { apiKey ->
                val model = getApiKey("opencodezen.model", "minimax-m2.5-free")
                withAgent(createAgent = { client -> OpenCodeZenAgent(client, apiKey = apiKey, model = model) }) { agent ->
                    testAgent(agent = agent, agentName = "OpenCodeZen")
                }
            } ?: Unit
        }
    }

    @Test
    fun testOpenCodeZenAgent_EmptyKey() {
        runBlocking {
            try {
                withAgent(createAgent = { client -> OpenCodeZenAgent(client, apiKey = "") }) { agent ->
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

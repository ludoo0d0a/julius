package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class CompletionsMeRealApiTests : RealApiTestBase() {

    @Test
    fun testCompletionsMeAgent_SimplePrompt() {
        runBlocking {
            withApiKey("COMPLETIONS_ME_KEY", "CompletionsMe") { apiKey ->
                val model = getApiKey("completionsme.model", "claude-sonnet-4.5")
                withAgent(createAgent = { client -> CompletionsMeAgent(client, apiKey = apiKey, model = model) }) { agent ->
                    testAgent(agent = agent, agentName = "CompletionsMe")
                }
            } ?: Unit
        }
    }

    @Test
    fun testCompletionsMeAgent_EmptyKey() {
        runBlocking {
            try {
                withAgent(createAgent = { client -> CompletionsMeAgent(client, apiKey = "") }) { agent ->
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

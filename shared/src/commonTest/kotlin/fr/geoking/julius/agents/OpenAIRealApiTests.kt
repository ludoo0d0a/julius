package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenAIRealApiTests : RealApiTestBase() {

    @Test
    fun testOpenAIAgent_SimplePrompt() {
        runBlocking {
            withApiKey("OPENAI_KEY", "OpenAI") { apiKey ->
                withOpenAIAgent(apiKey) { agent ->
                    testAgent(
                        agent = agent,
                        agentName = "OpenAI",
                        additionalAssertions = { response ->
                            if (response.audio != null) {
                                assertTrue(response.audio.isNotEmpty(), "Audio bytes should not be empty if present")
                            }
                        }
                    )
                }
            } ?: Unit
        }
    }

    @Test
    fun testOpenAIAgent_EmptyKey() {
        runBlocking {
            try {
                withOpenAIAgent("") { agent ->
                    agent.process("Test prompt")
                }
                throw AssertionError("Expected NetworkException was not thrown.")
            } catch (e: NetworkException) {
                assertTrue(
                    e.message?.contains("API key", ignoreCase = true) == true,
                    "Exception message should indicate API key is required: ${e.message}"
                )
                println("✅ OpenAI empty key test passed")
            }
        }
    }

    @Test
    fun testOpenAIAgent_CustomModel() {
        runBlocking {
            withApiKey("OPENAI_KEY", "OpenAI Custom Model") { apiKey ->
                withHttpClient { client ->
                    val agent = OpenAIAgent(client, apiKey = apiKey, model = "gpt-4o-mini")
                    testAgent(
                        agent = agent,
                        agentName = "OpenAI (gpt-4o-mini)",
                        prompt = "Hello, what model are you?"
                    )
                }
            } ?: Unit
        }
    }
}

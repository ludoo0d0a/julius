package fr.geoking.julius.agents

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class GeminiRealApiTests : RealApiTestBase() {

    @Test
    fun testListModels() = runBlocking {
        withApiKey("gemini.key", "Gemini") { apiKey ->
            withGeminiAgent(apiKey) { agent ->
                val modelsJson = agent.listModels()
                println("✅ Gemini ListModels Response:")
                println(modelsJson)
                assertTrue(modelsJson.isNotEmpty(), "ListModels response should not be empty")
            }
        }
    }

    @Test
    fun testGeminiAgent_SimplePrompt() = runBlocking {
        withApiKey("gemini.key", "Gemini") { apiKey ->
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
        }
    }
}

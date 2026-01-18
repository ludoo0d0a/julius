package com.antigravity.voiceai.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class GenkitRealApiTests : RealApiTestBase() {

    @Test
    fun testGenkitAgent_SimplePrompt() = runBlocking {
        val endpoint = getApiKey("genkit.endpoint")
        if (endpoint.isEmpty()) {
            println("⚠️  Skipping Genkit test - no endpoint provided")
            return@runBlocking
        }

        val apiKey = getApiKey("genkit.key")
        val client = createHttpClient()
        val agent = GenkitAgent(client, endpoint = endpoint, apiKey = apiKey)

        val testPrompt = defaultPrompt()

        try {
            val response = agent.process(testPrompt)
            println("✅ Genkit Response: ${response.text.take(150)}...")

            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
            assertTrue(
                !response.text.startsWith("Error connecting"),
                "Should not return connection error: ${response.text}"
            )
        } catch (e: Exception) {
            println("❌ Genkit test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.close()
        }
    }

    @Test
    fun testGenkitAgent_EmptyEndpoint() = runBlocking {
        val client = createHttpClient()
        val agent = GenkitAgent(client, endpoint = "", apiKey = "ignored")

        val response = agent.process("Test prompt")

        assertTrue(
            response.text.contains("endpoint", ignoreCase = true),
            "Should return endpoint required message"
        )
        println("✅ Genkit empty endpoint test passed")

        client.close()
    }
}

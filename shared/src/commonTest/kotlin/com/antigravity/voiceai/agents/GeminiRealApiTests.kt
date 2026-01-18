package com.antigravity.voiceai.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class GeminiRealApiTests : RealApiTestBase() {

    @Test
    fun testGeminiAgent_SimplePrompt() = runBlocking {
        val apiKey = getApiKey("gemini.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Gemini test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val agent = GeminiAgent(client, apiKey)

        val testPrompt = defaultPrompt()

        try {
            val response = agent.process(testPrompt)
            println("✅ Gemini Response: ${response.text.take(150)}...")

            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
            assertTrue(
                !response.text.startsWith("Error connecting"),
                "Should not return connection error: ${response.text}"
            )
        } catch (e: Exception) {
            println("❌ Gemini test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.close()
        }
    }
}

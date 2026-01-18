package com.antigravity.voiceai.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class OpenAIRealApiTests : RealApiTestBase() {

    @Test
    fun testOpenAIAgent_SimplePrompt() = runBlocking {
        val apiKey = getApiKey("openai.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping OpenAI test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val agent = OpenAIAgent(client, apiKey)

        val testPrompt = defaultPrompt()

        try {
            val response = agent.process(testPrompt)
            println("✅ OpenAI Response: ${response.text.take(150)}...")

            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
            assertTrue(!response.text.startsWith("Error"), "Should not return error message: ${response.text}")

            // Audio may be null if TTS fails, but text should always be present
            if (response.audio != null) {
                assertTrue(response.audio.isNotEmpty(), "Audio bytes should not be empty if present")
            }
        } catch (e: Exception) {
            println("❌ OpenAI test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.close()
        }
    }
}

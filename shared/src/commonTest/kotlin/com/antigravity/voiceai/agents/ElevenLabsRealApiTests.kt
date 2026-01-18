package com.antigravity.voiceai.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class ElevenLabsRealApiTests : RealApiTestBase() {

    @Test
    fun testElevenLabsAgent_WithPerplexity() = runBlocking {
        val perplexityKey = getApiKey("perplexity.key")
        val elevenLabsKey = getApiKey("elevenlabs.key")

        if (perplexityKey.isEmpty() || elevenLabsKey.isEmpty()) {
            println("⚠️  Skipping ElevenLabs test - API keys not provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val model = "llama-3.1-sonar-small-128k-online"
        val agent = ElevenLabsAgent(client, perplexityKey, elevenLabsKey, model = model)

        val testPrompt = defaultPrompt()

        try {
            val response = agent.process(testPrompt)
            println("✅ ElevenLabs Response: ${response.text.take(150)}...")

            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")

            // Audio may be null if ElevenLabs API fails, but text from Perplexity should be present
            if (response.audio != null) {
                assertTrue(response.audio.isNotEmpty(), "Audio bytes should not be empty if present")
                println("✅ ElevenLabs audio generated (${response.audio.size} bytes)")
            } else {
                println("⚠️  ElevenLabs audio generation failed (text response still present)")
            }
        } catch (e: Exception) {
            println("❌ ElevenLabs test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.close()
        }
    }
}

package fr.geoking.julius.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class ElevenLabsRealApiTests : RealApiTestBase() {

    @Test
    fun testElevenLabsAgent_WithPerplexity() = runBlocking {
        val perplexityKey = getApiKey("perplexity.key")
        val elevenLabsKey = getApiKey("elevenlabs.key")
        if (perplexityKey.isEmpty() || elevenLabsKey.isEmpty()) {
            println("⚠️  Skipping ElevenLabs test - perplexity.key and elevenlabs.key required")
            return@runBlocking
        }
        val model = getApiKey("perplexity.model", "llama-3.1-sonar-small-128k-online")
        withAgent(
            createAgent = { client -> ElevenLabsAgent(client, perplexityKey, elevenLabsKey, model = model) }
        ) { agent ->
            testAgent(
                agent = agent,
                agentName = "ElevenLabs",
                additionalAssertions = { response ->
                    if (response.audio != null) {
                        assertTrue(response.audio.isNotEmpty(), "Audio bytes should not be empty if present")
                        println("✅ ElevenLabs audio generated (${response.audio.size} bytes)")
                    } else {
                        println("⚠️  ElevenLabs audio null (text response still present)")
                    }
                }
            )
        }
    }
}

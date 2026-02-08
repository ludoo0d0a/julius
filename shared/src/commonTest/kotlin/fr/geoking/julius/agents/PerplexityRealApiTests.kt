package fr.geoking.julius.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PerplexityRealApiTests : RealApiTestBase() {

    @Test
    fun testPerplexityAgent_SimplePrompt() = runBlocking {
        val apiKey = getApiKey("perplexity.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Perplexity test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val model = "llama-3.1-sonar-small-128k-online"
        val agent = PerplexityAgent(client, apiKey, model)

        val testPrompt = defaultPrompt()

        try {
            val response = agent.process(testPrompt)
            println("✅ Perplexity Response: ${response.text.take(150)}...")

            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
            assertNotEquals("No response", response.text, "Should not return 'No response' error")
        } catch (e: Exception) {
            println("❌ Perplexity test failed: ${e.message}")
            e.printStackTrace()

            // Check if it's an API key error or actual API issue
            if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                println("⚠️  This looks like an authentication error - check API key")
            }
            throw e
        } finally {
            client.close()
        }
    }
}

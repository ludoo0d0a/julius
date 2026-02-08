package fr.geoking.julius.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class DeepgramRealApiTests : RealApiTestBase() {

    @Test
    fun testDeepgramAgent_ChatCompletions() = runBlocking {
        val apiKey = getApiKey("deepgram.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Deepgram test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val agent = DeepgramAgent(client, apiKey)

        val testPrompt = defaultPrompt()

        try {
            val response = agent.process(testPrompt)
            println("✅ Deepgram Response: ${response.text.take(150)}...")

            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")

            // Check for error messages
            if (response.text.contains("Error connecting")) {
                println("❌ Deepgram API connection error: ${response.text}")
                throw AssertionError("Deepgram API error: ${response.text}")
            }

            if (response.text.contains("API key is required")) {
                println("❌ Deepgram API key error")
                throw AssertionError("Deepgram API key error")
            }
        } catch (e: Exception) {
            println("❌ Deepgram test failed: ${e.message}")
            e.printStackTrace()

            // Deepgram might not have chat/completions endpoint - this could be the issue
            if (e.message?.contains("404") == true || e.message?.contains("Not Found") == true) {
                println("⚠️  Deepgram endpoint might not exist - verify API endpoint")
            }
            throw e
        } finally {
            client.close()
        }
    }

    @Test
    fun testDeepgramAgent_EmptyKey() {
        val client = createHttpClient()
        val agent = DeepgramAgent(client, "")

        try {
            runBlocking {
                agent.process("Test prompt")
            }
            // If it doesn't throw, the test fails
            throw AssertionError("Expected NetworkException was not thrown.")
        } catch (e: fr.geoking.julius.shared.NetworkException) {
            // This is the expected outcome
            assertTrue(
                e.message?.contains("API key", ignoreCase = true) == true,
                "The exception message should indicate an API key is required."
            )
            println("✅ Deepgram empty key test passed")
        } finally {
            client.close()
        }
    }
}

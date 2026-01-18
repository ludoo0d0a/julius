package com.antigravity.voiceai.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class FirebaseAIRealApiTests : RealApiTestBase() {

    @Test
    fun testFirebaseAIAgent_SimplePrompt() = runBlocking {
        val apiKey = getApiKey("firebaseai.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Firebase AI test - no API key provided")
            return@runBlocking
        }

        val model = getApiKey("firebaseai.model", default = "gemini-1.5-flash-latest")
        val client = createHttpClient()
        val agent = FirebaseAIAgent(client, apiKey = apiKey, model = model)

        val testPrompt = defaultPrompt()

        try {
            val response = agent.process(testPrompt)
            println("✅ Firebase AI Response: ${response.text.take(150)}...")

            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
            assertTrue(
                !response.text.startsWith("Error connecting"),
                "Should not return connection error: ${response.text}"
            )
        } catch (e: Exception) {
            println("❌ Firebase AI test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.close()
        }
    }

    @Test
    fun testFirebaseAIAgent_EmptyKey() = runBlocking {
        val client = createHttpClient()
        val agent = FirebaseAIAgent(client, "")

        val response = agent.process("Test prompt")

        assertTrue(
            response.text.contains("key", ignoreCase = true),
            "Should return API key required message"
        )
        println("✅ Firebase AI empty key test passed")

        client.close()
    }
}

package fr.geoking.julius.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class FirebaseAIRealApiTests : RealApiTestBase() {

    @Test
    fun testFirebaseAIAgent_SimplePrompt() = runBlocking {
        val apiKey = getApiKey("FIREBASE_AI_KEY")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Firebase AI test - no FIREBASE_AI_KEY provided")
            return@runBlocking
        }
        val model = getApiKey("FIREBASE_AI_MODEL", "gemini-1.5-flash-latest")
        withAgent(createAgent = { client -> FirebaseAIAgent(client, apiKey = apiKey, model = model) }) { agent ->
            testAgent(
                agent = agent,
                agentName = "Firebase AI",
                additionalAssertions = { response ->
                    assertTrue(
                        !response.text.startsWith("Error connecting"),
                        "Should not return connection error: ${response.text}"
                    )
                }
            )
        }
    }

    @Test
    fun testFirebaseAIAgent_EmptyKey() = runBlocking {
        withAgent(createAgent = { client -> FirebaseAIAgent(client, "") }) { agent ->
            val response = agent.process("Test prompt")
            assertTrue(
                response.text.contains("key", ignoreCase = true),
                "Should return API key required message: ${response.text}"
            )
            println("✅ Firebase AI empty key test passed")
        }
    }
}

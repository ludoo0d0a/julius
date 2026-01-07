package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals

/**
 * Integration tests that actually call the real APIs.
 * These tests require API keys to be set in the environment or local.properties.
 * 
 * To run these tests:
 * 1. Ensure API keys are available (they will be empty string if not set)
 * 2. Tests will pass if API keys are invalid (they'll get error messages)
 * 3. Tests verify that the API calls are correctly structured
 */
class RealApiTests {

    private fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    // Helper to read API keys from local.properties or system properties
    private fun getApiKey(propertyName: String, default: String = ""): String {
        // Try system property first
        System.getProperty(propertyName)?.let { return it }
        
        // Try to read from local.properties
        try {
            val localPropsFile = java.io.File("local.properties")
            if (localPropsFile.exists()) {
                val props = java.util.Properties()
                localPropsFile.inputStream().use { props.load(it) }
                props.getProperty(propertyName)?.let { return it }
            }
        } catch (e: Exception) {
            // Ignore if file doesn't exist or can't be read
        }
        
        return default
    }

    @Test
    fun testOpenAIAgent_SimplePrompt() = runBlocking {
        val apiKey = getApiKey("openai.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping OpenAI test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val agent = OpenAIAgent(client, apiKey)
        
        val testPrompt = "donne la météo de demain"
        
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

    @Test
    fun testGeminiAgent_SimplePrompt() = runBlocking {
        val apiKey = getApiKey("gemini.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Gemini test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val agent = GeminiAgent(client, apiKey)
        
        val testPrompt = "donne la météo de demain"
        
        try {
            val response = agent.process(testPrompt)
            println("✅ Gemini Response: ${response.text.take(150)}...")
            
            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
            assertTrue(!response.text.startsWith("Error connecting"), 
                "Should not return connection error: ${response.text}")
        } catch (e: Exception) {
            println("❌ Gemini test failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.close()
        }
    }

    @Test
    fun testNativeAgent_Perplexity() = runBlocking {
        val apiKey = getApiKey("perplexity.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Native/Perplexity test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val model = "llama-3.1-sonar-small-128k-online"
        val agent = NativeAgent(client, apiKey, model)
        
        val testPrompt = "donne la météo de demain"
        
        try {
            val response = agent.process(testPrompt)
            println("✅ Native/Perplexity Response: ${response.text.take(150)}...")
            
            assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
            assertNotEquals("No response", response.text, "Should not return 'No response' error")
        } catch (e: Exception) {
            println("❌ Native/Perplexity test failed: ${e.message}")
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
        val agent = ElevenLabsAgent(client, perplexityKey, elevenLabsKey, model)
        
        val testPrompt = "donne la météo de demain"
        
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

    @Test
    fun testDeepgramAgent_ChatCompletions() = runBlocking {
        val apiKey = getApiKey("deepgram.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Deepgram test - no API key provided")
            return@runBlocking
        }

        val client = createHttpClient()
        val agent = DeepgramAgent(client, apiKey)
        
        val testPrompt = "donne la météo de demain"
        
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
    fun testDeepgramAgent_EmptyKey() = runBlocking {
        val client = createHttpClient()
        val agent = DeepgramAgent(client, "")
        
        val response = agent.process("Test prompt")
        
        assertTrue(response.text.contains("API key", ignoreCase = true),
            "Should return API key required message")
        println("✅ Deepgram empty key test passed")
        
        client.close()
    }
}


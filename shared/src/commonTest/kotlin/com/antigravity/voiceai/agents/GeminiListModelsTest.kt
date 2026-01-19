package com.antigravity.voiceai.agents

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Dedicated test class for running only testListModels
 * Inspired by: https://www.kmpship.app/blog/kotlin-multiplatform-testing-guide-2025
 * This allows filtering to work more reliably
 */
class GeminiListModelsTest : RealApiTestBase() {

    @Test
    fun testListModels() = runBlocking {
        // Check API key first - if missing, skip with clear message
        val apiKey = getApiKey("gemini.key")
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping Gemini testListModels - no API key provided")
            println("💡 Add gemini.key to local.properties to run this test")
            return@runBlocking
        }

        // Execute the test
        withGeminiAgent(apiKey) { agent ->
            println("🔍 Testing Gemini listModels()...")
            val modelsJson = agent.listModels()
            println("✅ Gemini ListModels Response:")
            println("=" .repeat(60))
            println(modelsJson)
            println("=" .repeat(60))
            assertTrue(modelsJson.isNotEmpty(), "ListModels response should not be empty")
        }
    }
}

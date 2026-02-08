package fr.geoking.julius.debug

import fr.geoking.julius.agents.GeminiAgent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

/**
 * Simple debug script to test GeminiAgent.listModels() and process()
 * 
 * Set breakpoints in this file or in GeminiAgent to debug step by step.
 * 
 * To run from IDE: Right-click this file > Run 'GeminiDebug.main()'
 * Or run from command line: ./gradlew :shared:desktopRunDebug
 */
fun main() = runBlocking {
    println("=".repeat(60))
    println("Gemini Debug Script")
    println("=".repeat(60))
    
    // Read API key from local.properties, system property, or environment variable
    val apiKey = getApiKey()
    if (apiKey.isBlank()) {
        println("❌ ERROR: No API key found!")
        println("💡 Options:")
        println("   1. Add 'gemini.key=your_key' to local.properties in project root")
        println("   2. Set system property: -Dgemini.key=your_key")
        println("   3. Set environment variable: export gemini.key=your_key")
        return@runBlocking
    }
    
    println("✅ API key found: ${apiKey.take(10)}...")
    println()
    
    // Create HTTP client
    val client = HttpClient(OkHttp.create()) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    
    try {
        // Create GeminiAgent
        println("🔧 Creating GeminiAgent...")
        val agent = GeminiAgent(client, apiKey = apiKey)
        println("✅ GeminiAgent created")
        println()
        
        // Test 1: List Models
        println("-".repeat(60))
        println("TEST 1: listModels()")
        println("-".repeat(60))
        try {
            val modelsJson = agent.listModels()
            println("✅ Success! Models list:")
            println(modelsJson)
            println()
        } catch (e: Exception) {
            println("❌ Error listing models: ${e.message}")
            e.printStackTrace()
            println()
        }
        
        // Test 2: Process a simple prompt
        println("-".repeat(60))
        println("TEST 2: process() with simple prompt")
        println("-".repeat(60))
        try {
            val prompt = "Say hello in one sentence"
            println("📝 Prompt: $prompt")
            val response = agent.process(prompt)
            println("✅ Success! Response:")
            println(response.text)
            println()
        } catch (e: Exception) {
            println("❌ Error processing prompt: ${e.message}")
            e.printStackTrace()
            println()
        }
        
    } finally {
        client.close()
        println("=".repeat(60))
        println("Debug script completed")
        println("=".repeat(60))
    }
}

/**
 * Reads API key from:
 * 1. System property (e.g., -Dgemini.key=...)
 * 2. Environment variable (e.g., export gemini.key=...)
 * 3. local.properties file in project root
 */
private fun getApiKey(): String {
    val keyName = "gemini.key"
    
    // Try system property first
    System.getProperty(keyName)?.let { return it.trim() }
    
    // Try environment variable
    System.getenv(keyName)?.let { return it.trim() }
    
    // Try to read from local.properties - look in project root
    try {
        val possiblePaths = listOf(
            File("local.properties"),  // Current dir (when running from project root)
            File("../local.properties"),  // Parent dir
            File("../../local.properties"),  // Grandparent dir
            File("../../../local.properties"),  // Great-grandparent dir
            File("shared/local.properties"),  // shared module dir
            File("../../shared/local.properties")  // shared module from deeper dir
        )
        
        for (localPropsFile in possiblePaths) {
            if (localPropsFile.exists() && localPropsFile.isFile) {
                val props = Properties()
                localPropsFile.inputStream().use { props.load(it) }
                val value = props.getProperty(keyName)?.trim()
                if (!value.isNullOrEmpty()) {
                    println("📁 Found API key from ${localPropsFile.absolutePath}")
                    return value
                }
            }
        }
    } catch (e: Exception) {
        println("⚠️  Error reading local.properties: ${e.message}")
    }
    
    return ""
}

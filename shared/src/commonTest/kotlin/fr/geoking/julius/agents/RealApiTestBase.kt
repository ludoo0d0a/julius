package fr.geoking.julius.agents

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Base for real API tests. API keys and optional model overrides are read from:
 * - System properties (e.g. -Dopenai.key=...)
 * - local.properties in project root (e.g. openai.key=..., gemini.key=..., perplexity.key=...,
 *   elevenlabs.key=..., deepgram.key=..., firebaseai.key=..., firebaseai.model=...,
 *   completionsme.key=..., completionsme.model=..., apifreellm.key=..., opencodezen.key=..., opencodezen.model=...)
 *
 * Each agent test: checks key (skip if missing), builds agent with key/model, sends a question, asserts non-empty answer.
 */
open class RealApiTestBase {
    protected fun createHttpClient(): HttpClient {
        return HttpClient(createTestHttpClientEngine()) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true  // Ensure model and other defaults are sent to APIs (e.g. OpenAI)
                })
            }
        }
    }

    // Helper to read API keys from local.properties or system properties
    protected fun getApiKey(propertyName: String, default: String = ""): String {
        return TestPropertyReader.getProperty(propertyName, default)
    }

    protected fun defaultPrompt(): String = "donne la météo de demain"

    /**
     * Executes a test with API key check - returns null if API key is missing (test should skip)
     */
    protected suspend fun <T> withApiKey(propertyName: String, testName: String, block: suspend (String) -> T): T? {
        val apiKey = getApiKey(propertyName)
        if (apiKey.isEmpty()) {
            println("⚠️  Skipping $testName test - no API key provided")
            return null
        }
        return block(apiKey)
    }

    /**
     * Executes a block with HTTP client, ensuring it's closed after execution
     */
    protected suspend fun <T> withHttpClient(block: suspend (HttpClient) -> T): T {
        val client = createHttpClient()
        return try {
            block(client)
        } finally {
            client.close()
        }
    }

    /**
     * Executes a block with an agent, managing HTTP client lifecycle
     */
    protected suspend fun <T> withAgent(
        createAgent: (HttpClient) -> ConversationalAgent,
        block: suspend (ConversationalAgent) -> T
    ): T {
        return withHttpClient { client ->
            val agent = createAgent(client)
            block(agent)
        }
    }

    /**
     * Executes a block with a GeminiAgent (pure HTTP-based implementation).
     * Model can be overridden via getApiKey("gemini.model", "gemini-2.0-flash").
     */
    protected suspend fun <T> withGeminiAgent(
        apiKey: String,
        model: String = getApiKey("gemini.model", "gemini-2.0-flash"),
        block: suspend (GeminiAgent) -> T
    ): T {
        return withHttpClient { client ->
            val agent = GeminiAgent(client, apiKey = apiKey, model = model)
            block(agent)
        }
    }

    /**
     * Executes a block with an OpenAIAgent.
     */
    protected suspend fun <T> withOpenAIAgent(
        apiKey: String,
        block: suspend (OpenAIAgent) -> T
    ): T {
        return withHttpClient { client ->
            val agent = OpenAIAgent(client, apiKey = apiKey)
            block(agent)
        }
    }

    /**
     * Executes a block with a PerplexityAgent.
     * Model can be overridden via getApiKey("perplexity.model", "llama-3.1-sonar-small-128k-online").
     */
    protected suspend fun <T> withPerplexityAgent(
        apiKey: String,
        model: String = getApiKey("perplexity.model", "llama-3.1-sonar-small-128k-online"),
        block: suspend (PerplexityAgent) -> T
    ): T {
        return withHttpClient { client ->
            val agent = PerplexityAgent(client, apiKey = apiKey, model = model)
            block(agent)
        }
    }

    /**
     * Tests an agent with automatic error handling and assertions
     */
    protected suspend fun testAgent(
        agent: ConversationalAgent,
        agentName: String,
        prompt: String = defaultPrompt(),
        additionalAssertions: ((AgentResponse) -> Unit)? = null
    ) {
        val response = agent.process(prompt)
        println("✅ $agentName Response: ${response.text.take(150)}...")

        assertTrue(response.text.isNotEmpty(), "Response text should not be empty")
        assertTrue(
            !response.text.startsWith("Error"),
            "Should not return error message: ${response.text}"
        )

        additionalAssertions?.invoke(response)
    }

    /**
     * Tests listModels() function on an agent
     */
    protected suspend fun testListModels(
        agent: ConversationalAgent,
        agentName: String
    ) {
        try {
            val modelsJson = agent.listModels()
            println("✅ $agentName ListModels Response:")
            println(modelsJson)
            assertTrue(modelsJson.isNotEmpty(), "ListModels response should not be empty")
        } catch (e: UnsupportedOperationException) {
            println("⚠️  $agentName does not support listing models")
            throw e
        } catch (e: Exception) {
            println("❌ $agentName ListModels test failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Tests an HTTP request with automatic error handling
     */
    protected suspend fun testHttpRequest(
        client: HttpClient,
        url: String,
        testName: String,
        block: suspend (HttpResponse, String) -> Unit
    ) {
        try {
            val response = client.get(url)
            val responseBody = response.bodyAsText()
            println("✅ $testName Response:")
            println(responseBody)
            block(response, responseBody)
        } catch (e: Exception) {
            println("❌ $testName test failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}

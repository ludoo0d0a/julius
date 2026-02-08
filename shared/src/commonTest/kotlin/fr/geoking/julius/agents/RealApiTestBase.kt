package fr.geoking.julius.agents

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

open class RealApiTestBase {
    protected fun createHttpClient(): HttpClient {
        return HttpClient(createTestHttpClientEngine()) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
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
     * Executes a block with a GeminiAgent (pure HTTP-based implementation)
     */
    protected suspend fun <T> withGeminiAgent(
        apiKey: String,
        block: suspend (GeminiAgent) -> T
    ): T {
        return withHttpClient { client ->
            val agent = GeminiAgent(client, apiKey = apiKey)
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

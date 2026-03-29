package fr.geoking.julius.agents

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class NewAgentsMockTests {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createMockClient(responseBody: String): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Test
    fun testDeepSeekAgent_MockResponse() = runBlocking {
        val mockResponse = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "Hello from DeepSeek!"
                  }
                }
              ]
            }
        """.trimIndent()
        val client = createMockClient(mockResponse)
        val agent = DeepSeekAgent(client, apiKey = "test-key")

        val response = agent.process("Hello")
        assertEquals("Hello from DeepSeek!", response.text)
    }

    @Test
    fun testGroqAgent_MockResponse() = runBlocking {
        val mockResponse = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "Hello from Groq!"
                  }
                }
              ]
            }
        """.trimIndent()
        val client = createMockClient(mockResponse)
        val agent = GroqAgent(client, apiKey = "test-key")

        val response = agent.process("Hello")
        assertEquals("Hello from Groq!", response.text)
    }

    @Test
    fun testOpenRouterAgent_MockResponse() = runBlocking {
        val mockResponse = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "Hello from OpenRouter!"
                  }
                }
              ]
            }
        """.trimIndent()
        val client = createMockClient(mockResponse)
        val agent = OpenRouterAgent(client, apiKey = "test-key")

        val response = agent.process("Hello")
        assertEquals("Hello from OpenRouter!", response.text)
    }
}

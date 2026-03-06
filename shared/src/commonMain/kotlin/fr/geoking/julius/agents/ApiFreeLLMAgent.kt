package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ApiFreeLLM agent - free LLM API (custom format, not OpenAI-compatible).
 * Free tier: 1 request every 25 seconds, 32k context.
 * Sign in with Google at apifreellm.com to get API key.
 * See https://apifreellm.com/en/api-access
 */
class ApiFreeLLMAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://apifreellm.com/api/v1"
) : ConversationalAgent {

    @Serializable private data class Req(val message: String)
    @Serializable private data class Res(val success: Boolean, val response: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "ApiFreeLLM API key is required. Sign in with Google at apifreellm.com")
        }

        val response = client.post("$baseUrl/chat") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(Req(message = input))
        }

        val responseBody = response.bodyAsText()

        if (response.status.value == 429) {
            throw NetworkException(429, "Rate limit: wait 25 seconds between requests")
        }
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Error from ApiFreeLLM: $responseBody")
        }

        val res = json.decodeFromString<Res>(responseBody)
        val text = res.response?.takeIf { res.success } ?: "No response"
        return AgentResponse(text, null)
    }
}

package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import fr.geoking.julius.shared.platform.getCurrentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val baseUrl: String = "https://apifreellm.com/api/v1",
    override val rateLimitDelayMs: Long = 25000L
) : ConversationalAgent {

    private val mutex = Mutex()
    private var lastRequestStartTime: Long = 0

    @Serializable private data class Req(val message: String)
    @Serializable private data class Res(val success: Boolean, val response: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? {
        if (input.apifreellmKey.isBlank()) {
            return AgentSetupDescriptor.MissingApiKey(missingApiKeyMessage(input.selectedAgentDisplayName))
        }
        return null
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "ApiFreeLLM API key is required. Sign in with Google at apifreellm.com")
        }

        // Enforce rate limit delay
        val now = getCurrentTimeMillis()
        val timeSinceLastRequest = now - lastRequestStartTime
        if (timeSinceLastRequest < rateLimitDelayMs) {
            val waitTime = rateLimitDelayMs - timeSinceLastRequest
            delay(waitTime)
        }

        lastRequestStartTime = getCurrentTimeMillis()

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

package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DeepSeek agent - OpenAI-compatible API.
 * See https://api-docs.deepseek.com/
 */
class DeepSeekAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "deepseek-chat",
    private val baseUrl: String = "https://api.deepseek.com"
) : ConversationalAgent {

    private val mutex = Mutex()

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class Req(val model: String, val messages: List<Msg>)
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class Res(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? {
        if (input.deepSeekKey.isBlank()) {
            return AgentSetupDescriptor.MissingApiKey(missingApiKeyMessage(input.selectedAgentDisplayName))
        }
        return null
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        val url = "$baseUrl/chat/completions"
        if (apiKey.isBlank()) {
            throw NetworkException(
                httpCode = null,
                message = "DeepSeek API key is required. Get one at platform.deepseek.com",
                provider = "DeepSeek"
            )
        }

        val response = client.post(url) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(Req(model = model, messages = listOf(Msg("user", input))))
        }

        val responseBody = response.bodyAsText()

        if (response.status.value != 200) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "Error from DeepSeek: $responseBody",
                url = url,
                provider = "DeepSeek"
            )
        }

        val text = json.decodeFromString<Res>(responseBody).choices.firstOrNull()?.message?.content ?: "No response"
        return AgentResponse(text, null)
    }
}

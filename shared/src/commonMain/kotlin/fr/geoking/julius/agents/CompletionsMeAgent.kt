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
 * Completions.me agent - 100% OpenAI-compatible free API.
 * Models: claude-opus-4.6, claude-sonnet-4.5, gpt-5.2, gemini-3.1-pro, etc.
 * See https://www.completions.me/
 */
class CompletionsMeAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "claude-sonnet-4.5",
    private val baseUrl: String = "https://www.completions.me/api/v1"
) : ConversationalAgent {

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class Req(val model: String, val messages: List<Msg>)
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class Res(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "Completions.me API key is required. Get one at completions.me/register")
        }

        val response = client.post("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(Req(model = model, messages = listOf(Msg("user", input))))
        }

        val responseBody = response.bodyAsText()

        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Error from Completions.me: $responseBody")
        }

        val text = json.decodeFromString<Res>(responseBody).choices.firstOrNull()?.message?.content ?: "No response"
        return AgentResponse(text, null)
    }
}

package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
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
 * OpenCode Zen agent - OpenAI-compatible API gateway.
 * Free models: big-pickle, minimax-m2.5-free, gpt-5-nano
 * See https://opencode.ai/docs/zen/
 */
class OpenCodeZenAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "minimax-m2.5-free",
    private val baseUrl: String = "https://opencode.ai/zen/v1"
) : ConversationalAgent {

    private val mutex = Mutex()

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class Req(val model: String, val messages: List<Msg>)
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class Res(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? {
        if (input.opencodeZenKey.isBlank()) {
            return AgentSetupDescriptor.MissingApiKey(missingApiKeyMessage(input.selectedAgentDisplayName))
        }
        return null
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "OpenCode Zen API key is required. Get one at opencode.ai")
        }

        val response = client.post("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(Req(model = model, messages = listOf(Msg("user", input))))
        }

        val responseBody = response.bodyAsText()

        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Error from OpenCode Zen: $responseBody")
        }

        val text = json.decodeFromString<Res>(responseBody).choices.firstOrNull()?.message?.content ?: "No response"
        return AgentResponse(text, null)
    }
}

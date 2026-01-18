package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PerplexityAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String
) : ConversationalAgent {

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class Req(val model: String, val messages: List<Msg>)
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class Res(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "Perplexity API key is required. Please set it in settings.")
        }

        val response = client.post("https://api.perplexity.ai/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                Req(
                    model = model,
                    messages = listOf(Msg("user", input))
                )
            )
        }

        val responseBody = response.bodyAsText()

        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Error from Perplexity API: $responseBody")
        }

        val text = json.decodeFromString<Res>(responseBody).choices.firstOrNull()?.message?.content ?: "No response"
        return AgentResponse(text, null) // System TTS
    }
}

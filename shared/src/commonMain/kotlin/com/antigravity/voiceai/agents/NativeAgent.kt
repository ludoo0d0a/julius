package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NativeAgent(
    private val client: HttpClient,
    private val apiKey: String
) : ConversationalAgent {

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class Req(val model: String = "llama-3.1-sonar-small-128k-online", val messages: List<Msg>)
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class Res(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
         try {
            val responseBody = client.post("https://api.perplexity.ai/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    Req(messages = listOf(Msg("user", input)))
                )
            }.bodyAsText()

            val text = json.decodeFromString<Res>(responseBody).choices.firstOrNull()?.message?.content ?: "No response"
            return AgentResponse(text, null) // System TTS
        } catch (e: Exception) {
            return AgentResponse("Error: ${e.message}", null)
        }
    }
}

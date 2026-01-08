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

class DeepgramAgent(
    private val client: HttpClient,
    private val deepgramKey: String
) : ConversationalAgent {

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class ChatReq(
        val model: String = "nova-2",
        val messages: List<Msg>,
        val temperature: Double = 0.7
    )
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class ChatRes(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
        if (deepgramKey.isBlank()) {
            return AgentResponse("Deepgram API key is required. Please set it in settings.", null)
        }

        try {
            val response = client.post("https://api.deepgram.com/v1/chat/completions") {
                header("Authorization", "Bearer $deepgramKey")
                contentType(ContentType.Application.Json)
                setBody(
                    ChatReq(messages = listOf(Msg("user", input)))
                )
            }

            val responseBody = response.bodyAsText()

            // Check for error response
            if (response.status.value != 200) {
                return AgentResponse("Error connecting to Deepgram: ${response.status.value} - $responseBody", null)
            }

            // Check if response body is empty
            if (responseBody.isBlank()) {
                return AgentResponse("Error connecting to Deepgram: Empty response", null)
            }

            val text = json.decodeFromString<ChatRes>(responseBody)
                .choices
                .firstOrNull()
                ?.message
                ?.content
                ?: "No response from Deepgram"

            // Deepgram doesn't provide TTS in chat completions, use system TTS
            return AgentResponse(text, null)
        } catch (e: Exception) {
            e.printStackTrace()
            return AgentResponse("Error connecting to Deepgram: ${e.message}", null)
        }
    }
}

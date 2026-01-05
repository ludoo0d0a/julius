package com.antigravity.voiceai.shared

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PerplexityChatService(
    private val client: HttpClient,
    private val apiKey: String
) : ChatService {

    @Serializable
    private data class Message(val role: String, val content: String)
    
    @Serializable
    private data class Request(
        val model: String = "llama-3.1-sonar-small-128k-online",
        val messages: List<Message>
    )

    @Serializable
    private data class Choice(val message: Message)
    
    @Serializable
    private data class Response(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendMessage(message: String): String {
        try {
            // Placeholder URL - for Perplexity it is https://api.perplexity.ai/chat/completions
            val responseBody = client.post("https://api.perplexity.ai/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    Request(messages = listOf(Message("user", message)))
                )
            }.bodyAsText()

            val response = json.decodeFromString<Response>(responseBody)
            return response.choices.firstOrNull()?.message?.content ?: "No response"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: ${e.message}"
        }
    }
}

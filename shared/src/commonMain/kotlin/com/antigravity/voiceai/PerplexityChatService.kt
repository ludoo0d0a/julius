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
        val model: String,
        val messages: List<Message>
    )

    @Serializable
    private data class Choice(val message: Message)
    
    @Serializable
    private data class Response(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sendMessage(message: String, model: String): String {
        try {
            log.i { "Sending message to Perplexity with model $model" }
            val response = client.post("https://api.perplexity.ai/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    Request(
                        model = model,
                        messages = listOf(Message("user", message))
                    )
                )
            }

            val responseBody = response.bodyAsText()

            if (response.status.value != 200) {
                throw Exception("Error from Perplexity API: ${response.status.value} ${responseBody}")
            }


            val decodedResponse = json.decodeFromString<Response>(responseBody)
            return decodedResponse.choices.firstOrNull()?.message?.content ?: "No response"
        } catch (e: Exception) {
            log.e(e) { "Error sending message to Perplexity" }
            throw e
        }
    }
}

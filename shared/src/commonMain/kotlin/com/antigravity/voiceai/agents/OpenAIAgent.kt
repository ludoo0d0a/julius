package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenAIAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : ConversationalAgent {

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class ChatReq(val model: String = "gpt-4o", val messages: List<Msg>)
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class ChatRes(val choices: List<Choice>)
    
    @Serializable private data class TtsReq(val model: String = "tts-1-hd", val input: String, val voice: String = "nova")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "OpenAI API key is required. Please set it in settings.")
        }

        // 1. Get Text
        val chatResponse = client.post("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatReq(model = "gpt-4o", messages = listOf(Msg("user", input))))
        }
        
        val chatBody = chatResponse.bodyAsText()
        
        val text = try {
            if (chatResponse.status.value != 200) {
                throw NetworkException(chatResponse.status.value, "Error from OpenAI: $chatBody")
            } else {
                json.decodeFromString<ChatRes>(chatBody).choices.firstOrNull()?.message?.content 
                    ?: "No response from OpenAI"
            }
        } catch (e: Exception) {
            throw NetworkException(null, "Error from OpenAI: ${e.message}")
        }

        // 2. Get Audio
        val audioBytes = try {
            client.post("$baseUrl/audio/speech") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(TtsReq(model = "tts-1-hd", input = text, voice = "nova"))
            }.body<ByteArray>()
        } catch (e: Exception) {
            null
        }

        return AgentResponse(text, audioBytes)
    }
}

package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenAIAgent(
    private val client: HttpClient,
    private val apiKey: String
) : ConversationalAgent {

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class ChatReq(val model: String = "gpt-4o", val messages: List<Msg>)
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class ChatRes(val choices: List<Choice>)
    
    @Serializable private data class TtsReq(val model: String = "tts-1-hd", val input: String, val voice: String = "nova")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
        // 1. Get Text
        val chatBody = client.post("https://api.openai.com/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatReq(messages = listOf(Msg("user", input))))
        }.bodyAsText()
        
        val text = try {
            json.decodeFromString<ChatRes>(chatBody).choices.first().message.content
        } catch (e: Exception) {
            "Error from OpenAI"
        }

        // 2. Get Audio
        val audioBytes = try {
            client.post("https://api.openai.com/v1/audio/speech") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(TtsReq(input = text))
            }.readBytes()
        } catch (e: Exception) {
            null
        }

        return AgentResponse(text, audioBytes)
    }
}

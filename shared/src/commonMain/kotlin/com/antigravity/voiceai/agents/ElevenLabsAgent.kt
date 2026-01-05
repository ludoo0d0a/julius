package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ElevenLabsAgent(
    private val client: HttpClient,
    private val perplexityKey: String,
    private val elevenLabsKey: String,
    private val voiceId: String = "JBFqnCBsd6RMkjVDRZzb" // Example: George
) : ConversationalAgent {

    private val nativeAgent = NativeAgent(client, perplexityKey)

    @Serializable private data class TtsReq(
        val text: String,
        val model_id: String = "eleven_turbo_v2_5",
        val voice_settings: VoiceSettings = VoiceSettings()
    )
    @Serializable private data class VoiceSettings(val stability: Double = 0.5, val similarity_boost: Double = 0.75)

    override suspend fun process(input: String): AgentResponse {
        // 1. Get Text from LLM (reusing NativeAgent)
        val response = nativeAgent.process(input)
        val text = response.text

        // 2. Get Audio from ElevenLabs
        val audioBytes = try {
            client.post("https://api.elevenlabs.io/v1/text-to-speech/$voiceId") {
                header("xi-api-key", elevenLabsKey)
                contentType(ContentType.Application.Json)
                setBody(TtsReq(text = text))
            }.readBytes()
        } catch (e: Exception) {
            null
        }

        return AgentResponse(text, audioBytes)
    }
}

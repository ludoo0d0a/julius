package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class ElevenLabsAgent(
    private val client: HttpClient,
    private val perplexityKey: String,
    private val elevenLabsKey: String,
    private val voiceId: String = "JBFqnCBsd6RMkjVDRZzb", // Example: George
    private val model: String
) : ConversationalAgent {

    private val perplexityAgent = PerplexityAgent(client, perplexityKey, model)

    @Serializable private data class TtsReq(
        val text: String,
        val model_id: String = "eleven_turbo_v2_5",
        val voice_settings: VoiceSettings = VoiceSettings()
    )
    @Serializable private data class VoiceSettings(val stability: Double = 0.5, val similarity_boost: Double = 0.75)

    override suspend fun process(input: String): AgentResponse {
        if (elevenLabsKey.isBlank()) {
            throw NetworkException(null, "ElevenLabs API key is required. Please set it in settings.")
        }

        // 1. Get Text from LLM (reusing PerplexityAgent)
        val response = perplexityAgent.process(input)
        val text = response.text

        // 2. Get Audio from ElevenLabs
        val audioBytes = try {
            val ttsResponse = client.post("https://api.elevenlabs.io/v1/text-to-speech/$voiceId") {
                header("xi-api-key", elevenLabsKey)
                contentType(ContentType.Application.Json)
                setBody(TtsReq(text = text))
            }
            if (ttsResponse.status.value != 200) {
                throw NetworkException(ttsResponse.status.value, "Error from ElevenLabs: ${ttsResponse.bodyAsText()}")
            }
            ttsResponse.body<ByteArray>()
        } catch (e: Exception) {
            throw NetworkException(null, "Error from ElevenLabs: ${e.message}")
        }

        return AgentResponse(text, audioBytes)
    }
}

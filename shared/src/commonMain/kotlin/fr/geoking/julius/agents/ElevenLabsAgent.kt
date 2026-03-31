package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import kotlinx.serialization.json.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

class ElevenLabsAgent(
    private val client: HttpClient,
    private val perplexityKey: String,
    private val elevenLabsKey: String,
    private val voiceId: String = "JBFqnCBsd6RMkjVDRZzb", // Example: George
    private val model: String,
    private val scribe2: Boolean = true,
    private val baseUrl: String = "https://api.elevenlabs.io/v1"
) : ConversationalAgent {

    private val mutex = Mutex()
    private val perplexityAgent = PerplexityAgent(client, perplexityKey, model)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class TranscriptionRes(val text: String)

    @Serializable private data class TtsReq(
        val text: String,
        val model_id: String = "eleven_turbo_v2_5",
        val voice_settings: VoiceSettings = VoiceSettings()
    )
    @Serializable private data class VoiceSettings(val stability: Double = 0.5, val similarity_boost: Double = 0.75)

    override val isSttSupported: Boolean get() = true

    override suspend fun transcribe(audioData: ByteArray): String? = mutex.withLock {
        if (elevenLabsKey.isBlank()) return null

        try {
            val response = client.submitFormWithBinaryData(
                url = "$baseUrl/speech-to-text",
                formData = formData {
                    append("file", audioData, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"audio.pcm\"")
                        append(HttpHeaders.ContentType, "application/octet-stream")
                    })
                    append("model_id", if (scribe2) "scribe_v2" else "scribe_v1")
                    append("file_format", "pcm_s16le_16")
                }
            ) {
                header("xi-api-key", elevenLabsKey)
            }

            if (response.status.value == 200) {
                return json.decodeFromString<TranscriptionRes>(response.bodyAsText()).text
            } else {
                fr.geoking.julius.shared.log.e { "ElevenLabs STT error: ${response.status} ${response.bodyAsText()}" }
            }
        } catch (e: Exception) {
            fr.geoking.julius.shared.log.e { "ElevenLabs STT exception: ${e.message}" }
        }
        return null
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        if (elevenLabsKey.isBlank()) {
            throw NetworkException(null, "ElevenLabs API key is required. Please set it in settings.")
        }

        // 1. Get Text from LLM (reusing PerplexityAgent)
        val response = perplexityAgent.process(input)
        val text = response.text

        // 2. Get Audio from ElevenLabs
        val audioBytes = try {
            val ttsResponse = client.post("$baseUrl/text-to-speech/$voiceId") {
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

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? {
        if (input.perplexityKey.isBlank() || input.elevenLabsKey.isBlank()) {
            return AgentSetupDescriptor.MissingApiKey(missingApiKeyMessage(input.selectedAgentDisplayName))
        }
        return null
    }
}

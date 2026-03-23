package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DeepgramAgent(
    private val client: HttpClient,
    private val deepgramKey: String,
    private val baseUrl: String = "https://api.deepgram.com/v1"
) : ConversationalAgent {

    private val mutex = Mutex()

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class ChatReq(
        val model: String = "nova-2",
        val messages: List<Msg>,
        val temperature: Double = 0.7
    )
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class ChatRes(val choices: List<Choice>)

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class TranscriptionRes(val results: Results)
    @Serializable private data class Results(val channels: List<Channel>)
    @Serializable private data class Channel(val alternatives: List<Alternative>)
    @Serializable private data class Alternative(val transcript: String)

    override val isSttSupported: Boolean get() = true

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? {
        if (input.deepgramKey.isBlank()) {
            return AgentSetupDescriptor.MissingApiKey(missingApiKeyMessage(input.selectedAgentDisplayName))
        }
        return null
    }

    override suspend fun transcribe(audioData: ByteArray): String? = mutex.withLock {
        if (deepgramKey.isBlank()) return null

        try {
            val response = client.post("$baseUrl/listen?model=nova-2&smart_format=true") {
                header("Authorization", "Bearer $deepgramKey")
                contentType(ContentType.parse("audio/x-uncompressed;bit=16;rate=16000;channels=1"))
                setBody(audioData)
            }

            if (response.status.value == 200) {
                val res = json.decodeFromString<TranscriptionRes>(response.bodyAsText())
                return res.results.channels.firstOrNull()?.alternatives?.firstOrNull()?.transcript
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        if (deepgramKey.isBlank()) {
            throw NetworkException(null, "Deepgram API key is required. Please set it in settings.")
        }

        try {
            val response = client.post("$baseUrl/chat/completions") {
                header("Authorization", "Bearer $deepgramKey")
                contentType(ContentType.Application.Json)
                setBody(
                    ChatReq(messages = listOf(Msg("user", input)))
                )
            }

            val responseBody = response.bodyAsText()

            // Check for error response
            if (response.status.value != 200) {
                throw NetworkException(response.status.value, "Error connecting to Deepgram: $responseBody")
            }

            // Check if response body is empty
            if (responseBody.isBlank()) {
                throw NetworkException(null, "Error connecting to Deepgram: Empty response")
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
            throw NetworkException(null, "Error connecting to Deepgram: ${e.message}")
        }
    }
}

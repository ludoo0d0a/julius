package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import fr.geoking.julius.shared.ActionType
import fr.geoking.julius.shared.DeviceAction

class OpenAIAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "gpt-4o",
    private val baseUrl: String = "https://api.openai.com/v1",
    private val toolsEnabled: Boolean = false
) : ConversationalAgent {

    private val mutex = Mutex()

    @Serializable private data class Msg(
        val role: String,
        val content: String? = null,
        val tool_calls: List<ToolCallRes>? = null,
        val tool_call_id: String? = null
    )
    @Serializable private data class ToolCallRes(val id: String, val type: String, val function: FunctionCallRes)
    @Serializable private data class FunctionCallRes(val name: String, val arguments: String)

    @Serializable private data class Tool(val type: String, val function: FunctionDef)
    @Serializable private data class FunctionDef(val name: String, val description: String, val parameters: JsonObject)

    @Serializable private data class ChatReq(
        val model: String,
        val messages: List<Msg>,
        val tools: List<Tool>? = null
    )
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class ChatRes(val choices: List<Choice>)
    
    @Serializable private data class TtsReq(val model: String = "tts-1-hd", val input: String, val voice: String = "nova")
    @Serializable private data class TranscriptionRes(val text: String)

    private val json = Json { ignoreUnknownKeys = true }

    override val isSttSupported: Boolean get() = true

    override suspend fun transcribe(audioData: ByteArray): String? = mutex.withLock {
        if (apiKey.isBlank()) return null

        try {
            val wavData = pcmToWav(audioData)
            val response = client.submitFormWithBinaryData(
                url = "$baseUrl/audio/transcriptions",
                formData = formData {
                    append("file", wavData, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"audio.wav\"")
                        append(HttpHeaders.ContentType, "audio/wav")
                    })
                    append("model", "whisper-1")
                }
            ) {
                header("Authorization", "Bearer $apiKey")
            }

            if (response.status.value == 200) {
                return json.decodeFromString<TranscriptionRes>(response.bodyAsText()).text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 16000): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono

        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = 1 // channels = 1 (Mono)
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2 // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        val wav = ByteArray(44 + pcmData.size)
        header.copyInto(wav)
        pcmData.copyInto(wav, 44)
        return wav
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "OpenAI API key is required. Please set it in settings.")
        }

        val tools = if (toolsEnabled) {
            listOf(
                Tool("function", FunctionDef("get_location", "Get the current GPS location of the device", buildJsonObject {})),
                Tool("function", FunctionDef("get_battery_level", "Get the current battery level percentage of the device", buildJsonObject {})),
                Tool("function", FunctionDef("get_volume_levels", "Get the current system volume levels (media, alarm, ring)", buildJsonObject {}))
            )
        } else null

        // 1. Get Text
        val chatResponse = client.post("$baseUrl/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(ChatReq(
                model = model,
                messages = listOf(Msg("user", input)),
                tools = tools
            ))
        }
        
        val chatBody = chatResponse.bodyAsText()
        
        val responseMessage = try {
            if (chatResponse.status.value != 200) {
                throw NetworkException(chatResponse.status.value, "Error from OpenAI: $chatBody")
            } else {
                json.decodeFromString<ChatRes>(chatBody).choices.firstOrNull()?.message
                    ?: throw Exception("No response from OpenAI")
            }
        } catch (e: Exception) {
            if (e is NetworkException) throw e
            throw NetworkException(null, "Error from OpenAI: ${e.message}")
        }

        val text = responseMessage.content ?: ""
        val toolCalls = responseMessage.tool_calls?.mapNotNull { tc ->
            val type = when (tc.function.name) {
                "get_location" -> ActionType.GET_LOCATION
                "get_battery_level" -> ActionType.GET_BATTERY_LEVEL
                "get_volume_levels" -> ActionType.GET_VOLUME_LEVEL
                else -> null
            }
            type?.let { ToolCall(tc.id, DeviceAction(it)) }
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

        return AgentResponse(text, audioBytes, toolCalls = toolCalls)
    }
}

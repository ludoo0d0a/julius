package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import fr.geoking.julius.shared.ActionType
import fr.geoking.julius.shared.DeviceAction

class OpenAIAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val toolsEnabled: Boolean = false
) : ConversationalAgent {

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
        val model: String = "gpt-4o",
        val messages: List<Msg>,
        val tools: List<Tool>? = null
    )
    @Serializable private data class Choice(val message: Msg)
    @Serializable private data class ChatRes(val choices: List<Choice>)
    
    @Serializable private data class TtsReq(val model: String = "tts-1-hd", val input: String, val voice: String = "nova")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun process(input: String): AgentResponse {
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
                model = "gpt-4o",
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

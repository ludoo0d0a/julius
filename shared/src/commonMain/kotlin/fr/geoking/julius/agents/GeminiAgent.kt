package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import fr.geoking.julius.shared.ActionType
import fr.geoking.julius.shared.DeviceAction

/**
 * Pure HTTP-based GeminiAgent implementation using REST API.
 * Works on all platforms (Android, iOS, Desktop) using Ktor HTTP client.
 * Follows the Gemini API REST specification:
 * https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference
 */
class GeminiAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash", // Updated default model
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val toolsEnabled: Boolean = false
) : ConversationalAgent {

    init {
        // Configure client with JSON serialization if not already configured
        // The client should be pre-configured by the caller with ContentNegotiation
    }

    @Serializable
    private data class Part(
        val text: String? = null,
        val functionCall: FunctionCallRes? = null,
        val functionResponse: FunctionResponseRes? = null
    )

    @Serializable
    private data class FunctionCallRes(val name: String, val args: JsonObject)

    @Serializable
    private data class FunctionResponseRes(val name: String, val response: JsonObject)

    @Serializable
    private data class Tool(val function_declarations: List<FunctionDeclaration>)

    @Serializable
    private data class FunctionDeclaration(val name: String, val description: String, val parameters: JsonObject)


    @Serializable
    private data class Content(
        val parts: List<Part>,
        val role: String = "user"
    )

    @Serializable
    private data class GenerateContentRequest(
        val contents: List<Content>,
        val tools: List<Tool>? = null
    )

    @Serializable
    private data class Candidate(
        val content: Content,
        val finishReason: String? = null
    )

    @Serializable
    private data class ErrorDetail(
        val message: String,
        val status: String
    )

    @Serializable
    private data class ErrorResponse(
        val error: ErrorDetail
    )

    @Serializable
    private data class GenerateContentResponse(
        val candidates: List<Candidate>? = null
    )

    @Serializable
    private data class ModelInfo(
        val name: String,
        val supportedGenerationMethods: List<String> = emptyList()
    )

    @Serializable
    private data class ModelsListResponse(
        val models: List<ModelInfo>
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun process(input: String): AgentResponse {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "Gemini API key is required. Please set it in settings.")
        }

        try {
            // Following REST API specification from:
            // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference
            val url = "$baseUrl/models/$model:generateContent?key=$apiKey"

            val tools = if (toolsEnabled) {
                listOf(
                    Tool(listOf(
                        FunctionDeclaration("get_location", "Get the current GPS location of the device", buildJsonObject {}),
                        FunctionDeclaration("get_battery_level", "Get the current battery level percentage of the device", buildJsonObject {}),
                        FunctionDeclaration("get_volume_levels", "Get the current system volume levels (media, alarm, ring)", buildJsonObject {})
                    ))
                )
            } else null

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = input)),
                        role = "user"
                    )
                ),
                tools = tools
            )

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseBody = response.bodyAsText()

            if (response.status.value != 200) {
                try {
                    val errorRes = json.decodeFromString<ErrorResponse>(responseBody)
                    throw NetworkException(
                        response.status.value,
                        "Error connecting to Gemini: ${errorRes.error.message}"
                    )
                } catch (e: Exception) {
                    if (e is NetworkException) throw e
                    throw NetworkException(
                        response.status.value,
                        "Error connecting to Gemini: HTTP ${response.status.value} - $responseBody"
                    )
                }
            }

            if (responseBody.isBlank()) {
                throw NetworkException(
                    response.status.value,
                    "Error connecting to Gemini: Received empty response body"
                )
            }

            val res = json.decodeFromString<GenerateContentResponse>(responseBody)
            val candidate = res.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.find { it.text != null }?.text ?: ""

            val toolCalls = candidate?.content?.parts?.filter { it.functionCall != null }?.mapNotNull { p ->
                val fc = p.functionCall!!
                val type = when (fc.name) {
                    "get_location" -> ActionType.GET_LOCATION
                    "get_battery_level" -> ActionType.GET_BATTERY_LEVEL
                    "get_volume_levels" -> ActionType.GET_VOLUME_LEVEL
                    else -> null
                }
                // Gemini function calls don't have explicit IDs in the same way as OpenAI in simple REST,
                // but we need one for our internal model.
                type?.let { ToolCall(fc.name, DeviceAction(it)) }
            }

            return AgentResponse(text, null, toolCalls = toolCalls) // Audio null = Use System TTS
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is NetworkException) throw e
            throw NetworkException(null, "Error connecting to Gemini: ${e.message}")
        }
    }

    override suspend fun listModels(): String {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "Gemini API key is required. Please set it in settings.")
        }

        try {
            val url = "$baseUrl/models?key=$apiKey"
            val response = client.get(url)
            val responseBody = response.bodyAsText()

            if (response.status.value != 200) {
                throw NetworkException(
                    response.status.value,
                    "Error listing Gemini models: HTTP ${response.status.value} - $responseBody"
                )
            }

            // Parse the JSON response and filter models that support generateContent
            val modelsResponse = json.decodeFromString<ModelsListResponse>(responseBody)
            val filteredModelNames = modelsResponse.models
                .filter { modelInfo ->
                    modelInfo.supportedGenerationMethods.contains("generateContent")
                }
                .map { it.name }

            // Return only model names as JSON array
            return json.encodeToString(ListSerializer(String.serializer()), filteredModelNames)
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is NetworkException) throw e
            throw NetworkException(null, "Error listing Gemini models: ${e.message}")
        }
    }
}

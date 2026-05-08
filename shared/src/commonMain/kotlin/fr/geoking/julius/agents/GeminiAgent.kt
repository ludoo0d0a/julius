package fr.geoking.julius.agents

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import fr.geoking.julius.shared.action.ActionType
import fr.geoking.julius.shared.action.DeviceAction

/**
 * Pure HTTP-based GeminiAgent implementation using REST API.
 * Works on all platforms (Android, iOS, Desktop) using Ktor HTTP client.
 * Follows the Gemini API REST specification:
 * https://ai.google.dev/api/rest/v1beta/models/generateContent
 *
 * Uses system instructions and generation config for voice-assistant-optimized responses.
 * Supports free-tier models: gemini-1.5-flash, gemini-1.5-pro, gemini-2.0-flash.
 */
class GeminiAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash",
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val toolsEnabled: Boolean = false,
    private val systemInstruction: String = "You are Julius, a friendly voice assistant for Android and Android Auto. " +
        "Keep responses concise and natural for text-to-speech: short sentences, avoid bullet points or long lists. " +
        "Be helpful, conversational, and direct. When the user asks for actions (music, alarm, battery, volume, etc.), use the provided tools.",
    private val temperature: Float = 0.7f,
    private val maxOutputTokens: Int = 1024
) : ConversationalAgent {

    private val mutex = Mutex()

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
    private data class SystemInstructionPart(val text: String)

    @Serializable
    private data class SystemInstruction(val parts: List<SystemInstructionPart>)

    @Serializable
    private data class GenerationConfig(
        val temperature: Float? = null,
        val maxOutputTokens: Int? = null
    )

    @Serializable
    private data class GenerateContentRequest(
        val contents: List<Content>,
        val systemInstruction: SystemInstruction? = null,
        val generationConfig: GenerationConfig? = null,
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

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? {
        if (input.geminiKey.isBlank()) {
            return AgentSetupDescriptor.MissingApiKey(missingApiKeyMessage(input.selectedAgentDisplayName))
        }
        return null
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        // Following REST API specification from:
        // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference
        val url = "$baseUrl/models/$model:generateContent?key=$apiKey"
        val displayUrl = "$baseUrl/models/$model:generateContent"
        if (apiKey.isBlank()) {
            throw NetworkException(
                httpCode = null,
                message = "Gemini API key is required. Please set it in settings.",
                provider = "Gemini"
            )
        }

        try {

            val tools = if (toolsEnabled) {
                listOf(
                    Tool(listOf(
                        FunctionDeclaration("get_battery_level", "Get the current battery level percentage of the device", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("get_volume_levels", "Get the current system volume levels (media, alarm, ring)", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("play_music", "Play music or start a music app. Specify the song name, artist, or album if provided.", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("query", buildJsonObject {
                                    put("type", "string")
                                    put("description", "The song name, artist, or album to play. Leave empty to just open the player.")
                                })
                            })
                        }),
                        FunctionDeclaration("play_audiobook", "Play an audiobook or start an audiobook app", buildJsonObject { put("type", "object") }),
                        
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
                systemInstruction = SystemInstruction(parts = listOf(SystemInstructionPart(text = systemInstruction))),
                generationConfig = GenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = maxOutputTokens
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
                        httpCode = response.status.value,
                        message = "Error connecting to Gemini: ${errorRes.error.message}",
                        url = displayUrl,
                        provider = "Gemini"
                    )
                } catch (e: Exception) {
                    if (e is NetworkException) throw e
                    throw NetworkException(
                        httpCode = response.status.value,
                        message = "Error connecting to Gemini: HTTP ${response.status.value} - $responseBody",
                        url = displayUrl,
                        provider = "Gemini"
                    )
                }
            }

            if (responseBody.isBlank()) {
                throw NetworkException(
                    httpCode = response.status.value,
                    message = "Error connecting to Gemini: Received empty response body",
                    url = displayUrl,
                    provider = "Gemini"
                )
            }

            val res = json.decodeFromString<GenerateContentResponse>(responseBody)
            val candidate = res.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.find { it.text != null }?.text ?: ""

            val toolCalls = candidate?.content?.parts?.filter { it.functionCall != null }?.mapNotNull { p ->
                val fc = p.functionCall!!
                val type = ExtendedToolActionRegistry.apiToolNameToActionType(fc.name)

                val target = when (fc.name) {
                    "play_music" -> fc.args["query"]?.toString()?.removeSurrounding("\"")
                    else -> null
                }

                // Gemini function calls don't have explicit IDs in the same way as OpenAI in simple REST,
                // but we need one for our internal model.
                type?.let { ToolCall(fc.name, DeviceAction(it, target)) }
            }

            return AgentResponse(text, null, toolCalls = toolCalls) // Audio null = Use System TTS
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is NetworkException) throw e
            throw NetworkException(
                httpCode = null,
                message = "Error connecting to Gemini: ${e.message}",
                url = displayUrl,
                provider = "Gemini"
            )
        }
    }

    override suspend fun listModels(): String {
        val url = "$baseUrl/models?key=$apiKey"
        val displayUrl = "$baseUrl/models"
        if (apiKey.isBlank()) {
            throw NetworkException(
                httpCode = null,
                message = "Gemini API key is required. Please set it in settings.",
                provider = "Gemini"
            )
        }

        try {
            val response = client.get(url)
            val responseBody = response.bodyAsText()

            if (response.status.value != 200) {
                throw NetworkException(
                    httpCode = response.status.value,
                    message = "Error listing Gemini models: HTTP ${response.status.value} - $responseBody",
                    url = displayUrl,
                    provider = "Gemini"
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
            throw NetworkException(
                httpCode = null,
                message = "Error listing Gemini models: ${e.message}",
                url = displayUrl,
                provider = "Gemini"
            )
        }
    }
}

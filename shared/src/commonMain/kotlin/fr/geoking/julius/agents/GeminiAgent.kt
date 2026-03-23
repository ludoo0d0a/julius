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
import fr.geoking.julius.shared.ActionType
import fr.geoking.julius.shared.DeviceAction

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
        "Be helpful, conversational, and direct. When the user asks for actions (navigation, fuel, parking, music, weather, etc.), use the provided tools.",
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

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
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
                        FunctionDeclaration("get_location", "Get the user's current GPS location and address", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("show_map", "Open the map at the user's current location", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("get_battery_level", "Get the current battery level percentage of the device", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("get_volume_levels", "Get the current system volume levels (media, alarm, ring)", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("find_gas_stations_nearby", "Find nearby gas stations or fuel prices", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("find_parking_nearby", "Find nearby parking spaces", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("find_restaurants_nearby", "Find nearby restaurants", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("find_fastfood_nearby", "Find nearby fast food outlets", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("find_service_area_nearby", "Find nearby highway service areas or rest stops", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("get_traffic_info", "Get current traffic information and show traffic layer", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("get_weather", "Get current weather. Use when the user asks about weather or temperature. Pass location for a named place; omit for current GPS position.", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("location", buildJsonObject {
                                    put("type", "string")
                                    put("description", "City or place name. Empty for user's current location.")
                                })
                            })
                        }),
                        FunctionDeclaration("play_music", "Play music or start a music app", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("play_audiobook", "Play an audiobook or start an audiobook app", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("call_contact", "Call a specific contact or phone number", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("number", buildJsonObject {
                                    put("type", "string")
                                    put("description", "The phone number or contact name to call")
                                })
                            })
                            put("required", Json.parseToJsonElement("[\"number\"]"))
                        }),
                        FunctionDeclaration("find_nearest_hospital", "Find the nearest hospital or emergency room", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("request_roadside_assistance", "Request roadside assistance or breakdown service", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("emergency_call", "Initiate an emergency call (e.g., 112)", buildJsonObject { put("type", "object") }),
                        FunctionDeclaration("navigate_to", "Navigate to a specific destination", buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {
                                put("destination", buildJsonObject {
                                    put("type", "string")
                                    put("description", "The destination address or place name")
                                })
                            })
                            put("required", Json.parseToJsonElement("[\"destination\"]"))
                        })
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
                    "show_map" -> ActionType.SHOW_MAP
                    "get_battery_level" -> ActionType.GET_BATTERY_LEVEL
                    "get_volume_levels" -> ActionType.GET_VOLUME_LEVEL
                    "find_gas_stations_nearby" -> ActionType.FIND_GAS_STATIONS
                    "find_parking_nearby" -> ActionType.FIND_PARKING
                    "find_restaurants_nearby" -> ActionType.FIND_RESTAURANTS
                    "find_fastfood_nearby" -> ActionType.FIND_FASTFOOD
                    "find_service_area_nearby" -> ActionType.FIND_SERVICE_AREA
                    "get_traffic_info" -> ActionType.GET_TRAFFIC
                    "get_weather" -> ActionType.GET_WEATHER
                    "play_music" -> ActionType.PLAY_MUSIC
                    "play_audiobook" -> ActionType.PLAY_AUDIOBOOK
                    "call_contact" -> ActionType.CALL_CONTACT
                    "find_nearest_hospital" -> ActionType.FIND_HOSPITAL
                    "request_roadside_assistance" -> ActionType.ROADSIDE_ASSISTANCE
                    "emergency_call" -> ActionType.EMERGENCY_CALL
                    "navigate_to" -> ActionType.NAVIGATE
                    else -> null
                }

                val target = when (fc.name) {
                    "call_contact" -> fc.args["number"]?.toString()?.removeSurrounding("\"")
                    "navigate_to" -> fc.args["destination"]?.toString()?.removeSurrounding("\"")
                    "get_weather" -> when (val loc = fc.args["location"]) {
                        null, is JsonNull -> null
                        is JsonPrimitive -> loc.content.trim().takeIf { it.isNotBlank() }
                        else -> null
                    }
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

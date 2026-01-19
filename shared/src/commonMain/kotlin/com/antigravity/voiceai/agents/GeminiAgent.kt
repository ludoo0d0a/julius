package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException
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

/**
 * Pure HTTP-based GeminiAgent implementation using REST API.
 * Works on all platforms (Android, iOS, Desktop) using Ktor HTTP client.
 * Follows the Gemini API REST specification:
 * https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference
 */
class GeminiAgent(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
) : ConversationalAgent {

    init {
        // Configure client with JSON serialization if not already configured
        // The client should be pre-configured by the caller with ContentNegotiation
    }

    @Serializable
    private data class Part(
        val text: String? = null
    )

    @Serializable
    private data class Content(
        val parts: List<Part>,
        val role: String = "user"
    )

    @Serializable
    private data class GenerateContentRequest(
        val contents: List<Content>
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

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = input)),
                        role = "user"
                    )
                )
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
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I didn't get a response."

            return AgentResponse(text, null) // Audio null = Use System TTS
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

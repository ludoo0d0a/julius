package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException
import com.google.genai.Client
import com.google.genai.types.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class GeminiAgentLib(
    private val apiKey: String,
    private val model: String = "gemini-3-flash-preview"
) : ConversationalAgent {
    
    private val client: Client by lazy {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is required. Please set it in settings.")
        }
        Client.builder()
            .apiKey(apiKey)
            .build()
    }

    actual override suspend fun process(input: String): AgentResponse {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "Gemini API key is required. Please set it in settings.")
        }

        return try {
            // Use the official Google GenAI SDK as per documentation
            // Following: https://ai.google.dev/gemini-api/docs/text-generation#java
            val response: GenerateContentResponse = withContext(Dispatchers.IO) {
                client.models.generateContent(model, input, null)
            }

            val text = response.text() ?: "I didn't get a response."
            AgentResponse(text, null) // Audio null = Use System TTS
        } catch (e: Exception) {
            e.printStackTrace()
            throw NetworkException(null, "Error connecting to Gemini: ${e.message}")
        }
    }

    override suspend fun listModels(): String {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "Gemini API key is required. Please set it in settings.")
        }

        try {
            // Note: The official SDK may have different methods for listing models
            // For now, return a JSON array with the current model
            // This can be enhanced if the SDK provides a listModels method
            return "[{\"name\":\"$model\"}]"
        } catch (e: Exception) {
            e.printStackTrace()
            if (e is NetworkException) throw e
            throw NetworkException(null, "Error listing Gemini models: ${e.message}")
        }
    }
}

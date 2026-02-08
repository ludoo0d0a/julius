package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException

/**
 * iOS implementation of GeminiAgentLib.
 * Google GenAI SDK (Java) is not available on iOS.
 */
actual class GeminiAgentLib : ConversationalAgent {

    private val apiKey: String
    private val model: String

    actual constructor(apiKey: String, model: String) {
        this.apiKey = apiKey
        this.model = model
    }

    override suspend fun process(input: String): AgentResponse {
        throw NetworkException(
            null,
            "GeminiAgentLib (Google GenAI SDK) is not supported on iOS. Use GeminiAgent (HTTP-based) instead."
        )
    }

    override suspend fun listModels(): String {
        throw NetworkException(
            null,
            "GeminiAgentLib (Google GenAI SDK) is not supported on iOS. Use GeminiAgent (HTTP-based) instead."
        )
    }
}

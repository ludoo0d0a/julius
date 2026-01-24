package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException

/**
 * iOS implementation of GeminiAgentLib.
 * Google GenAI SDK (Java) is not available on iOS.
 */
actual class GeminiAgentLib actual constructor(
    private val apiKey: String,
    private val model: String
) : ConversationalAgent {

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

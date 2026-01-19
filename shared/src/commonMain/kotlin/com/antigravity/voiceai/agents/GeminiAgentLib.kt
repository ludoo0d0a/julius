package com.antigravity.voiceai.agents

import com.antigravity.voiceai.shared.NetworkException

/**
 * Expect declaration for platform-specific GeminiAgentLib implementation using Google GenAI SDK.
 * On Android and Desktop: Uses the official Google GenAI SDK.
 * On iOS: Not supported (SDK is Java-only).
 */
expect class GeminiAgentLib(
    apiKey: String,
    model: String = "gemini-3-flash-preview"
) : ConversationalAgent {
    override suspend fun process(input: String): AgentResponse
}

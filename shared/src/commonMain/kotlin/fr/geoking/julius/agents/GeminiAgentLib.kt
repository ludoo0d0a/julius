package fr.geoking.julius.agents

/**
 * Expect declaration for platform-specific GeminiAgentLib implementation using Google GenAI SDK.
 * On Android and Desktop: Uses the official Google GenAI SDK.
 * On iOS: Not supported (SDK is Java-only).
 * Actual implementations extend [ConversationalAgent].
 */
expect class GeminiAgentLib(
    apiKey: String,
    model: String = "gemini-3-flash-preview"
)

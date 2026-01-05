package com.antigravity.voiceai.agents

import io.ktor.client.HttpClient

class DeepgramAgent(
    private val client: HttpClient,
    private val deepgramKey: String
) : ConversationalAgent {
    override suspend fun process(input: String): AgentResponse {
        return AgentResponse("Deepgram not implemented yet (requires key)", null)
    }
}

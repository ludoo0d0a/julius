package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException

/**
 * Placeholder agent for providers that run locally on device but are not yet fully implemented.
 * This allows the UI to show the download helper and configuration.
 */
class LocalPlaceholderAgent(
    private val agentName: String,
    private val modelPath: String
) : ConversationalAgent {

    override suspend fun process(input: String): AgentResponse {
        throw NetworkException(null, "$agentName is not yet fully implemented for inference. Model at: $modelPath")
    }
}

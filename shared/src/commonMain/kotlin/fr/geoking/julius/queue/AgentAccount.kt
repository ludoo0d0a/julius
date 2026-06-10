package fr.geoking.julius.queue

import fr.geoking.julius.api.codingagent.CodingAgentBackend
import kotlinx.serialization.Serializable

@Serializable
data class AgentAccount(
    val id: String,
    val label: String,
    val backend: CodingAgentBackend,
    val apiKey: String,
    val enabled: Boolean = true,
)

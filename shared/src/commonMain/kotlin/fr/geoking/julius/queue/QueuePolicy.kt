package fr.geoking.julius.queue

import fr.geoking.julius.api.codingagent.CodingAgentBackend
import kotlinx.serialization.Serializable

@Serializable
data class QueuePolicy(
    val parallelLimit: Int = 3,
    val dailyLimitPerAccount: Int = 15,
    val queuePaused: Boolean = false,
    val autoMergeOnCiSuccess: Boolean = true,
)

fun defaultQueuePolicies(): Map<CodingAgentBackend, QueuePolicy> = CodingAgentBackend.entries.associateWith {
    QueuePolicy()
}

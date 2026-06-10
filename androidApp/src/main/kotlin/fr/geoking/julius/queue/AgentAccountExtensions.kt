package fr.geoking.julius.queue

import fr.geoking.julius.AppSettings
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import java.util.UUID

fun AppSettings.enabledAccountsFor(backend: CodingAgentBackend): List<AgentAccount> =
    agentAccounts.filter { it.backend == backend && it.enabled && it.apiKey.isNotBlank() }

fun AppSettings.julesApiKeys(): List<String> =
    enabledAccountsFor(CodingAgentBackend.JULES).map { it.apiKey }
        .ifEmpty { julesKeys }

fun AppSettings.queuePolicyFor(backend: CodingAgentBackend): QueuePolicy =
    queuePolicies[backend] ?: QueuePolicy()

fun migrateJulesKeysToAccounts(julesKeys: List<String>, anthropicKey: String): List<AgentAccount> {
    val accounts = mutableListOf<AgentAccount>()
    julesKeys.forEachIndexed { index, key ->
        if (key.isNotBlank()) {
            accounts.add(
                AgentAccount(
                    id = UUID.randomUUID().toString(),
                    label = "Jules Account ${index + 1}",
                    backend = CodingAgentBackend.JULES,
                    apiKey = key,
                )
            )
        }
    }
    if (anthropicKey.isNotBlank() && accounts.none { it.backend == CodingAgentBackend.CLAUDE_CODE }) {
        accounts.add(
            AgentAccount(
                id = UUID.randomUUID().toString(),
                label = "Claude Account 1",
                backend = CodingAgentBackend.CLAUDE_CODE,
                apiKey = anthropicKey,
            )
        )
    }
    return accounts
}

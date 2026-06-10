package fr.geoking.julius.queue

import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.persistence.AccountDailyUsageEntity
import fr.geoking.julius.persistence.JulesSessionEntity

class AccountAllocator {

    fun selectAccount(
        backend: CodingAgentBackend,
        accounts: List<AgentAccount>,
        policy: QueuePolicy,
        activeSessions: List<JulesSessionEntity>,
        dailyUsage: List<AccountDailyUsageEntity>,
        dayEpoch: Long,
    ): AgentAccount? {
        val enabled = accounts.filter { it.backend == backend && it.enabled && it.apiKey.isNotBlank() }
        if (enabled.isEmpty()) return null

        val usageByAccount = dailyUsage
            .filter { it.dayEpoch == dayEpoch }
            .associateBy { it.accountId }

        val activeByKey = activeSessions
            .filter { session ->
                session.sessionState !in TERMINAL_SESSION_STATES &&
                    !session.isFinished
            }
            .groupingBy { it.apiKey ?: "" }
            .eachCount()

        return enabled
            .filter { account ->
                val used = usageByAccount[account.id]?.startedCount ?: 0
                used < policy.dailyLimitPerAccount
            }
            .minWithOrNull(
                compareBy<AgentAccount> { activeByKey[it.apiKey] ?: 0 }
                    .thenBy { usageByAccount[it.id]?.startedCount ?: 0 },
            )
    }

    companion object {
        private val TERMINAL_SESSION_STATES = setOf("COMPLETED", "FAILED", "QUEUED_OFFLINE")
    }
}

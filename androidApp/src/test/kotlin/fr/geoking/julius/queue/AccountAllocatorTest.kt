package fr.geoking.julius.queue

import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.persistence.JulesSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountAllocatorTest {

    private val allocator = AccountAllocator()

    @Test
    fun selectAccount_picksLeastLoaded() {
        val accounts = listOf(
            AgentAccount("a1", "A1", CodingAgentBackend.JULES, "key1"),
            AgentAccount("a2", "A2", CodingAgentBackend.JULES, "key2"),
        )
        val sessions = listOf(
            activeSession("s1", "key1"),
            activeSession("s2", "key1"),
            activeSession("s3", "key2"),
        )
        val selected = allocator.selectAccount(
            backend = CodingAgentBackend.JULES,
            accounts = accounts,
            policy = QueuePolicy(),
            activeSessions = sessions,
            dailyUsage = emptyList(),
            dayEpoch = 0L,
        )
        assertEquals("a2", selected?.id)
    }

    @Test
    fun selectAccount_returnsNullWhenDailyCapReached() {
        val accounts = listOf(
            AgentAccount("a1", "A1", CodingAgentBackend.JULES, "key1"),
        )
        val selected = allocator.selectAccount(
            backend = CodingAgentBackend.JULES,
            accounts = accounts,
            policy = QueuePolicy(dailyLimitPerAccount = 15),
            activeSessions = emptyList(),
            dailyUsage = listOf(
                fr.geoking.julius.persistence.AccountDailyUsageEntity("a1", 0L, 15),
            ),
            dayEpoch = 0L,
        )
        assertNull(selected)
    }

    private fun activeSession(id: String, apiKey: String) = JulesSessionEntity(
        id = id,
        title = "t",
        prompt = "p",
        sourceName = "src",
        prUrl = null,
        prTitle = null,
        prState = null,
        prMergeable = null,
        sessionState = "ACTIVE",
        lastUpdated = 0L,
        apiKey = apiKey,
    )
}

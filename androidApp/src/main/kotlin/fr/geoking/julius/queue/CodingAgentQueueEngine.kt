package fr.geoking.julius.queue

import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.persistence.AccountDailyUsageDao
import fr.geoking.julius.persistence.AccountDailyUsageEntity
import fr.geoking.julius.persistence.FeatureDao
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.JulesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CodingAgentQueueEngine(
    private val settingsManager: SettingsManager,
    private val featureDao: FeatureDao,
    private val accountDailyUsageDao: AccountDailyUsageDao,
    private val featureRepository: FeatureRepository,
    private val julesRepository: JulesRepository,
    private val accountAllocator: AccountAllocator,
    private val gitHubLifecycle: FeatureGitHubLifecycle,
) {
    private val _status = MutableStateFlow(QueueStatus())
    val status: StateFlow<QueueStatus> = _status.asStateFlow()

    suspend fun tick() {
        val settings = settingsManager.settings.value
        val backend = settings.codingAgentBackend
        val policy = settings.queuePolicyFor(backend)
        val dayEpoch = currentDayEpochUtc()
        val githubToken = settings.githubApiKey

        pollActiveFeatures(githubToken, policy)
        if (!policy.queuePaused) {
            dequeuePending(backend, policy, dayEpoch)
        }
        refreshStatus(backend, policy, dayEpoch)
    }

    fun hasWorkToDo(): Boolean {
        val settings = settingsManager.settings.value
        val policy = settings.queuePolicyFor(settings.codingAgentBackend)
        val status = _status.value
        return status.pendingCount > 0 ||
            status.activeCount > 0 ||
            (!policy.queuePaused && status.pendingCount > 0)
    }

    suspend fun refreshStatusOnly() {
        val settings = settingsManager.settings.value
        val backend = settings.codingAgentBackend
        val policy = settings.queuePolicyFor(backend)
        refreshStatus(backend, policy, currentDayEpochUtc())
    }

    private suspend fun pollActiveFeatures(githubToken: String, policy: QueuePolicy) {
        val activeFeatures = featureDao.getActiveFeatures()
        for (feature in activeFeatures) {
            try {
                val sessions = julesRepository.getSessionsByFeature(feature.id)
                if (sessions.isEmpty()) continue

                // 1. Poll all non-finished sessions
                for (session in sessions) {
                    if (!session.isFinished) {
                        julesRepository.pollSessionStatus(session.id, githubToken)
                    }
                }

                // 2. Fetch updated sessions and recalculate aggregated status
                val updatedSessions = julesRepository.getSessionsByFeature(feature.id)
                val allFinished = updatedSessions.all { it.isFinished }
                val anyFailed = updatedSessions.any { it.sessionState == "FAILED" }
                val anyStarted = updatedSessions.any { it.sessionState !in setOf("QUEUED", "QUEUED_OFFLINE", null) }

                val newStatus = when {
                    allFinished && anyFailed -> "FAILED"
                    allFinished -> "COMPLETED"
                    anyStarted -> "IN_PROGRESS"
                    else -> feature.status
                }

                var currentFeature = feature
                if (newStatus != feature.status) {
                    currentFeature = feature.copy(status = newStatus, updatedAt = System.currentTimeMillis())
                    featureDao.updateFeature(currentFeature)
                }

                // 3. Process open PRs for auto-merge
                if (githubToken.isNotBlank()) {
                    for (session in updatedSessions) {
                        if (session.prUrl != null && session.prState == "open") {
                            gitHubLifecycle.processOpenPr(currentFeature, session, githubToken, policy.autoMergeOnCiSuccess)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to poll feature ${feature.id}", e)
            }
        }
    }

    private suspend fun dequeuePending(backend: CodingAgentBackend, policy: QueuePolicy, dayEpoch: Long) {
        val accounts = settingsManager.settings.value.agentAccounts
        if (accounts.none { it.backend == backend && it.enabled }) return

        val activeCount = featureDao.getQueuedOrInProgressCount()
        var slots = (policy.parallelLimit - activeCount).coerceAtLeast(0)
        if (slots == 0) return

        val dailyUsage = accountDailyUsageDao.getAllForDay(dayEpoch)
        val activeSessions = julesRepository.getAllActiveSessions()

        while (slots > 0) {
            val pending = featureDao.getNextPendingFeature() ?: break
            val account = accountAllocator.selectAccount(
                backend = backend,
                accounts = accounts,
                policy = policy,
                activeSessions = activeSessions,
                dailyUsage = dailyUsage,
                dayEpoch = dayEpoch,
            ) ?: break

            try {
                featureRepository.startFeature(pending.id, account)
                incrementDailyUsage(account.id, dayEpoch, dailyUsage)
                slots--
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start feature ${pending.id}", e)
                featureDao.updateFeature(
                    pending.copy(
                        status = "FAILED",
                        errorMessage = e.message,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                break
            }
        }
    }

    private suspend fun incrementDailyUsage(
        accountId: String,
        dayEpoch: Long,
        cache: List<AccountDailyUsageEntity>,
    ) {
        val existing = cache.find { it.accountId == accountId && it.dayEpoch == dayEpoch }
            ?: accountDailyUsageDao.getUsage(accountId, dayEpoch)
        val next = (existing?.startedCount ?: 0) + 1
        accountDailyUsageDao.upsert(AccountDailyUsageEntity(accountId, dayEpoch, next))
    }

    private suspend fun refreshStatus(backend: CodingAgentBackend, policy: QueuePolicy, dayEpoch: Long) {
        val settings = settingsManager.settings.value
        val dailyUsage = accountDailyUsageDao.getAllForDay(dayEpoch)
        val activeSessions = julesRepository.getAllActiveSessions()
        val activeByKey = activeSessions.groupingBy { it.apiKey ?: "" }.eachCount()

        val accountRows = settings.agentAccounts
            .filter { it.backend == backend }
            .map { account ->
                AccountQuotaRow(
                    accountId = account.id,
                    label = account.label,
                    usedToday = dailyUsage.find { it.accountId == account.id }?.startedCount ?: 0,
                    dailyLimit = policy.dailyLimitPerAccount,
                    activeSessions = activeByKey[account.apiKey] ?: 0,
                    enabled = account.enabled,
                )
            }

        _status.value = QueueStatus(
            paused = policy.queuePaused,
            activeCount = featureDao.getQueuedOrInProgressCount(),
            parallelLimit = policy.parallelLimit,
            pendingCount = featureDao.getPendingFeaturesCount(),
            accounts = accountRows,
        )
    }

    companion object {
        private const val TAG = "CodingAgentQueueEngine"
    }
}

package fr.geoking.julius.repository

import fr.geoking.julius.api.claude.ClaudeCodeClient
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.persistence.JulesActivityEntity
import fr.geoking.julius.persistence.JulesDao
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.persistence.JulesSourceEntity
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import java.time.OffsetDateTime
import java.time.Instant

class JulesRepository(
    private val context: Context,
    private val julesClient: JulesClient,
    private val claudeCodeClient: ClaudeCodeClient,
    private val githubClient: GitHubClient,
    private val julesDao: JulesDao,
    private val networkService: NetworkService,
    private val settingsManager: SettingsManager
) {
    private val syncSemaphore = kotlinx.coroutines.sync.Semaphore(3)

    private fun backend(): CodingAgentBackend = settingsManager.settings.value.codingAgentBackend

    private fun isClaudeBackend(): Boolean = backend() == CodingAgentBackend.CLAUDE_CODE

    private fun activeApiKey(): String? = if (isClaudeBackend()) {
        settingsManager.settings.value.anthropicApiKey.takeIf { it.isNotBlank() }
    } else {
        settingsManager.settings.value.julesKeys.firstOrNull()
    }

    fun isCodingAgentConfigured(apiKeys: List<String>): Boolean = if (isClaudeBackend()) {
        settingsManager.settings.value.anthropicApiKey.isNotBlank() &&
            settingsManager.settings.value.githubApiKey.isNotBlank()
    } else {
        apiKeys.isNotEmpty()
    }

    private suspend fun parseRepoSource(sourceName: String): Pair<String, String>? {
        val source = julesDao.getSources().find { it.name == sourceName }
        if (source?.owner != null && source.repo != null) return source.owner to source.repo
        val parts = sourceName.split("/")
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) return parts[0] to parts[1]
        return null
    }

    private suspend fun ensureClaudeSetup(apiKey: String): Pair<String, String> {
        var settings = settingsManager.settings.value
        var agentId = settings.claudeAgentId
        var agentVersion = settings.claudeAgentVersion
        var environmentId = settings.claudeEnvironmentId

        if (agentId.isBlank()) {
            val agent = claudeCodeClient.createAgent(apiKey)
            agentId = agent.id
            agentVersion = agent.version
            settings = settings.copy(claudeAgentId = agentId, claudeAgentVersion = agentVersion)
            settingsManager.saveSettings(settings)
        }
        if (environmentId.isBlank()) {
            val envName = "julius-coding-${System.currentTimeMillis()}"
            val env = claudeCodeClient.createEnvironment(apiKey, envName)
            environmentId = env.id
            settings = settings.copy(claudeEnvironmentId = environmentId)
            settingsManager.saveSettings(settings)
        }
        return agentId to environmentId
    }

    private fun filterSessionsForBackend(sessions: List<JulesSessionEntity>): List<JulesSessionEntity> =
        sessions.filter { backend().matchesSessionId(it.id) || it.isPendingOffline }

    suspend fun getSourcesCached(): List<JulesClient.JulesSource> {
        return julesDao.getSources().map {
            JulesClient.JulesSource(
                name = it.name,
                id = it.id,
                githubRepo = if (it.owner != null && it.repo != null) {
                    JulesClient.JulesGitHubRepo(it.owner, it.repo)
                } else null
            )
        }
    }

    fun getSources(apiKeys: List<String>): Flow<List<JulesClient.JulesSource>> = flow {
        // 1. Emit from cache
        val cached = getSourcesCached()
        if (cached.isNotEmpty()) {
            emit(cached)
        }

        // 2. Refresh if needed (e.g. once a month = 30 days)
        val lastUpdated = julesDao.getSources().firstOrNull()?.lastUpdated ?: 0L
        val ttlMs = 30L * 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastUpdated > ttlMs || cached.isEmpty()) {
            try {
                val allSources = mutableMapOf<String, JulesClient.JulesSource>()
                if (isClaudeBackend()) {
                    val githubToken = settingsManager.settings.value.githubApiKey
                    if (githubToken.isNotBlank()) {
                        val repos = githubClient.listUserRepositories(githubToken)
                        repos.forEach { repo ->
                            val name = repo.fullName.ifBlank { "${repo.owner.login}/${repo.name}" }
                            allSources[name] = JulesClient.JulesSource(
                                name = name,
                                id = name,
                                githubRepo = JulesClient.JulesGitHubRepo(
                                    owner = repo.owner.login,
                                    repo = repo.name
                                )
                            )
                        }
                    }
                } else {
                    coroutineScope {
                        apiKeys.map { key ->
                            async {
                                try {
                                    val resp = julesClient.listSources(key)
                                    synchronized(allSources) {
                                        resp.sources.forEach { src ->
                                            allSources[src.name] = src
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("JulesRepository", "Failed to load sources for a key", e)
                                }
                            }
                        }.awaitAll()
                    }
                }

                if (allSources.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val entities = allSources.values.map {
                        JulesSourceEntity(
                            name = it.name,
                            id = it.id,
                            owner = it.githubRepo?.owner,
                            repo = it.githubRepo?.repo,
                            lastUpdated = now
                        )
                    }
                    julesDao.clearSources()
                    julesDao.insertSources(entities)
                    emit(allSources.values.toList())
                }
            } catch (e: Exception) {
                android.util.Log.e("JulesRepository", "Failed to refresh sources", e)
            }
        }
    }

    fun getSessions(apiKeys: List<String>, sourceName: String, githubToken: String): Flow<List<JulesSessionEntity>> = flow {
        // 1. Emit from cache
        try {
            val cached = filterSessionsForBackend(julesDao.getSessionsBySource(sourceName))
            emit(cached)
        } catch (e: Exception) {
            emit(emptyList())
        }

        // 2. Fetch from network
        var anySuccess = false

        if (isClaudeBackend()) {
            val apiKey = activeApiKey()
            if (apiKey != null) {
                try {
                    val resp = claudeCodeClient.listSessions(apiKey)
                    val localForSource = julesDao.getSessionsBySource(sourceName)
                        .filter { backend().matchesSessionId(it.id) || it.isPendingOffline }
                    val knownIds = localForSource.map { it.id }.toSet()
                    val remoteSessions = resp.data.filter { it.id in knownIds }

                    val currentEntities = mutableListOf<JulesSessionEntity>()
                    for (session in remoteSessions) {
                        val existing = try { julesDao.getSession(session.id) } catch (e: Exception) { null }
                        val apiUpdateTime = try {
                            if (session.updatedAt.isNotBlank()) {
                                OffsetDateTime.parse(session.updatedAt).toInstant().toEpochMilli()
                            } else 0L
                        } catch (e: Exception) { 0L }
                        val newLastUpdated = maxOf(existing?.lastUpdated ?: 0L, apiUpdateTime).let {
                            if (it == 0L) System.currentTimeMillis() else it
                        }
                        currentEntities.add(
                            JulesSessionEntity(
                                id = session.id,
                                title = session.title.ifBlank { existing?.title ?: "Claude session" },
                                prompt = existing?.prompt ?: "",
                                sourceName = sourceName,
                                prUrl = existing?.prUrl,
                                prTitle = existing?.prTitle,
                                prId = existing?.prId,
                                prState = existing?.prState,
                                prMergeable = existing?.prMergeable,
                                sessionState = claudeCodeClient.mapSessionState(session.status),
                                url = claudeCodeClient.sessionUrl(session.id),
                                createTime = session.createdAt,
                                updateTime = session.updatedAt,
                                isArchived = existing?.isArchived ?: false,
                                lastUpdated = newLastUpdated,
                                apiKey = apiKey,
                                featureId = existing?.featureId
                            )
                        )
                    }
                    if (currentEntities.isNotEmpty()) {
                        julesDao.insertSessions(currentEntities)
                    }
                    anySuccess = true
                } catch (e: Exception) {
                    android.util.Log.e("JulesRepository", "Failed to fetch Claude sessions", e)
                }
            }
        } else {
            coroutineScope {
                apiKeys.map { key ->
                    async {
                        try {
                            val resp = julesClient.listSessions(key, pageSize = 50)
                            val sessions = resp.sessions.orEmpty().filter { it.sourceContext?.source == sourceName }
                            anySuccess = true

                            val currentEntities = mutableListOf<JulesSessionEntity>()
                            for (session in sessions) {
                                val sessionId = session.id.takeIf { it.isNotBlank() } ?: session.name
                                if (sessionId.isBlank()) continue

                                val pr = session.outputs?.firstOrNull()?.pullRequest
                                val existing = try { julesDao.getSession(sessionId) } catch (e: Exception) { null }

                                val apiUpdateTime = try {
                                    if (!session.updateTime.isNullOrBlank()) {
                                        OffsetDateTime.parse(session.updateTime).toInstant().toEpochMilli()
                                    } else 0L
                                } catch (e: Exception) { 0L }

                                val newLastUpdated = maxOf(existing?.lastUpdated ?: 0L, apiUpdateTime).let {
                                    if (it == 0L) System.currentTimeMillis() else it
                                }

                                currentEntities.add(JulesSessionEntity(
                                    id = sessionId,
                                    title = session.title,
                                    prompt = session.prompt,
                                    sourceName = sourceName,
                                    prUrl = pr?.url,
                                    prTitle = pr?.title,
                                    prId = pr?.id,
                                    prState = existing?.prState,
                                    prMergeable = existing?.prMergeable,
                                    sessionState = session.state,
                                    url = session.url,
                                    createTime = session.createTime,
                                    updateTime = session.updateTime,
                                    isArchived = existing?.isArchived ?: false,
                                    lastUpdated = newLastUpdated,
                                    apiKey = key,
                                    featureId = existing?.featureId
                                ))
                            }

                            val localSessionsForKey = julesDao.getSessionsBySourceAndKey(sourceName, key)
                            val currentIds = currentEntities.map { it.id }.toSet()
                            val toDelete = localSessionsForKey.filter { !currentIds.contains(it.id) && !it.isPendingOffline }

                            toDelete.forEach { julesDao.deleteSession(it.id) }
                            if (currentEntities.isNotEmpty()) {
                                julesDao.insertSessions(currentEntities)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("JulesRepository", "Failed to fetch sessions for key ${key.take(8)}...", e)
                        }
                    }
                }.awaitAll()
            }
        }

        if (anySuccess) {
            val entities = try {
                filterSessionsForBackend(julesDao.getSessionsBySource(sourceName))
            } catch (e: Exception) {
                emptyList()
            }
            emit(entities)

            // Update PR statuses in parallel
            coroutineScope {
                entities.filter { !it.prUrl.isNullOrBlank() }.map { entity ->
                    async { updatePrStatus(githubToken, entity) }
                }.awaitAll()
            }

            try {
                emit(julesDao.getSessionsBySource(sourceName))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    suspend fun updatePrStatus(githubToken: String, session: JulesSessionEntity) {
        val url = session.prUrl ?: return
        val prRef = parseGitHubPullRequestUrl(url) ?: return
        try {
            val detail = githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            val state = when {
                detail.merged -> "merged"
                detail.state == "closed" -> "closed"
                else -> "open"
            }
            try {
                // If status changed, update lastUpdated too
                val existing = julesDao.getSession(session.id)
                val statusChanged = existing != null && (existing.prState != state || existing.prMergeable != detail.mergeable)

                julesDao.updateSessionPrStatus(session.id, state, detail.mergeable)

                val branch = detail.head?.ref
                val repo = detail.repository?.fullName ?: detail.head?.repo?.fullName
                julesDao.updateSessionGitHubDetails(session.id, branch, repo)

                if (statusChanged) {
                    julesDao.updateSessionLastUpdated(session.id, System.currentTimeMillis())

                    // Add a local activity to log the status change
                    val activityId = "local_log_${java.util.UUID.randomUUID()}"
                    val now = OffsetDateTime.now().toString()
                    val logEntity = JulesActivityEntity(
                        id = activityId,
                        sessionId = session.id,
                        originator = "system",
                        text = "GitHub PR status changed to: ${state.uppercase()}",
                        timestamp = now,
                        sortTimestamp = System.currentTimeMillis(),
                        type = "github_log"
                    )
                    julesDao.insertActivities(listOf(logEntity))
                }
            } catch (e: Exception) {
                android.util.Log.e("JulesRepository", "Failed to update PR status in DB", e)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    suspend fun archiveSession(sessionId: String) {
        try {
            val existing = julesDao.getSession(sessionId)
            val key = existing?.apiKey ?: activeApiKey()
            if (key != null && !sessionId.startsWith("offline_")) {
                try {
                    if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                        claudeCodeClient.archiveSession(key, sessionId)
                    } else {
                        julesClient.deleteSession(key, sessionId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JulesRepository", "Remote session deletion failed for $sessionId", e)
                }
            }
            julesDao.archiveSession(sessionId)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to archive session $sessionId", e)
        }
    }

    suspend fun archiveCompletedSessions(sourceName: String? = null) {
        try {
            val completed = if (sourceName != null) {
                julesDao.getCompletedSessions(sourceName)
            } else {
                julesDao.getAllCompletedSessions()
            }
            if (completed.isEmpty()) return

            coroutineScope {
                completed.map { session ->
                    async {
                        val key = session.apiKey ?: activeApiKey()
                        if (key != null && !session.id.startsWith("offline_")) {
                            try {
                                if (isClaudeBackend() || session.id.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                                    claudeCodeClient.archiveSession(key, session.id)
                                } else {
                                    julesClient.deleteSession(key, session.id)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("JulesRepository", "Remote bulk session deletion failed for ${session.id}", e)
                            }
                        }
                    }
                }.awaitAll()
            }

            julesDao.archiveSessions(completed.map { it.id })
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to archive completed sessions", e)
        }
    }

    suspend fun updateSessionLastUpdated(sessionId: String, lastUpdated: Long) {
        try {
            julesDao.updateSessionLastUpdated(sessionId, lastUpdated)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to update session lastUpdated", e)
        }
    }

    suspend fun linkSessionToFeature(sessionId: String, featureId: String?) {
        try {
            julesDao.updateSessionFeature(sessionId, featureId)
            julesDao.updateSessionLastUpdated(sessionId, System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to link session $sessionId to feature $featureId", e)
        }
    }

    suspend fun createSession(apiKeys: List<String>, prompt: String, source: String, title: String, featureId: String? = null): String {
        val isOnline = networkService.status.value.isConnected
        if (isOnline) {
            if (isClaudeBackend()) {
                val apiKey = settingsManager.settings.value.anthropicApiKey.takeIf { it.isNotBlank() }
                    ?: throw Exception("Anthropic API key required for Claude Code")
                val githubToken = settingsManager.settings.value.githubApiKey.takeIf { it.isNotBlank() }
                    ?: throw Exception("GitHub token required for Claude Code")
                val (owner, repo) = parseRepoSource(source)
                    ?: throw Exception("Invalid repository source: $source")
                val (agentId, environmentId) = ensureClaudeSetup(apiKey)
                val agentVersion = settingsManager.settings.value.claudeAgentVersion
                val session = claudeCodeClient.createSession(
                    apiKey = apiKey,
                    agentId = agentId,
                    agentVersion = agentVersion,
                    environmentId = environmentId,
                    prompt = prompt,
                    owner = owner,
                    repo = repo,
                    githubToken = githubToken,
                    title = title
                )
                val entity = JulesSessionEntity(
                    id = session.id,
                    title = session.title.ifBlank { title },
                    prompt = prompt,
                    sourceName = source,
                    prUrl = null,
                    prTitle = null,
                    prId = null,
                    prState = null,
                    prMergeable = null,
                    sessionState = claudeCodeClient.mapSessionState(session.status),
                    url = claudeCodeClient.sessionUrl(session.id),
                    createTime = session.createdAt,
                    updateTime = session.updatedAt,
                    isArchived = false,
                    lastUpdated = System.currentTimeMillis(),
                    apiKey = apiKey,
                    featureId = featureId
                )
                julesDao.insertSessions(listOf(entity))
                return session.id
            }

            val apiKey = apiKeys.firstOrNull() ?: throw Exception("No API key available")
            val session = julesClient.createSession(
                apiKey = apiKey,
                prompt = prompt,
                source = source,
                title = title
            )
            val entity = JulesSessionEntity(
                id = session.id,
                title = session.title,
                prompt = session.prompt,
                sourceName = source,
                prUrl = session.outputs?.firstOrNull()?.pullRequest?.url,
                prTitle = session.outputs?.firstOrNull()?.pullRequest?.title,
                prId = session.outputs?.firstOrNull()?.pullRequest?.id,
                prState = null,
                prMergeable = null,
                sessionState = session.state,
                url = session.url,
                createTime = session.createTime,
                updateTime = session.updateTime,
                isArchived = false,
                lastUpdated = System.currentTimeMillis(),
                apiKey = apiKey,
                featureId = featureId
            )
            julesDao.insertSessions(listOf(entity))
            return session.id
        } else {
            val tempId = "offline_${java.util.UUID.randomUUID()}"
            val entity = JulesSessionEntity(
                id = tempId,
                title = title,
                prompt = prompt,
                sourceName = source,
                prUrl = null,
                prTitle = null,
                prState = null,
                prMergeable = null,
                sessionState = "QUEUED_OFFLINE",
                isArchived = false,
                lastUpdated = System.currentTimeMillis(),
                isPendingOffline = true,
                queuedAt = System.currentTimeMillis(),
                featureId = featureId
            )
            julesDao.insertSessions(listOf(entity))
            scheduleSync()
            return tempId
        }
    }

    suspend fun sendMessage(sessionId: String, prompt: String, apiKey: String? = null) {
        val isOnline = networkService.status.value.isConnected
        if (isOnline && !sessionId.startsWith("offline_")) {
            val existing = julesDao.getSession(sessionId)
            val key = apiKey ?: existing?.apiKey ?: activeApiKey()
                ?: throw Exception("No API key available")

            if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                claudeCodeClient.sendMessage(key, sessionId, prompt)
            } else {
                julesClient.sendMessage(key, sessionId, prompt)
            }
            julesDao.updateSessionLastUpdated(sessionId, System.currentTimeMillis())
        } else {
            val activityId = "offline_act_${java.util.UUID.randomUUID()}"
            val now = OffsetDateTime.now().toString()
            val entity = JulesActivityEntity(
                id = activityId,
                sessionId = sessionId,
                originator = "user",
                text = prompt,
                timestamp = now,
                sortTimestamp = System.currentTimeMillis(),
                isPendingOffline = true
            )
            julesDao.insertActivities(listOf(entity))
            julesDao.updateSessionLastUpdated(sessionId, System.currentTimeMillis())
            scheduleSync()
        }
    }

    private fun scheduleSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<fr.geoking.julius.worker.JulesSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to schedule sync", e)
        }
    }

    suspend fun syncOfflineData() = coroutineScope {
        if (!isCodingAgentConfigured(settingsManager.settings.value.julesKeys)) return@coroutineScope
        val apiKey = activeApiKey() ?: return@coroutineScope

        // 1. Sync pending sessions
        val pendingSessions = julesDao.getPendingOfflineSessions()
        for (session in pendingSessions) {
            async {
                syncSemaphore.withPermit {
                    try {
                        val newSessionId = if (isClaudeBackend()) {
                            val githubToken = settingsManager.settings.value.githubApiKey
                            val (owner, repo) = parseRepoSource(session.sourceName)
                                ?: return@withPermit
                            val (agentId, environmentId) = ensureClaudeSetup(apiKey)
                            val agentVersion = settingsManager.settings.value.claudeAgentVersion
                            val created = claudeCodeClient.createSession(
                                apiKey = apiKey,
                                agentId = agentId,
                                agentVersion = agentVersion,
                                environmentId = environmentId,
                                prompt = session.prompt,
                                owner = owner,
                                repo = repo,
                                githubToken = githubToken,
                                title = session.title
                            )
                            val entity = JulesSessionEntity(
                                id = created.id,
                                title = created.title.ifBlank { session.title },
                                prompt = session.prompt,
                                sourceName = session.sourceName,
                                prUrl = null,
                                prTitle = null,
                                prId = null,
                                prState = null,
                                prMergeable = null,
                                sessionState = claudeCodeClient.mapSessionState(created.status),
                                url = claudeCodeClient.sessionUrl(created.id),
                                createTime = created.createdAt,
                                updateTime = created.updatedAt,
                                isArchived = false,
                                lastUpdated = System.currentTimeMillis(),
                                isPendingOffline = false,
                                queuedAt = null,
                                apiKey = apiKey,
                                featureId = session.featureId
                            )
                            julesDao.insertSessions(listOf(entity))
                            created.id
                        } else {
                            val created = julesClient.createSession(
                                apiKey = apiKey,
                                prompt = session.prompt,
                                source = session.sourceName,
                                title = session.title
                            )
                            val updatedEntity = JulesSessionEntity(
                                id = created.id,
                                title = created.title,
                                prompt = created.prompt,
                                sourceName = session.sourceName,
                                prUrl = created.outputs?.firstOrNull()?.pullRequest?.url,
                                prTitle = created.outputs?.firstOrNull()?.pullRequest?.title,
                                prId = created.outputs?.firstOrNull()?.pullRequest?.id,
                                prState = null,
                                prMergeable = null,
                                sessionState = created.state,
                                url = created.url,
                                createTime = created.createTime,
                                updateTime = created.updateTime,
                                isArchived = false,
                                lastUpdated = System.currentTimeMillis(),
                                isPendingOffline = false,
                                queuedAt = null,
                                apiKey = apiKey,
                                featureId = session.featureId
                            )
                            julesDao.insertSessions(listOf(updatedEntity))
                            created.id
                        }
                        julesDao.updateActivitiesSessionId(session.id, newSessionId)
                        julesDao.deleteSession(session.id)

                        syncPendingActivities(apiKey, newSessionId)
                    } catch (e: Exception) {
                        android.util.Log.e("JulesRepository", "Failed to sync offline session ${session.id}", e)
                    }
                }
            }
        }

        // 2. Sync pending activities for already online sessions
        val pendingActivities = julesDao.getPendingOfflineActivities()
        val sessionIds = pendingActivities.map { it.sessionId }.distinct()
            .filter { !it.startsWith("offline_") }

        for (sessionId in sessionIds) {
            async {
                syncSemaphore.withPermit {
                    val existing = julesDao.getSession(sessionId)
                    val key = existing?.apiKey ?: activeApiKey()
                    if (key != null) {
                        syncPendingActivities(key, sessionId)
                    }
                }
            }
        }
    }

    private suspend fun syncPendingActivities(apiKey: String, sessionId: String) {
        val activities = julesDao.getActivitiesBySession(sessionId).filter { it.isPendingOffline }
        for (activity in activities) {
            try {
                if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                    claudeCodeClient.sendMessage(apiKey, sessionId, activity.text)
                } else {
                    julesClient.sendMessage(apiKey, sessionId, activity.text)
                }
                // Mark as synced by updating the record or deleting and letting fetch replace it
                val synced = activity.copy(isPendingOffline = false)
                julesDao.insertActivities(listOf(synced))
            } catch (e: Exception) {
                android.util.Log.e("JulesRepository", "Failed to sync activity ${activity.id}", e)
                break // Stop for this session if one fails (keep order)
            }
        }
    }

    /**
     * Polls the status of a specific session and its associated PR.
     * Refreshes PR details from GitHub and session details (for new PR URLs) from Jules.
     */
    suspend fun pollSessionStatus(sessionId: String, githubToken: String) {
        try {
            var existing = julesDao.getSession(sessionId)
            val apiKey = existing?.apiKey ?: activeApiKey() ?: return

            if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                val session = claudeCodeClient.getSession(apiKey, sessionId)
                val events = claudeCodeClient.listEvents(apiKey, sessionId).data
                val prUrl = claudeCodeClient.findPullRequestUrl(events)
                val mappedState = claudeCodeClient.mapSessionState(session.status)
                existing = julesDao.getSession(sessionId)

                if (existing != null) {
                    val statusChanged = existing.sessionState != mappedState
                    if (statusChanged) {
                        julesDao.updateSessionState(sessionId, mappedState)
                    }
                    if (prUrl != null && existing.prUrl != prUrl) {
                        val updated = existing.copy(
                            prUrl = prUrl,
                            sessionState = mappedState,
                            url = claudeCodeClient.sessionUrl(sessionId),
                            createTime = session.createdAt,
                            updateTime = session.updatedAt,
                            lastUpdated = System.currentTimeMillis()
                        )
                        julesDao.insertSessions(listOf(updated))
                        updatePrStatus(githubToken, updated)
                    } else {
                        if (statusChanged) {
                            julesDao.updateSessionLastUpdated(sessionId, System.currentTimeMillis())
                        }
                        if (existing.prUrl != null) {
                            updatePrStatus(githubToken, existing)
                        }
                    }
                }
                return
            }

            val session = julesClient.getSession(apiKey, sessionId)
            val pr = session.outputs?.firstOrNull()?.pullRequest
            existing = julesDao.getSession(sessionId)

            if (existing != null) {
                val statusChanged = existing.sessionState != session.state
                if (statusChanged) {
                    julesDao.updateSessionState(sessionId, session.state)
                }

                if (pr?.url != null && existing.prUrl != pr.url) {
                    val updated = existing.copy(
                        prUrl = pr.url,
                        prTitle = pr.title,
                        prId = pr.id,
                        sessionState = session.state,
                        url = session.url,
                        createTime = session.createTime,
                        updateTime = session.updateTime,
                        lastUpdated = System.currentTimeMillis()
                    )
                    julesDao.insertSessions(listOf(updated))
                    updatePrStatus(githubToken, updated)
                } else {
                    if (statusChanged) {
                        julesDao.updateSessionLastUpdated(sessionId, System.currentTimeMillis())
                    }
                    if (existing.prUrl != null) {
                        updatePrStatus(githubToken, existing)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "pollSessionStatus failed for $sessionId", e)
        }
    }

    fun getActivities(sessionId: String): Flow<List<JulesChatItem>> = flow {
        val existingSession = try { julesDao.getSession(sessionId) } catch (e: Exception) { null }
        val apiKey = existingSession?.apiKey ?: activeApiKey()

        // 1. Emit from cache
        try {
            val cached = julesDao.getActivitiesBySession(sessionId)
            if (cached.isNotEmpty()) {
                // Re-apply grouping logic on cached entities if needed, but for now we reconstruct from flattened entities
                val items = mutableListOf<JulesChatItem>()
                var currentAgentGroup: JulesChatItem.AgentMessage? = null

                fun flush() {
                    currentAgentGroup?.let { items.add(it) }
                    currentAgentGroup = null
                }

                for (a in cached.sortedBy { it.timestamp }) {
                    if (a.originator == "user") {
                        flush()
                        items.add(JulesChatItem.UserMessage(a.id, a.timestamp, a.text))
                    } else {
                        val subItem = JulesChatItem.AgentSubItem(a.id, a.timestamp, a.text, a.type)
                        if (a.type == "progress") {
                            if (currentAgentGroup != null && currentAgentGroup!!.title == "Progress") {
                                currentAgentGroup = currentAgentGroup!!.copy(
                                    subItems = currentAgentGroup!!.subItems + subItem
                                )
                            } else {
                                flush()
                                currentAgentGroup = JulesChatItem.AgentMessage(
                                    id = a.id,
                                    createTime = a.timestamp,
                                    title = "Progress",
                                    subItems = listOf(subItem)
                                )
                            }
                        } else {
                            flush()
                            items.add(JulesChatItem.AgentMessage(
                                id = a.id,
                                createTime = a.timestamp,
                                title = a.text,
                                subItems = listOf(subItem)
                            ))
                        }
                    }
                }
                flush()
                emit(items)
            }
        } catch (e: Exception) {
            // Ignore cache error
        }

        // 2. Fetch from network
        if (apiKey != null) {
        try {
            if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                val resp = claudeCodeClient.listEvents(apiKey, sessionId)
                val prUrl = claudeCodeClient.findPullRequestUrl(resp.data)
                if (prUrl != null) {
                    val existing = julesDao.getSession(sessionId)
                    if (existing != null && existing.prUrl != prUrl) {
                        val updated = existing.copy(prUrl = prUrl, lastUpdated = System.currentTimeMillis())
                        julesDao.insertSessions(listOf(updated))
                        val githubToken = settingsManager.settings.value.githubApiKey
                        if (githubToken.isNotBlank()) {
                            try {
                                updatePrStatus(githubToken, updated)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }

                val items = claudeCodeClient.eventsToChatItems(resp.data)
                val entities = resp.data.mapNotNull { event ->
                    val text = claudeCodeClient.extractText(event)
                    if (text.isBlank() && event.type !in listOf("session.status_terminated")) return@mapNotNull null
                    val originator = if (event.type == "user.message") "user" else "agent"
                    val type = when {
                        event.type in setOf("agent.tool_use", "agent.mcp_tool_use", "session.status_running") -> "progress"
                        event.type == "session.status_terminated" -> "completion"
                        event.type == "user.message" -> "user_message"
                        else -> "agent_message"
                    }
                    val ts = event.processedAt ?: ""
                    val sortTs = try {
                        if (ts.isNotBlank()) OffsetDateTime.parse(ts).toInstant().toEpochMilli() else 0L
                    } catch (e: Exception) { 0L }
                    JulesActivityEntity(
                        id = event.id.ifBlank { "claude_evt_${event.type}_${sortTs}" },
                        sessionId = sessionId,
                        originator = originator,
                        text = text.ifBlank { event.type },
                        timestamp = ts,
                        sortTimestamp = sortTs,
                        type = type,
                        activityJson = Json.encodeToString(ClaudeCodeClient.ClaudeEvent.serializer(), event)
                    )
                }
                try {
                    julesDao.clearActivitiesBySession(sessionId)
                    julesDao.insertActivities(entities)
                } catch (e: Exception) {
                    // Ignore DB error
                }
                emit(items)
                return@flow
            }

            val resp = julesClient.listActivities(apiKey, sessionId, pageSize = 50)

            // Sync PR info from activities to session if found
            for (activity in resp.activities) {
                val pr = activity.outputs?.firstOrNull()?.pullRequest ?: activity.sessionCompleted?.outputs?.firstOrNull()?.pullRequest
                if (pr?.url != null) {
                    val existing = julesDao.getSession(sessionId)
                    if (existing != null && existing.prUrl != pr.url) {
                        val updated = existing.copy(
                            prUrl = pr.url,
                            prTitle = pr.title,
                            prId = pr.id,
                            lastUpdated = System.currentTimeMillis()
                        )
                        julesDao.insertSessions(listOf(updated))
                        val githubToken = settingsManager.settings.value.githubApiKey
                        if (githubToken.isNotBlank()) {
                            try {
                                updatePrStatus(githubToken, updated)
                            } catch (e: Exception) {
                                // Ignore GitHub update errors here
                            }
                        }
                    }
                }
            }

            val items = julesClient.activitiesToChatItems(resp.activities)

            val entities = mutableListOf<JulesActivityEntity>()
            for (activity in resp.activities) {
                val a = activity
                val text = julesClient.extractText(a)
                val type = when {
                    a.planGenerated != null -> "plan"
                    a.progressUpdated != null -> "progress"
                    a.sessionCompleted != null -> "completion"
                    a.userMessaged != null || a.messageSent != null -> "user_message"
                    a.agentMessaged != null -> "agent_message"
                    a.sessionFailed != null -> "failure"
                    else -> "other"
                }
                val ts = a.createTime
                val sortTs = try { OffsetDateTime.parse(ts).toInstant().toEpochMilli() } catch (e: Exception) { 0L }

                entities.add(JulesActivityEntity(
                    id = a.id,
                    sessionId = sessionId,
                    originator = a.originator,
                    text = text,
                    timestamp = ts,
                    sortTimestamp = sortTs,
                    type = type,
                    artifactsJson = if (a.artifacts != null) Json.encodeToString(a.artifacts) else null,
                    activityJson = Json.encodeToString(JulesClient.JulesActivity.serializer(), a)
                ))
            }

            try {
                julesDao.clearActivitiesBySession(sessionId)
                julesDao.insertActivities(entities)
            } catch (e: Exception) {
                // Ignore DB error
            }

            emit(items)
        } catch (e: Exception) {
            // Ignore network/processing error
        }
        }
    }

    suspend fun mergePr(githubToken: String, sessionId: String, prUrl: String, deleteBranch: Boolean = false): Result<Unit> {
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            val prDetail = if (deleteBranch) githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number) else null
            githubClient.mergePullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)

            val headRef = prDetail?.head?.ref
            if (deleteBranch && headRef != null) {
                try {
                    githubClient.deleteBranch(githubToken, prRef.owner, prRef.repo, headRef)
                } catch (e: Exception) {
                    android.util.Log.e("JulesRepository", "Failed to delete branch after merge", e)
                }
            }

            val session = julesDao.getSession(sessionId)
            if (session != null) {
                updatePrStatus(githubToken, session)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBranch(githubToken: String, owner: String, repo: String, branch: String): Result<Unit> {
        return try {
            githubClient.deleteBranch(githubToken, owner, repo, branch)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConflictingFiles(githubToken: String, prUrl: String): Result<List<String>> {
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            val files = githubClient.getPullRequestFiles(githubToken, prRef.owner, prRef.repo, prRef.number)
            val detail = githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            val headRef = detail.head?.ref ?: return Result.failure(Exception("Head ref not found"))

            val conflictingFiles = mutableListOf<String>()
            for (file in files) {
                val contentResp = githubClient.getFileContent(githubToken, prRef.owner, prRef.repo, file.filename, headRef)
                val content = contentResp.content?.let {
                    if (contentResp.encoding == "base64") {
                        android.util.Base64.decode(it.replace("\n", ""), android.util.Base64.DEFAULT).decodeToString()
                    } else it
                } ?: ""

                if (content.contains("<<<<<<<") && content.contains("=======") && content.contains(">>>>>>>")) {
                    conflictingFiles.add(file.filename)
                }
            }
            Result.success(conflictingFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class Conflict(
        val fullMatch: String,
        val mine: String,
        val incoming: String,
        val startIndex: Int,
        val endIndex: Int
    )

    fun parseConflicts(content: String): List<Conflict> {
        val regex = Regex("<<<<<<< .*?\r?\n(.*?)\r?\n=======?\r?\n(.*?)\r?\n>>>>>>> .*?\r?\n", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(content).map { match ->
            Conflict(
                fullMatch = match.value,
                mine = match.groupValues[1],
                incoming = match.groupValues[2],
                startIndex = match.range.first,
                endIndex = match.range.last + 1
            )
        } .toList()
    }

    suspend fun getFileContent(githubToken: String, prUrl: String, path: String): Result<Pair<String, String>> {
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            val detail = githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            val headRef = detail.head?.ref ?: return Result.failure(Exception("Head ref not found"))
            val contentResp = githubClient.getFileContent(githubToken, prRef.owner, prRef.repo, path, headRef)
            val content = contentResp.content?.let {
                if (contentResp.encoding == "base64") {
                    android.util.Base64.decode(it.replace("\n", ""), android.util.Base64.DEFAULT).decodeToString()
                } else it
            } ?: ""
            Result.success(content to contentResp.sha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveResolvedFile(
        githubToken: String,
        prUrl: String,
        path: String,
        content: String,
        sha: String
    ): Result<Unit> {
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            val detail = githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            val headRef = detail.head?.ref ?: return Result.failure(Exception("Head ref not found"))
            val contentBase64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            githubClient.updateFileContent(
                githubToken,
                prRef.owner,
                prRef.repo,
                path,
                contentBase64,
                "Resolve conflicts in $path",
                sha,
                headRef
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closePr(githubToken: String, prUrl: String): Result<Unit> {
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            githubClient.closePullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPrDetails(githubToken: String, prUrl: String): Result<GitHubClient.GitHubPullRequestDetail> {
        val prRef = fr.geoking.julius.api.github.parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            val detail = githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGitHubResourceDetails(githubToken: String, url: String): Result<GitHubClient.GitHubPullRequestDetail?> {
        val prRef = fr.geoking.julius.api.github.parseGitHubPullRequestUrl(url)
        if (prRef != null) {
            return getPrDetails(githubToken, url)
        }

        val branchRef = fr.geoking.julius.api.github.parseGitHubBranchUrl(url)
        if (branchRef != null) {
            return try {
                // Find open PR for this branch
                val prs = githubClient.listPullRequests(githubToken, branchRef.owner, branchRef.repo, state = "open", head = "${branchRef.owner}:${branchRef.branch}")
                if (prs.isNotEmpty()) {
                    getPrDetails(githubToken, prs.first().htmlUrl)
                } else {
                    Result.success(null) // No PR found for this branch
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        return Result.failure(Exception("Unsupported GitHub URL"))
    }

    suspend fun createPullRequest(
        githubToken: String,
        owner: String,
        repo: String,
        head: String,
        base: String,
        title: String,
        body: String?
    ): Result<GitHubClient.GitHubPullRequestDetail> {
        return try {
            val detail = githubClient.createPullRequest(githubToken, owner, repo, title, head, base, body)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNetworkService() = networkService

    suspend fun getSession(sessionId: String): JulesSessionEntity? {
        return julesDao.getSession(sessionId)
    }

    suspend fun getSessionsByFeature(featureId: String): List<JulesSessionEntity> {
        return julesDao.getSessionsByFeature(featureId)
    }

    suspend fun getActivitiesBySession(sessionId: String): List<JulesActivityEntity> {
        return julesDao.getActivitiesBySession(sessionId)
    }

    /** Daily session quota for the Jules UI. Null when the API does not expose usage. */
    suspend fun getUsageQuota(apiKeys: List<String>): JulesQuota? {
        if (apiKeys.isEmpty()) return null
        // Jules public API has no documented quota endpoint; UI hides the quota row when null.
        return null
    }

    suspend fun approvePlan(apiKey: String, sessionId: String) {
        if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) return
        julesClient.approvePlan(apiKey, sessionId)
    }

    suspend fun pauseSession(sessionId: String) {
        if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) return
        try {
            val session = julesDao.getSession(sessionId) ?: return
            val apiKey = session.apiKey ?: activeApiKey() ?: return
            julesClient.pauseSession(apiKey, sessionId)
            pollSessionStatus(sessionId, settingsManager.settings.value.githubApiKey)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to pause session $sessionId", e)
            throw e
        }
    }

    suspend fun resumeSession(sessionId: String) {
        if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) return
        try {
            val session = julesDao.getSession(sessionId) ?: return
            val apiKey = session.apiKey ?: activeApiKey() ?: return
            julesClient.resumeSession(apiKey, sessionId)
            pollSessionStatus(sessionId, settingsManager.settings.value.githubApiKey)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to resume session $sessionId", e)
            throw e
        }
    }
}

/** Used/limit for optional daily session display in [JulesScreen]. */
data class JulesQuota(val used: Int, val limit: Int)

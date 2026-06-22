package fr.geoking.julius.repository

import fr.geoking.julius.api.claude.ClaudeCodeClient
import fr.geoking.julius.api.codingagent.CodingAgentBackend
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.api.jules.primaryPullRequest
import fr.geoking.julius.queue.currentDayEpochUtc
import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.queue.AgentAccount
import fr.geoking.julius.queue.enabledAccountsFor
import fr.geoking.julius.queue.julesApiKeys
import fr.geoking.julius.queue.queuePolicyFor
import fr.geoking.julius.debug.DbCacheDebugTracker
import fr.geoking.julius.debug.DbEntityKind
import fr.geoking.julius.persistence.AccountDailyUsageDao
import fr.geoking.julius.persistence.JulesActivityEntity
import fr.geoking.julius.persistence.JulesDao
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.persistence.JulesSourceEntity
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import java.time.OffsetDateTime
import java.time.Instant

private const val SOURCES_CACHE_TTL_MS = 5 * 60 * 1000L

class JulesRepository(
    private val context: Context,
    private val julesClient: JulesClient,
    private val claudeCodeClient: ClaudeCodeClient,
    private val githubClient: GitHubClient,
    private val julesDao: JulesDao,
    private val accountDailyUsageDao: AccountDailyUsageDao,
    private val networkService: NetworkService,
    private val settingsManager: SettingsManager,
    private val dbCacheDebugTracker: DbCacheDebugTracker,
) {
    private val sourcesRefreshMutex = Mutex()

    private fun backend(): CodingAgentBackend = settingsManager.settings.value.codingAgentBackend

    private suspend fun trackProjectReplace(
        previous: Map<String, JulesSourceEntity>,
        entities: List<JulesSourceEntity>,
    ) {
        var saved = 0
        var updated = 0
        for (entity in entities) {
            if (previous.containsKey(entity.name)) updated++ else saved++
        }
        val deleted = (previous.keys - entities.map { it.name }.toSet()).size
        dbCacheDebugTracker.record(DbEntityKind.PROJECTS, saved = saved, updated = updated, deleted = deleted)
    }

    private suspend fun trackSessionUpserts(entities: List<JulesSessionEntity>) {
        var saved = 0
        var updated = 0
        for (entity in entities) {
            val existing = runCatching { julesDao.getSession(entity.id) }.getOrNull()
            if (existing == null) saved++ else updated++
        }
        dbCacheDebugTracker.record(DbEntityKind.SESSIONS, saved = saved, updated = updated)
    }

    private suspend fun trackSessionDeletes(count: Int) {
        if (count > 0) dbCacheDebugTracker.record(DbEntityKind.SESSIONS, deleted = count)
    }

    private suspend fun trackActivityReplace(previousCount: Int, inserted: Int) {
        dbCacheDebugTracker.record(
            DbEntityKind.ACTIVITIES,
            saved = inserted,
            deleted = previousCount,
        )
    }

    private fun isClaudeBackend(): Boolean = backend() == CodingAgentBackend.CLAUDE_CODE

    private data class SessionPullRequestFields(
        val url: String?,
        val title: String?,
        val description: String?,
        val id: String?,
        val repo: String?,
    )

    /** Maps Session.outputs[].pullRequest (getSession / listSessions) to local PR fields. */
    private fun pullRequestFields(pr: JulesClient.JulesPullRequest?): SessionPullRequestFields {
        if (pr == null) return SessionPullRequestFields(null, null, null, null, null)
        val ref = pr.url?.let { parseGitHubPullRequestUrl(it) }
        return SessionPullRequestFields(
            url = pr.url?.takeIf { it.isNotBlank() },
            title = pr.title?.takeIf { it.isNotBlank() },
            description = pr.description?.takeIf { it.isNotBlank() },
            id = ref?.number?.toString(),
            repo = ref?.let { "${it.owner}/${it.repo}" },
        )
    }

    /** Merges session PR output into entity; keeps existing values when the API omits outputs. */
    private fun mergeSessionPullRequest(
        entity: JulesSessionEntity,
        pr: JulesClient.JulesPullRequest?,
    ): JulesSessionEntity {
        val fields = pullRequestFields(pr)
        if (fields.url.isNullOrBlank()) return entity
        return entity.copy(
            prUrl = fields.url,
            prTitle = fields.title ?: entity.prTitle,
            prDescription = fields.description ?: entity.prDescription,
            prId = fields.id ?: entity.prId,
            prRepo = fields.repo ?: entity.prRepo,
            prState = entity.prState ?: "open",
        )
    }

    private fun parallelSyncLimit(): Int =
        settingsManager.settings.value.queuePolicyFor(backend()).parallelLimit.coerceAtLeast(1)

    private fun activeApiKey(): String? {
        val settings = settingsManager.settings.value
        return if (isClaudeBackend()) {
            settings.enabledAccountsFor(CodingAgentBackend.CLAUDE_CODE).firstOrNull()?.apiKey
                ?: settings.anthropicApiKey.takeIf { it.isNotBlank() }
        } else {
            settings.enabledAccountsFor(CodingAgentBackend.JULES).firstOrNull()?.apiKey
                ?: settings.julesApiKeys().firstOrNull()
        }
    }

    /** One Jules key for listSources — avoids N parallel /sources calls for N accounts. */
    private fun primarySourceApiKey(apiKeys: List<String>): String? =
        activeApiKey()?.takeIf { it.isNotBlank() && (apiKeys.isEmpty() || it in apiKeys) }
            ?: apiKeys.firstOrNull { it.isNotBlank() }

    suspend fun getAllActiveSessions(): List<JulesSessionEntity> = julesDao.getActiveSessions()

    fun getSessionsFlow(sourceName: String): Flow<List<JulesSessionEntity>> =
        julesDao.getSessionsFlowBySource(sourceName).map { filterSessionsForBackend(it) }

    fun getAllSessionsFlow(): Flow<List<JulesSessionEntity>> =
        julesDao.getAllSessionsFlow().map { filterSessionsForBackend(it) }

    suspend fun updateSessionPrStatus(sessionId: String, state: String?, mergeable: Boolean?, mergeableState: String? = null) {
        julesDao.updateSessionPrStatus(sessionId, state ?: "open", mergeable, mergeableState)
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
                    JulesClient.JulesGitHubRepo(
                        owner = it.owner,
                        repo = it.repo,
                        defaultBranch = it.defaultBranch?.let { branch ->
                            JulesClient.JulesGitHubBranch(displayName = branch)
                        },
                    )
                } else null
            )
        }
    }

    fun getSourcesFlow(): Flow<List<JulesClient.JulesSource>> = julesDao.getSourcesFlow().map { entities ->
        entities.map {
            JulesClient.JulesSource(
                name = it.name,
                id = it.id,
                githubRepo = if (it.owner != null && it.repo != null) {
                    JulesClient.JulesGitHubRepo(
                        owner = it.owner,
                        repo = it.repo,
                        defaultBranch = it.defaultBranch?.let { branch ->
                            JulesClient.JulesGitHubBranch(displayName = branch)
                        },
                    )
                } else null
            )
        }
    }

    /** True when the Room cache is empty or older than [ttlMs] (default 5 min). */
    suspend fun shouldRefreshSources(ttlMs: Long = SOURCES_CACHE_TTL_MS): Boolean {
        val rows = runCatching { julesDao.getSources() }.getOrDefault(emptyList())
        if (rows.isEmpty()) return true
        val cachedAt = rows.maxOf { it.lastUpdated }
        return System.currentTimeMillis() - cachedAt > ttlMs
    }

    suspend fun refreshSources(apiKeys: List<String>) {
        sourcesRefreshMutex.withLock {
            refreshSourcesUnlocked(apiKeys)
        }
    }

    private suspend fun refreshSourcesUnlocked(apiKeys: List<String>) {
        dbCacheDebugTracker.begin("refreshSources")
        try {
            val allSources = mutableMapOf<String, JulesClient.JulesSource>()
            val julesKey = primarySourceApiKey(apiKeys)
            if (julesKey != null) {
                try {
                    val resp = julesClient.listSources(julesKey)
                    resp.sources.forEach { src -> allSources[src.name] = src }
                } catch (e: Exception) {
                    android.util.Log.e("JulesRepository", "Failed to load sources", e)
                }
            } else if (isClaudeBackend()) {
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
            }

            if (allSources.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val entities = allSources.values.map {
                    JulesSourceEntity(
                        name = it.name,
                        id = it.id,
                        owner = it.githubRepo?.owner,
                        repo = it.githubRepo?.repo,
                        defaultBranch = it.githubRepo?.defaultBranch?.displayName?.takeIf { branch -> branch.isNotBlank() },
                        lastUpdated = now
                    )
                }
                val previous = runCatching { julesDao.getSources() }.getOrDefault(emptyList()).associateBy { it.name }
                // Atomic swap so the cached source Flow never blinks empty mid-refresh.
                julesDao.replaceSources(entities)
                trackProjectReplace(previous, entities)
            }
            dbCacheDebugTracker.finish()
        } catch (e: Exception) {
            dbCacheDebugTracker.finish(e.message)
            android.util.Log.e("JulesRepository", "Failed to refresh sources", e)
        }
    }

    /** Room-backed sources: emits cached rows first, optionally refreshes from the API in the background. */
    fun getSources(apiKeys: List<String>, refresh: Boolean = true): Flow<List<JulesClient.JulesSource>> = flow {
        emitAll(getSourcesFlow())
    }.onStart {
        if (refresh) {
            kotlinx.coroutines.GlobalScope.launch {
                refreshSources(apiKeys)
            }
        }
    }

    /** Room-backed sessions: emits cached rows first, optionally refreshes from the API in the background. */
    fun getSessions(
        apiKeys: List<String>,
        sourceName: String,
        refresh: Boolean = true,
        pageSize: Int = 10,
        refreshActivities: Boolean = true,
    ): Flow<List<JulesSessionEntity>> = flow {
        emitAll(getSessionsFlow(sourceName))
    }.onStart {
        if (refresh) {
            kotlinx.coroutines.GlobalScope.launch {
                refreshSessionsInternal(apiKeys, sourceName, pageSize, refreshActivities)
            }
        }
    }

    private suspend fun refreshActivitiesForSessions(sessionIds: Collection<String>) {
        val ids = sessionIds.distinct().filter { it.isNotBlank() && !it.startsWith("offline_") }
        if (ids.isEmpty()) return
        val semaphore = kotlinx.coroutines.sync.Semaphore(parallelSyncLimit())
        coroutineScope {
            ids.map { sessionId ->
                async {
                    semaphore.withPermit {
                        try {
                            refreshActivitiesInternal(sessionId)
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "JulesRepository",
                                "Failed to refresh activities for $sessionId",
                                e,
                            )
                        }
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun getSessionsBySource(sourceName: String): List<JulesSessionEntity> =
        filterSessionsForBackend(julesDao.getSessionsBySource(sourceName))

    suspend fun refreshSessionsInternal(
        apiKeys: List<String>,
        sourceName: String,
        pageSize: Int = 10,
        refreshActivities: Boolean = true,
    ) {
        dbCacheDebugTracker.begin("refreshSessions:$sourceName")
        val refreshedSessionIds = mutableSetOf<String>()
        try {
        if (isClaudeBackend()) {
            val apiKey = activeApiKey()
            if (apiKey != null) {
                try {
                    val resp = claudeCodeClient.listSessions(apiKey)
                    val remoteSessions = resp.data
                    val currentEntities = mutableListOf<JulesSessionEntity>()
                    for (session in remoteSessions) {
                        val existing = try { julesDao.getSession(session.id) } catch (e: Exception) { null }
                        if (existing != null && existing.sourceName != sourceName) continue
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
                                sourceName = existing?.sourceName ?: sourceName,
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
                        trackSessionUpserts(currentEntities)
                        refreshedSessionIds.addAll(currentEntities.map { it.id })
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JulesRepository", "Failed to fetch Claude sessions", e)
                }
            }
        } else {
            val key = primarySourceApiKey(apiKeys)
            if (key != null) {
                try {
                    val resp = julesClient.listSessions(key, pageSize = pageSize)
                    val sessions = resp.sessions.orEmpty().filter {
                        it.sourceContext?.source == sourceName || it.sourceContext?.source == resolveJulesSource(sourceName)
                    }
                    val currentEntities = mutableListOf<JulesSessionEntity>()
                    for (session in sessions) {
                        val sessionId = session.id.takeIf { it.isNotBlank() } ?: session.name
                        if (sessionId.isBlank()) continue
                        val existing = try { julesDao.getSession(sessionId) } catch (e: Exception) { null }
                        val apiUpdateTime = try {
                            if (!session.updateTime.isNullOrBlank()) {
                                OffsetDateTime.parse(session.updateTime).toInstant().toEpochMilli()
                            } else 0L
                        } catch (e: Exception) { 0L }
                        val newLastUpdated = maxOf(existing?.lastUpdated ?: 0L, apiUpdateTime).let {
                            if (it == 0L) System.currentTimeMillis() else it
                        }
                        val baseEntity = JulesSessionEntity(
                            id = sessionId,
                            title = session.title,
                            prompt = session.prompt,
                            sourceName = sourceName,
                            prUrl = existing?.prUrl,
                            prTitle = existing?.prTitle,
                            prDescription = existing?.prDescription,
                            prId = existing?.prId,
                            prRepo = existing?.prRepo,
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
                        )
                        val merged = mergeSessionPullRequest(baseEntity, session.primaryPullRequest())
                        currentEntities.add(merged)
                    }
                    val localSessionsForKey = julesDao.getSessionsBySourceAndKey(sourceName, key)
                    val currentIds = currentEntities.map { it.id }.toSet()
                    val toDelete = localSessionsForKey.filter { !currentIds.contains(it.id) && !it.isPendingOffline }
                    toDelete.forEach { julesDao.deleteSession(it.id) }
                    trackSessionDeletes(toDelete.size)
                    if (currentEntities.isNotEmpty()) {
                        julesDao.insertSessions(currentEntities)
                        trackSessionUpserts(currentEntities)
                        refreshedSessionIds.addAll(currentEntities.map { it.id })
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JulesRepository", "Failed to fetch sessions for key ${key.take(8)}...", e)
                }
            }
        }
        if (refreshActivities) {
            refreshActivitiesForSessions(refreshedSessionIds)
        }
        dbCacheDebugTracker.finish()
        } catch (e: Exception) {
            dbCacheDebugTracker.finish(e.message)
            android.util.Log.e("JulesRepository", "Failed to refresh sessions for $sourceName", e)
        }
    }

    suspend fun updatePrStatus(githubToken: String, session: JulesSessionEntity) {
        val url = session.prUrl ?: return
        val prRef = parseGitHubPullRequestUrl(url) ?: return
        try {
            val detail = githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            val state = detail.toPrState()
            val mergeableState = detail.mergeableState
            try {
                // If status changed, update lastUpdated too
                val existing = julesDao.getSession(session.id)
                val statusChanged = existing != null && (
                    existing.prState != state ||
                        existing.prMergeable != detail.mergeable ||
                        existing.prMergeableState != mergeableState
                    )

                julesDao.updateSessionPrStatus(session.id, state, detail.mergeable, mergeableState)

                val branch = detail.head?.ref
                val repo = detail.repository?.fullName ?: detail.head?.repo?.fullName
                julesDao.updateSessionGitHubDetails(session.id, branch, repo)

                val prTitle = detail.title.takeIf { it.isNotBlank() }
                val prDescription = detail.body?.takeIf { it.isNotBlank() }
                if (prTitle != null || prDescription != null) {
                    val current = julesDao.getSession(session.id) ?: session
                    julesDao.insertSessions(
                        listOf(
                            current.copy(
                                prTitle = prTitle ?: current.prTitle,
                                prDescription = prDescription ?: current.prDescription,
                            ),
                        ),
                    )
                }

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

    suspend fun deleteSessionPermanently(sessionId: String) {
        try {
            val existing = julesDao.getSession(sessionId)
            val key = existing?.apiKey ?: activeApiKey()
            if (key != null && !sessionId.startsWith("offline_")) {
                try {
                    if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                        claudeCodeClient.deleteSession(key, sessionId)
                    } else {
                        julesClient.deleteSession(key, sessionId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JulesRepository", "Remote session permanent deletion failed for $sessionId", e)
                }
            }
            julesDao.deleteSession(sessionId)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to permanently delete session $sessionId", e)
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

    suspend fun createSession(
        apiKeys: List<String>,
        prompt: String,
        source: String,
        title: String?,
        featureId: String? = null,
        requirePlanApproval: Boolean? = null,
    ): String {
        val settings = settingsManager.settings.value
        val account = settings.enabledAccountsFor(backend()).firstOrNull { it.apiKey in apiKeys }
            ?: apiKeys.firstOrNull()?.let { key ->
                AgentAccount(
                    id = "legacy",
                    label = "Legacy",
                    backend = backend(),
                    apiKey = key,
                )
            }
            ?: throw Exception("No API key available")
        return createSession(account, prompt, source, title, featureId, requirePlanApproval)
    }

    suspend fun createSession(
        account: AgentAccount,
        prompt: String,
        source: String,
        title: String?,
        featureId: String? = null,
        requirePlanApproval: Boolean? = null,
    ): String {
        val finalPrompt = prompt.ifBlank { title ?: "New session" }
        val finalTitle = title?.takeIf { it.isNotBlank() }

        val isOnline = networkService.status.value.isConnected
        if (isOnline) {
            if (account.backend == CodingAgentBackend.CLAUDE_CODE || isClaudeBackend()) {
                val apiKey = account.apiKey.takeIf { it.isNotBlank() }
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
                    prompt = finalPrompt,
                    owner = owner,
                    repo = repo,
                    githubToken = githubToken,
                    title = finalTitle ?: prompt.take(80)
                )
                val entity = JulesSessionEntity(
                    id = session.id,
                    title = session.title.ifBlank { title ?: "" },
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

            val apiKey = account.apiKey.takeIf { it.isNotBlank() } ?: throw Exception("No API key available")
            val session = createJulesApiSession(
                apiKey = apiKey,
                prompt = finalPrompt,
                source = source,
                title = finalTitle,
                requirePlanApproval = requirePlanApproval,
            )
            val entity = mergeSessionPullRequest(
                JulesSessionEntity(
                    id = session.id,
                    title = session.title,
                    prompt = session.prompt,
                    sourceName = source,
                    prUrl = null,
                    prTitle = null,
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
                ),
                session.primaryPullRequest(),
            )
            julesDao.insertSessions(listOf(entity))
            refreshActivitiesInternal(session.id)
            return session.id
        } else {
            val tempId = "offline_${java.util.UUID.randomUUID()}"
            val entity = JulesSessionEntity(
                id = tempId,
                title = finalTitle ?: prompt.take(80),
                prompt = finalPrompt,
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

    /**
     * Maps a feature sourceName (which may be a raw "owner/repo") to a valid Jules source
     * resource name using the cached sources. Falls back to the input if no match is found.
     */
    private suspend fun resolveJulesSource(source: String): String {
        if (source.startsWith("sources/")) return source
        val cached = runCatching { getSourcesCached() }.getOrDefault(emptyList())
        cached.firstOrNull { it.name == source }?.let { return it.name }
        cached.firstOrNull { s ->
            val gh = s.githubRepo
            gh != null && ("${gh.owner}/${gh.repo}" == source || gh.repo == source)
        }?.let { return it.name }
        return source
    }

    /** Resolves the branch to pass in sourceContext.githubRepoContext.startingBranch (API-required). */
    private suspend fun resolveStartingBranch(resolvedSource: String, rawSource: String): String {
        val cached = runCatching { getSourcesCached() }.getOrDefault(emptyList())
        val match = cached.firstOrNull { it.name == resolvedSource || it.name == rawSource }
            ?: cached.firstOrNull { s ->
                val gh = s.githubRepo ?: return@firstOrNull false
                "${gh.owner}/${gh.repo}" == rawSource || gh.repo == rawSource
            }
        match?.githubRepo?.defaultBranch?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return "main"
    }

    private suspend fun createJulesApiSession(
        apiKey: String,
        prompt: String,
        source: String,
        title: String?,
        requirePlanApproval: Boolean? = null,
    ): JulesClient.JulesSession {
        val resolvedSource = resolveJulesSource(source)
        return julesClient.createSession(
            apiKey = apiKey,
            prompt = prompt,
            source = resolvedSource,
            title = title,
            startingBranch = resolveStartingBranch(resolvedSource, source),
            automationMode = JulesClient.AutomationMode.AUTO_CREATE_PR,
            requirePlanApproval = requirePlanApproval,
        )
    }

    /**
     * Records a local FAILED session so a start error is surfaced on the conversation
     * (with the error as a system activity) rather than on the feature.
     */
    suspend fun createFailedSession(source: String, title: String?, prompt: String, featureId: String?, error: String?): String {
        val id = "error_${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val entity = JulesSessionEntity(
            id = id,
            title = title?.takeIf { it.isNotBlank() } ?: prompt.take(80).ifBlank { "Conversation" },
            prompt = prompt,
            sourceName = source,
            prUrl = null,
            prTitle = null,
            prState = null,
            prMergeable = null,
            sessionState = "FAILED",
            isArchived = false,
            lastUpdated = now,
            featureId = featureId,
        )
        julesDao.insertSessions(listOf(entity))

        val text = if (error != null) {
            Json.encodeToString(
                fr.geoking.julius.api.jules.JulesErrorDetails.serializer(),
                fr.geoking.julius.api.jules.JulesErrorDetails(error = "Échec du démarrage de la session : $error")
            )
        } else {
            "Échec du démarrage de la session : erreur inconnue"
        }

        val act = JulesActivityEntity(
            id = "err_${java.util.UUID.randomUUID()}",
            sessionId = id,
            originator = "system",
            text = text,
            timestamp = OffsetDateTime.now().toString(),
            sortTimestamp = now,
            type = "error",
        )
        julesDao.insertActivities(listOf(act))
        return id
    }

    private suspend fun addErrorActivity(sessionId: String, e: Throwable) {
        val now = System.currentTimeMillis()
        val details = if (e is NetworkException) {
            fr.geoking.julius.api.jules.JulesErrorDetails(
                error = e.message ?: "Unknown network error",
                url = e.url,
                httpCode = e.httpCode,
                requestBody = e.requestBody
            )
        } else {
            fr.geoking.julius.api.jules.JulesErrorDetails(error = e.message ?: e.toString())
        }

        val act = JulesActivityEntity(
            id = "err_${java.util.UUID.randomUUID()}",
            sessionId = sessionId,
            originator = "system",
            text = Json.encodeToString(fr.geoking.julius.api.jules.JulesErrorDetails.serializer(), details),
            timestamp = OffsetDateTime.now().toString(),
            sortTimestamp = now,
            type = "error",
        )
        julesDao.insertActivities(listOf(act))
        julesDao.updateSessionLastUpdated(sessionId, now)
    }

    suspend fun sendMessage(sessionId: String, prompt: String, apiKey: String? = null) {
        val tempId = "msg_${java.util.UUID.randomUUID()}"
        val nowStr = OffsetDateTime.now().toString()
        val nowMs = System.currentTimeMillis()
        val isOnline = networkService.status.value.isConnected && !sessionId.startsWith("offline_")

        val localActivity = JulesActivityEntity(
            id = tempId,
            sessionId = sessionId,
            originator = "user",
            text = prompt,
            timestamp = nowStr,
            sortTimestamp = nowMs,
            isPendingOffline = !isOnline
        )
        julesDao.insertActivities(listOf(localActivity))
        julesDao.updateSessionLastUpdated(sessionId, nowMs)

        if (isOnline) {
            val existing = julesDao.getSession(sessionId)
            val key = apiKey ?: existing?.apiKey ?: activeApiKey()
                ?: throw Exception("No API key available")

            try {
                if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                    claudeCodeClient.sendMessage(key, sessionId, prompt)
                } else {
                    julesClient.sendMessage(key, sessionId, prompt)
                }
            } catch (e: Exception) {
                addErrorActivity(sessionId, e)
                throw e
            }
        } else {
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
        val settings = settingsManager.settings.value
        if (!isCodingAgentConfigured(settings.julesApiKeys())) return@coroutineScope
        val apiKey = activeApiKey() ?: return@coroutineScope
        val syncSemaphore = kotlinx.coroutines.sync.Semaphore(parallelSyncLimit())

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
                            val created = createJulesApiSession(
                                apiKey = apiKey,
                                prompt = session.prompt,
                                source = session.sourceName,
                                title = session.title,
                            )
                            val updatedEntity = mergeSessionPullRequest(
                                JulesSessionEntity(
                                    id = created.id,
                                    title = created.title,
                                    prompt = created.prompt,
                                    sourceName = session.sourceName,
                                    prUrl = null,
                                    prTitle = null,
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
                                ),
                                created.primaryPullRequest(),
                            )
                            julesDao.insertSessions(listOf(updatedEntity))
                            refreshActivitiesInternal(created.id)
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
     * Polls session state from Jules/Claude and refreshes cached activities.
     * PR metadata comes from session outputs only; GitHub is not queried here.
     */
    suspend fun pollSessionStatus(sessionId: String) {
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
                    } else if (statusChanged) {
                        julesDao.updateSessionLastUpdated(sessionId, System.currentTimeMillis())
                    }
                }
            } else {
                val session = julesClient.getSession(apiKey, sessionId)
                existing = julesDao.getSession(sessionId)

                if (existing != null) {
                    val statusChanged = existing.sessionState != session.state
                    val updated = mergeSessionPullRequest(
                        existing.copy(
                            sessionState = session.state,
                            url = session.url ?: existing.url,
                            createTime = session.createTime.ifBlank { existing.createTime },
                            updateTime = session.updateTime.ifBlank { existing.updateTime },
                            lastUpdated = System.currentTimeMillis(),
                        ),
                        session.primaryPullRequest(),
                    )
                    val prChanged = updated.prUrl != existing.prUrl ||
                        updated.prTitle != existing.prTitle ||
                        updated.prDescription != existing.prDescription ||
                        updated.prId != existing.prId ||
                        updated.prRepo != existing.prRepo

                    if (statusChanged || prChanged) {
                        julesDao.insertSessions(listOf(updated))
                    }
                }
            }

            val githubToken = settingsManager.settings.value.githubApiKey
            if (githubToken.isNotBlank()) {
                julesDao.getSession(sessionId)?.let { updatePrStatus(githubToken, it) }
            }
            refreshActivitiesInternal(sessionId)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "pollSessionStatus failed for $sessionId", e)
        }
    }

    fun getActivities(sessionId: String): Flow<List<JulesChatItem>> = flow {
        emitAll(julesDao.getActivitiesFlowBySession(sessionId).map { entities ->
            mapActivityEntitiesToChatItems(sessionId, entities)
        })
    }.onStart {
        // Fire-and-forget refresh in background scope to avoid blocking onStart
        kotlinx.coroutines.GlobalScope.launch {
            refreshActivitiesInternal(sessionId)
        }
    }

    private fun mapActivityEntitiesToChatItems(sessionId: String, entities: List<JulesActivityEntity>): List<JulesChatItem> {
        val json = Json { ignoreUnknownKeys = true }
        if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
            val events = entities.mapNotNull { it.activityJson }.map {
                json.decodeFromString(ClaudeCodeClient.ClaudeEvent.serializer(), it)
            }
            if (events.isNotEmpty()) return claudeCodeClient.eventsToChatItems(events)
        } else {
            val activities = entities.mapNotNull { it.activityJson }.map {
                json.decodeFromString(JulesClient.JulesActivity.serializer(), it)
            }
            if (activities.isNotEmpty()) return julesClient.activitiesToChatItems(activities)
        }

        val items = mutableListOf<JulesChatItem>()
        var currentAgentGroup: JulesChatItem.AgentMessage? = null
        fun flush() { currentAgentGroup?.let { items.add(it) }; currentAgentGroup = null }
        for (a in entities.sortedBy { it.timestamp }) {
            if (a.originator == "user") {
                flush()
                items.add(JulesChatItem.UserMessage(a.id, a.timestamp, a.text))
            } else {
                val subItem = JulesChatItem.AgentSubItem(a.id, a.timestamp, a.text, a.type)
                if (a.type == "progress") {
                    if (currentAgentGroup?.title == "Progress") {
                        currentAgentGroup = currentAgentGroup!!.copy(subItems = currentAgentGroup!!.subItems + subItem)
                    } else {
                        flush()
                        currentAgentGroup = JulesChatItem.AgentMessage(a.id, a.timestamp, "Progress", listOf(subItem))
                    }
                } else {
                    flush()
                    items.add(JulesChatItem.AgentMessage(a.id, a.timestamp, a.text, listOf(subItem)))
                }
            }
        }
        flush()
        return items
    }

    suspend fun refreshActivitiesInternal(sessionId: String) {
        dbCacheDebugTracker.begin("refreshActivities:$sessionId")
        try {
            val existingSession = try { julesDao.getSession(sessionId) } catch (e: Exception) { null }
            val apiKey = existingSession?.apiKey ?: activeApiKey()
            if (apiKey == null) {
                dbCacheDebugTracker.finish()
                return
            }

            if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) {
                val resp = claudeCodeClient.listEvents(apiKey, sessionId)
                val prUrl = claudeCodeClient.findPullRequestUrl(resp.data)
                if (prUrl != null) {
                    val existing = julesDao.getSession(sessionId)
                    if (existing != null && existing.prUrl != prUrl) {
                        val updated = existing.copy(
                            prUrl = prUrl,
                            prState = existing.prState ?: "open",
                            lastUpdated = System.currentTimeMillis(),
                        )
                        julesDao.insertSessions(listOf(updated))
                    }
                }

                val entities = resp.data.mapNotNull { event ->
                    val text = claudeCodeClient.extractText(event)
                    if (text.isBlank() && event.type !in listOf("session.status_terminated")) return@mapNotNull null
                    val originator = if (event.type == "user.message") "user" else "agent"
                    val type = when {
                        event.type in setOf("agent.tool_use", "agent.mcp_tool_use", "session.status_running") -> "progress"
                        event.type == "session.status_terminated" -> "completion"
                        event.type == "session.error" -> "failure"
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
                val previousCount = runCatching { julesDao.getActivitiesBySession(sessionId).size }.getOrDefault(0)
                julesDao.clearActivitiesBySession(sessionId)
                julesDao.insertActivities(entities)
                trackActivityReplace(previousCount, entities.size)
                return
            }

            val resp = julesClient.listActivities(apiKey, sessionId, pageSize = 50)
            for (activity in resp.activities) {
                // Per the Jules API, git patches surface via Artifact.changeSet.gitPatch.
                // Pull requests are not part of activities; they come from Session.outputs
                // (handled in refreshSessionsInternal / pollSessionStatus).
                val gitPatch = activity.artifacts?.mapNotNull { it.changeSet?.gitPatch }?.firstOrNull()

                if (gitPatch != null) {
                    val existing = julesDao.getSession(sessionId)
                    if (existing != null) {
                        val updated = existing.copy(
                            prPatch = gitPatch.unidiffPatch ?: existing.prPatch,
                            prBaseCommitId = gitPatch.baseCommitId ?: existing.prBaseCommitId,
                            lastUpdated = System.currentTimeMillis()
                        )
                        if (updated != existing) {
                            julesDao.insertSessions(listOf(updated))
                        }
                    }
                }
            }

            val entities = mutableListOf<JulesActivityEntity>()
            for (activity in resp.activities) {
                val a = activity
                val text = julesClient.extractText(a)
                val type = when {
                    a.planGenerated != null -> "plan"
                    a.progressUpdated != null -> "progress"
                    a.sessionCompleted != null -> "completion"
                    a.userMessaged != null -> "user_message"
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
            val previousCount = runCatching { julesDao.getActivitiesBySession(sessionId).size }.getOrDefault(0)
            julesDao.clearActivitiesBySession(sessionId)
            julesDao.insertActivities(entities)
            trackActivityReplace(previousCount, entities.size)
        } catch (e: Exception) {
            dbCacheDebugTracker.finish(e.message)
            android.util.Log.e("JulesRepository", "refreshActivitiesInternal failed for $sessionId", e)
            return
        }
        dbCacheDebugTracker.finish()
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

    /**
     * Title and body to seed a new agent conversation from an existing PR (conflict replay).
     * Prefers cached session fields, then GitHub, then the original session prompt.
     */
    suspend fun resolvePrConversationPrompt(
        session: JulesSessionEntity,
        githubToken: String,
    ): Pair<String, String> {
        var title = session.prTitle?.takeIf { it.isNotBlank() }
        var prompt = session.prDescription?.takeIf { it.isNotBlank() }

        val prUrl = session.prUrl
        if ((title == null || prompt == null) && !prUrl.isNullOrBlank() && githubToken.isNotBlank()) {
            getPrDetails(githubToken, prUrl).getOrNull()?.let { detail ->
                if (title == null) title = detail.title.takeIf { it.isNotBlank() }
                if (prompt == null) prompt = detail.body?.takeIf { it.isNotBlank() }
            }
        }

        val resolvedPrompt = prompt?.takeIf { it.isNotBlank() }
            ?: session.prompt.takeIf { it.isNotBlank() }
            ?: throw Exception("PR description not available")
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: resolvedPrompt.take(80).ifBlank { "Conversation" }
        return resolvedTitle to resolvedPrompt
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

    val cacheDebugTracker: DbCacheDebugTracker get() = dbCacheDebugTracker

    suspend fun getSession(sessionId: String): JulesSessionEntity? {
        return julesDao.getSession(sessionId)
    }

    suspend fun getSessionsByFeature(featureId: String): List<JulesSessionEntity> {
        return julesDao.getSessionsByFeature(featureId)
    }

    suspend fun getActivitiesBySession(sessionId: String): List<JulesActivityEntity> {
        return julesDao.getActivitiesBySession(sessionId)
    }

    /** Daily session quota from local counters and queue policy. */
    suspend fun getUsageQuota(apiKeys: List<String>): JulesQuota? {
        if (apiKeys.isEmpty()) return null
        val settings = settingsManager.settings.value
        val policy = settings.queuePolicyFor(backend())
        val accounts = settings.enabledAccountsFor(backend()).filter { it.apiKey in apiKeys }
        if (accounts.isEmpty()) return null

        val dayEpoch = currentDayEpochUtc()
        val dailyUsage = accountDailyUsageDao.getAllForDay(dayEpoch)

        val used = accounts.sumOf { account ->
            dailyUsage.find { it.accountId == account.id }?.startedCount ?: 0
        }
        return JulesQuota(used = used, limit = policy.dailyLimitPerAccount * accounts.size.coerceAtLeast(1))
    }

    suspend fun approvePlan(apiKey: String, sessionId: String) {
        if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) return
        try {
            julesClient.approvePlan(apiKey, sessionId)
        } catch (e: Exception) {
            addErrorActivity(sessionId, e)
            throw e
        }
    }

    suspend fun pauseSession(sessionId: String) {
        if (isClaudeBackend() || sessionId.startsWith(CodingAgentBackend.CLAUDE_SESSION_PREFIX)) return
        try {
            val session = julesDao.getSession(sessionId) ?: return
            val apiKey = session.apiKey ?: activeApiKey() ?: return
            julesClient.pauseSession(apiKey, sessionId)
            pollSessionStatus(sessionId)
        } catch (e: Exception) {
            addErrorActivity(sessionId, e)
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
            pollSessionStatus(sessionId)
        } catch (e: Exception) {
            addErrorActivity(sessionId, e)
            android.util.Log.e("JulesRepository", "Failed to resume session $sessionId", e)
            throw e
        }
    }
}

/** Used/limit for optional daily session display in the harness UI. */
data class JulesQuota(val used: Int, val limit: Int)

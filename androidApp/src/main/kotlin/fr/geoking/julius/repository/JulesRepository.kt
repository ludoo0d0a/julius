package fr.geoking.julius.repository

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
    private val githubClient: GitHubClient,
    private val julesDao: JulesDao,
    private val networkService: NetworkService,
    private val settingsManager: SettingsManager
) {
    private val syncSemaphore = kotlinx.coroutines.sync.Semaphore(3)

    fun getSources(apiKeys: List<String>): Flow<List<JulesClient.JulesSource>> = flow {
        // 1. Emit from cache
        val cached = julesDao.getSources()
        if (cached.isNotEmpty()) {
            emit(cached.map {
                JulesClient.JulesSource(
                    name = it.name,
                    id = it.id,
                    githubRepo = if (it.owner != null && it.repo != null) {
                        JulesClient.JulesGitHubRepo(it.owner, it.repo)
                    } else null
                )
            })
        }

        // 2. Refresh if needed (e.g. once a day)
        val lastUpdated = cached.firstOrNull()?.lastUpdated ?: 0L
        val oneDayMs = 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastUpdated > oneDayMs || cached.isEmpty()) {
            try {
                val allSources = mutableMapOf<String, JulesClient.JulesSource>()
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
            val cached = julesDao.getSessionsBySource(sourceName)
            emit(cached)
        } catch (e: Exception) {
            emit(emptyList())
        }

        // 2. Fetch from network for all API keys
        var anySuccess = false

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

                            val newLastUpdated = existing?.lastUpdated ?: System.currentTimeMillis()

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
                                apiKey = key
                            ))
                        }

                        // Clean up deleted sessions for THIS key only
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

        if (anySuccess) {
            val entities = try { julesDao.getSessionsBySource(sourceName) } catch (e: Exception) { emptyList() }
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
                if (statusChanged) {
                    julesDao.updateSessionLastUpdated(session.id, System.currentTimeMillis())
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
            val key = existing?.apiKey ?: settingsManager.settings.value.julesKeys.firstOrNull()
            if (key != null && !sessionId.startsWith("offline_")) {
                try {
                    julesClient.deleteSession(key, sessionId)
                } catch (e: Exception) {
                    // Even if remote deletion fails (e.g. 404), we want to archive locally
                    android.util.Log.e("JulesRepository", "Remote session deletion failed for $sessionId", e)
                }
            }
            julesDao.archiveSession(sessionId)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to archive session $sessionId", e)
        }
    }

    suspend fun archiveCompletedSessions(sourceName: String) {
        try {
            val completed = julesDao.getCompletedSessions(sourceName)
            if (completed.isEmpty()) return

            coroutineScope {
                completed.map { session ->
                    async {
                        val key = session.apiKey ?: settingsManager.settings.value.julesKeys.firstOrNull()
                        if (key != null && !session.id.startsWith("offline_")) {
                            try {
                                julesClient.deleteSession(key, session.id)
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

    suspend fun createSession(apiKeys: List<String>, prompt: String, source: String, title: String): String {
        val isOnline = networkService.status.value.isConnected
        if (isOnline) {
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
                apiKey = apiKey
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
                queuedAt = System.currentTimeMillis()
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
            val key = apiKey ?: existing?.apiKey ?: settingsManager.settings.value.julesKeys.firstOrNull()
                ?: throw Exception("No API key available")

            julesClient.sendMessage(key, sessionId, prompt)
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
        val apiKeys = settingsManager.settings.value.julesKeys
        if (apiKeys.isEmpty()) return@coroutineScope
        val apiKey = apiKeys.first()

        // 1. Sync pending sessions
        val pendingSessions = julesDao.getPendingOfflineSessions()
        for (session in pendingSessions) {
            async {
                syncSemaphore.withPermit {
                    try {
                        val newSession = julesClient.createSession(
                            apiKey = apiKey,
                            prompt = session.prompt,
                            source = session.sourceName,
                            title = session.title
                        )
                        // Update local session ID and all associated activities
                        val updatedEntity = JulesSessionEntity(
                            id = newSession.id,
                            title = newSession.title,
                            prompt = newSession.prompt,
                            sourceName = session.sourceName,
                            prUrl = newSession.outputs?.firstOrNull()?.pullRequest?.url,
                            prTitle = newSession.outputs?.firstOrNull()?.pullRequest?.title,
                            prId = newSession.outputs?.firstOrNull()?.pullRequest?.id,
                            prState = null,
                            prMergeable = null,
                            sessionState = newSession.state,
                            url = newSession.url,
                            createTime = newSession.createTime,
                            updateTime = newSession.updateTime,
                            isArchived = false,
                            lastUpdated = System.currentTimeMillis(),
                            isPendingOffline = false,
                            queuedAt = null,
                            apiKey = apiKey
                        )
                        julesDao.insertSessions(listOf(updatedEntity))
                        julesDao.updateActivitiesSessionId(session.id, newSession.id)
                        julesDao.deleteSession(session.id)

                        // After promotion, sync any activities that were attached to this session
                        syncPendingActivities(apiKey, newSession.id)
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
                    val key = existing?.apiKey ?: apiKeys.firstOrNull()
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
                julesClient.sendMessage(apiKey, sessionId, activity.text)
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
            val apiKey = existing?.apiKey ?: settingsManager.settings.value.julesKeys.firstOrNull()
                ?: return

            // 1. Refresh session from Jules to see if a PR was just created or if sessionState changed
            val session = julesClient.getSession(apiKey, sessionId)
            val pr = session.outputs?.firstOrNull()?.pullRequest
            existing = julesDao.getSession(sessionId)

            if (existing != null) {
                val statusChanged = existing.sessionState != session.state
                if (statusChanged) {
                    julesDao.updateSessionState(sessionId, session.state)
                }

                if (pr?.url != null && existing.prUrl != pr.url) {
                    // New PR URL found
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
                        // Refresh existing PR status
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
        val apiKey = existingSession?.apiKey ?: settingsManager.settings.value.julesKeys.firstOrNull()

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
            val resp = julesClient.listActivities(apiKey, sessionId, pageSize = 50)
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

    suspend fun mergePr(githubToken: String, prUrl: String): Result<Unit> {
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            githubClient.mergePullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
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
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            val detail = githubClient.getPullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getNetworkService() = networkService

    suspend fun getSession(sessionId: String): JulesSessionEntity? {
        return julesDao.getSession(sessionId)
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
        julesClient.approvePlan(apiKey, sessionId)
    }
}

/** Used/limit for optional daily session display in [JulesScreen]. */
data class JulesQuota(val used: Int, val limit: Int)

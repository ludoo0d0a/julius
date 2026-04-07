package fr.geoking.julius.repository

import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesActivityEntity
import fr.geoking.julius.persistence.JulesDao
import fr.geoking.julius.persistence.JulesSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.time.OffsetDateTime
import java.time.Instant

class JulesRepository(
    private val julesClient: JulesClient,
    private val githubClient: GitHubClient,
    private val julesDao: JulesDao
) {
    fun getSessions(apiKey: String, sourceName: String, githubToken: String): Flow<List<JulesSessionEntity>> = flow {
        // 1. Emit from cache
        try {
            val cached = julesDao.getSessionsBySource(sourceName)
            emit(cached)
        } catch (e: Exception) {
            emit(emptyList())
        }

        // 2. Fetch from network
        try {
            val resp = julesClient.listSessions(apiKey, pageSize = 50)
            val sessions = resp.sessions.orEmpty().filter { it.sourceContext?.source == sourceName }

            val entities = mutableListOf<JulesSessionEntity>()
            for (session in sessions) {
                val sessionId = session.id.takeIf { it.isNotBlank() } ?: session.name
                if (sessionId.isBlank()) continue

                val pr = session.outputs?.firstOrNull()?.pullRequest
                val existing = try { julesDao.getSession(sessionId) } catch (e: Exception) { null }

                // Only update lastUpdated if PR URL changed or it's a new session
                val newLastUpdated = if (existing == null || (pr?.url != null && existing.prUrl != pr.url)) {
                    System.currentTimeMillis()
                } else {
                    existing.lastUpdated
                }

                entities.add(JulesSessionEntity(
                    id = sessionId,
                    title = session.title,
                    prompt = session.prompt,
                    sourceName = sourceName,
                    prUrl = pr?.url,
                    prTitle = pr?.title,
                    prState = existing?.prState,
                    prMergeable = existing?.prMergeable,
                    sessionState = session.state,
                    isArchived = existing?.isArchived ?: false,
                    lastUpdated = newLastUpdated
                ))
            }

            try {
                julesDao.insertSessions(entities)
            } catch (e: Exception) {
                // Continue even if DB insert fails
            }

            // Always emit what we fetched from network
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
        } catch (e: Exception) {
            // Ignore network errors for caching strategy
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
                val statusChanged = existing?.prState != state || existing?.prMergeable != detail.mergeable
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
            julesDao.archiveSession(sessionId)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to archive session", e)
        }
    }

    suspend fun updateSessionLastUpdated(sessionId: String, lastUpdated: Long) {
        try {
            julesDao.updateSessionLastUpdated(sessionId, lastUpdated)
        } catch (e: Exception) {
            android.util.Log.e("JulesRepository", "Failed to update session lastUpdated", e)
        }
    }

    /**
     * Polls the status of a specific session and its associated PR.
     * Refreshes PR details from GitHub and session details (for new PR URLs) from Jules.
     */
    suspend fun pollSessionStatus(apiKey: String, sessionId: String, githubToken: String) {
        try {
            // 1. Refresh session from Jules to see if a PR was just created or if sessionState changed
            val session = julesClient.getSession(apiKey, sessionId)
            val pr = session.outputs?.firstOrNull()?.pullRequest
            val existing = julesDao.getSession(sessionId)

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
                        sessionState = session.state,
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

    fun getActivities(apiKey: String, sessionId: String): Flow<List<JulesChatItem>> = flow {
        // 1. Emit from cache
        try {
            val cached = julesDao.getActivitiesBySession(sessionId)
            if (cached.isNotEmpty()) {
                emit(cached.map {
                    if (it.originator == "user") JulesChatItem.UserMessage(it.id, it.timestamp, it.text)
                    else JulesChatItem.AgentMessage(it.id, it.timestamp, it.text)
                })
            }
        } catch (e: Exception) {
            // Ignore cache error
        }

        // 2. Fetch from network
        try {
            val resp = julesClient.listActivities(apiKey, sessionId, pageSize = 50)
            val items = julesClient.activitiesToChatItems(resp.activities)

            val entities = items.map { item ->
                val id: String
                val ts: String
                val text: String
                val originator: String

                when (item) {
                    is JulesChatItem.UserMessage -> {
                        id = item.id
                        ts = item.createTime
                        text = item.text
                        originator = "user"
                    }
                    is JulesChatItem.AgentMessage -> {
                        id = item.id
                        ts = item.createTime
                        text = item.text
                        originator = "agent"
                    }
                }

                val sortTs = try { OffsetDateTime.parse(ts).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
                val finalId = id.takeIf { it.isNotBlank() } ?: "act_${sessionId}_${sortTs}_${text.hashCode()}"

                JulesActivityEntity(
                    id = finalId,
                    sessionId = sessionId,
                    originator = originator,
                    text = text,
                    timestamp = ts,
                    sortTimestamp = sortTs
                )
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

    suspend fun mergePr(githubToken: String, prUrl: String): Result<Unit> {
        val prRef = parseGitHubPullRequestUrl(prUrl) ?: return Result.failure(Exception("Invalid PR URL"))
        return try {
            githubClient.mergePullRequest(githubToken, prRef.owner, prRef.repo, prRef.number)
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

    /** Daily session quota for the Jules UI. Null when the API does not expose usage. */
    suspend fun getUsageQuota(apiKey: String): JulesQuota? {
        if (apiKey.isBlank()) return null
        // Jules public API has no documented quota endpoint; UI hides the quota row when null.
        return null
    }
}

/** Used/limit for optional daily session display in [JulesScreen]. */
data class JulesQuota(val used: Int, val limit: Int)

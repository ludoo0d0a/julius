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

                entities.add(JulesSessionEntity(
                    id = sessionId,
                    title = session.title,
                    prompt = session.prompt,
                    sourceName = sourceName,
                    prUrl = pr?.url,
                    prTitle = pr?.title,
                    prState = existing?.prState,
                    isArchived = existing?.isArchived ?: false,
                    lastUpdated = System.currentTimeMillis()
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
            julesDao.updateSessionPrState(session.id, state)
        } catch (e: Exception) {
            // Ignore
        }
    }

    suspend fun archiveSession(sessionId: String) {
        julesDao.archiveSession(sessionId)
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

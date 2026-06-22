package fr.geoking.julius.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JulesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<JulesSessionEntity>)

    @Query("SELECT * FROM jules_sessions WHERE sourceName = :sourceName AND isArchived = 0 ORDER BY lastUpdated DESC")
    suspend fun getSessionsBySource(sourceName: String): List<JulesSessionEntity>

    @Query("SELECT * FROM jules_sessions WHERE sourceName = :sourceName AND isArchived = 0 ORDER BY lastUpdated DESC")
    fun getSessionsFlowBySource(sourceName: String): Flow<List<JulesSessionEntity>>

    @Query("SELECT * FROM jules_sessions WHERE isArchived = 0")
    fun getAllSessionsFlow(): Flow<List<JulesSessionEntity>>

    @Query("SELECT * FROM jules_sessions WHERE sourceName = :sourceName AND isArchived = 0 AND (prState IN ('merged', 'closed') OR (prUrl IS NULL AND sessionState IN ('COMPLETED', 'FAILED')))")
    suspend fun getCompletedSessions(sourceName: String): List<JulesSessionEntity>

    @Query("SELECT * FROM jules_sessions WHERE isArchived = 0 AND (prState IN ('merged', 'closed') OR (prUrl IS NULL AND sessionState IN ('COMPLETED', 'FAILED')))")
    suspend fun getAllCompletedSessions(): List<JulesSessionEntity>

    @Query("SELECT * FROM jules_sessions WHERE sourceName = :sourceName AND apiKey = :apiKey AND isArchived = 0")
    suspend fun getSessionsBySourceAndKey(sourceName: String, apiKey: String): List<JulesSessionEntity>

    @Query("UPDATE jules_sessions SET isArchived = 1 WHERE id = :sessionId")
    suspend fun archiveSession(sessionId: String)

    @Query("UPDATE jules_sessions SET isArchived = 1 WHERE id IN (:sessionIds)")
    suspend fun archiveSessions(sessionIds: List<String>)

    @Query("SELECT * FROM jules_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): JulesSessionEntity?

    @Query("SELECT * FROM jules_sessions WHERE featureId = :featureId")
    suspend fun getSessionsByFeature(featureId: String): List<JulesSessionEntity>

    @Query("UPDATE jules_sessions SET prState = :state, prMergeable = :mergeable, prMergeableState = :mergeableState WHERE id = :sessionId")
    suspend fun updateSessionPrStatus(sessionId: String, state: String, mergeable: Boolean?, mergeableState: String?)

    @Query("UPDATE jules_sessions SET prBranch = :branch, prRepo = :repo WHERE id = :sessionId")
    suspend fun updateSessionGitHubDetails(sessionId: String, branch: String?, repo: String?)

    @Query("UPDATE jules_sessions SET sessionState = :state WHERE id = :sessionId")
    suspend fun updateSessionState(sessionId: String, state: String?)

    @Query("UPDATE jules_sessions SET lastUpdated = :lastUpdated WHERE id = :sessionId")
    suspend fun updateSessionLastUpdated(sessionId: String, lastUpdated: Long)

    @Query("UPDATE jules_sessions SET featureId = :featureId WHERE id = :sessionId")
    suspend fun updateSessionFeature(sessionId: String, featureId: String?)

    @Query(
        """
        SELECT * FROM jules_sessions
        WHERE isArchived = 0
        AND (sessionState IS NULL OR sessionState NOT IN ('COMPLETED', 'FAILED', 'QUEUED_OFFLINE'))
        AND (prState IS NULL OR prState IN ('open', 'draft'))
        """
    )
    suspend fun getActiveSessions(): List<JulesSessionEntity>

    @Query("SELECT * FROM jules_sessions WHERE isPendingOffline = 1 ORDER BY queuedAt ASC")
    suspend fun getPendingOfflineSessions(): List<JulesSessionEntity>

    @Query("SELECT * FROM jules_activities WHERE isPendingOffline = 1 ORDER BY sortTimestamp ASC")
    suspend fun getPendingOfflineActivities(): List<JulesActivityEntity>

    @Query("DELETE FROM jules_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE jules_activities SET sessionId = :newSessionId WHERE sessionId = :oldSessionId")
    suspend fun updateActivitiesSessionId(oldSessionId: String, newSessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<JulesActivityEntity>)

    @Query("SELECT * FROM jules_activities WHERE sessionId = :sessionId ORDER BY sortTimestamp ASC")
    suspend fun getActivitiesBySession(sessionId: String): List<JulesActivityEntity>

    @Query("SELECT * FROM jules_activities WHERE sessionId = :sessionId ORDER BY sortTimestamp ASC")
    fun getActivitiesFlowBySession(sessionId: String): Flow<List<JulesActivityEntity>>

    @Query("DELETE FROM jules_activities WHERE sessionId = :sessionId AND isPendingOffline = 0 AND type NOT IN ('github_log', 'error', 'failure')")
    suspend fun clearActivitiesBySession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<JulesSourceEntity>)

    @Query("SELECT * FROM jules_sources")
    suspend fun getSources(): List<JulesSourceEntity>

    @Query("SELECT * FROM jules_sources")
    fun getSourcesFlow(): Flow<List<JulesSourceEntity>>

    @Query("DELETE FROM jules_sources")
    suspend fun clearSources()

    /**
     * Atomically swap the cached source list. Wrapping the clear + insert in a single
     * transaction means the [getSourcesFlow] cache never momentarily emits an empty list
     * during a background refresh (which would blink the UI back to the loading/empty state).
     */
    @Transaction
    suspend fun replaceSources(sources: List<JulesSourceEntity>) {
        clearSources()
        insertSources(sources)
    }
}

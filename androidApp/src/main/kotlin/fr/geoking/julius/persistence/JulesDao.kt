package fr.geoking.julius.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface JulesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<JulesSessionEntity>)

    @Query("SELECT * FROM jules_sessions WHERE sourceName = :sourceName AND isArchived = 0 ORDER BY lastUpdated DESC")
    suspend fun getSessionsBySource(sourceName: String): List<JulesSessionEntity>

    @Query("UPDATE jules_sessions SET isArchived = 1 WHERE id = :sessionId")
    suspend fun archiveSession(sessionId: String)

    @Query("SELECT * FROM jules_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): JulesSessionEntity?

    @Query("UPDATE jules_sessions SET prState = :state, prMergeable = :mergeable WHERE id = :sessionId")
    suspend fun updateSessionPrStatus(sessionId: String, state: String, mergeable: Boolean?)

    @Query("UPDATE jules_sessions SET sessionState = :state WHERE id = :sessionId")
    suspend fun updateSessionState(sessionId: String, state: String?)

    @Query("UPDATE jules_sessions SET lastUpdated = :lastUpdated WHERE id = :sessionId")
    suspend fun updateSessionLastUpdated(sessionId: String, lastUpdated: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<JulesActivityEntity>)

    @Query("SELECT * FROM jules_activities WHERE sessionId = :sessionId ORDER BY sortTimestamp ASC")
    suspend fun getActivitiesBySession(sessionId: String): List<JulesActivityEntity>

    @Query("DELETE FROM jules_activities WHERE sessionId = :sessionId")
    suspend fun clearActivitiesBySession(sessionId: String)
}

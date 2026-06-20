package fr.geoking.julius.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatureDao {
    @Query("SELECT * FROM features WHERE isArchived = 0 ORDER BY position ASC")
    fun getAllFeaturesFlow(): Flow<List<FeatureEntity>>
    @Query("SELECT * FROM features WHERE id = :id")
    suspend fun getFeature(id: String): FeatureEntity?

    @Query("SELECT * FROM features WHERE id = :id")
    fun getFeatureFlow(id: String): Flow<FeatureEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeature(feature: FeatureEntity)

    @Update
    suspend fun updateFeature(feature: FeatureEntity)

    @Update
    suspend fun updateFeatures(features: List<FeatureEntity>)

    @Query("DELETE FROM features WHERE id = :id")
    suspend fun deleteFeature(id: String)

    @Query("SELECT MAX(position) FROM features WHERE isArchived = 0")
    suspend fun getMaxPosition(): Int?

    @Query("SELECT * FROM features WHERE status = 'PENDING' AND isArchived = 0 ORDER BY priority DESC, position ASC LIMIT 1")
    suspend fun getNextPendingFeature(): FeatureEntity?

    @Query("UPDATE features SET status = 'QUEUED', updatedAt = :now WHERE id = :id")
    suspend fun markFeatureQueued(id: String, now: Long)

    /**
     * Atomically claim the next PENDING feature by flipping it to QUEUED inside a single
     * transaction, returning the feature that was claimed (or null if none are pending).
     *
     * This is the guard against duplicate conversations: SQLite serializes write
     * transactions, so two concurrent queue ticks can never both claim the same feature —
     * the loser sees it already QUEUED and moves on. Without this, the read-then-mark gap
     * let a single new feature start two sessions.
     */
    @Transaction
    suspend fun claimNextPendingFeature(now: Long): FeatureEntity? {
        val next = getNextPendingFeature() ?: return null
        markFeatureQueued(next.id, now)
        return next
    }

    @Query("SELECT COUNT(*) FROM features WHERE (status = 'IN_PROGRESS' OR status = 'QUEUED') AND isArchived = 0")
    suspend fun getActiveFeaturesCount(): Int

    @Query("SELECT * FROM features WHERE (status = 'QUEUED' OR status = 'IN_PROGRESS') AND isArchived = 0")
    suspend fun getActiveFeatures(): List<FeatureEntity>

    @Query("SELECT COUNT(*) FROM features WHERE status IN ('QUEUED', 'IN_PROGRESS', 'COMPLETED') AND updatedAt >= :since AND isArchived = 0")
    suspend fun getRecentlyStartedFeaturesCount(since: Long): Int

    @Query("SELECT COUNT(*) FROM features WHERE status = 'PENDING' AND isArchived = 0")
    suspend fun getPendingFeaturesCount(): Int

    @Query("SELECT COUNT(*) FROM features WHERE status IN ('QUEUED', 'IN_PROGRESS') AND isArchived = 0")
    suspend fun getQueuedOrInProgressCount(): Int

    @Query("UPDATE features SET status = 'PENDING', sessionId = null, errorMessage = null WHERE status = 'FAILED' AND isArchived = 0 AND (:sourceName IS NULL OR sourceName = :sourceName)")
    suspend fun retryFailedFeatures(sourceName: String?)

    @Query("UPDATE features SET isArchived = 1 WHERE status = 'COMPLETED' AND isArchived = 0 AND (:sourceName IS NULL OR sourceName = :sourceName)")
    suspend fun archiveCompletedFeatures(sourceName: String?)

    @Query("DELETE FROM features WHERE (:sourceName IS NULL OR sourceName = :sourceName)")
    suspend fun deleteAllFeatures(sourceName: String?)
}

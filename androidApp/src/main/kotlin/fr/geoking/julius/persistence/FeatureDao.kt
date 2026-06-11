package fr.geoking.julius.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatureDao {
    @Query("SELECT * FROM features ORDER BY position ASC")
    fun getAllFeaturesFlow(): Flow<List<FeatureEntity>>

    @Query("SELECT * FROM features WHERE id = :id")
    suspend fun getFeature(id: String): FeatureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeature(feature: FeatureEntity)

    @Update
    suspend fun updateFeature(feature: FeatureEntity)

    @Update
    suspend fun updateFeatures(features: List<FeatureEntity>)

    @Query("DELETE FROM features WHERE id = :id")
    suspend fun deleteFeature(id: String)

    @Query("SELECT MAX(position) FROM features")
    suspend fun getMaxPosition(): Int?

    @Query("SELECT * FROM features WHERE status = 'PENDING' ORDER BY priority DESC, position ASC LIMIT 1")
    suspend fun getNextPendingFeature(): FeatureEntity?

    @Query("SELECT COUNT(*) FROM features WHERE status = 'IN_PROGRESS' OR status = 'QUEUED'")
    suspend fun getActiveFeaturesCount(): Int

    @Query("SELECT * FROM features WHERE status = 'QUEUED' OR status = 'IN_PROGRESS'")
    suspend fun getActiveFeatures(): List<FeatureEntity>

    @Query("SELECT COUNT(*) FROM features WHERE status IN ('QUEUED', 'IN_PROGRESS', 'COMPLETED') AND updatedAt >= :since")
    suspend fun getRecentlyStartedFeaturesCount(since: Long): Int

    @Query("SELECT COUNT(*) FROM features WHERE status = 'PENDING'")
    suspend fun getPendingFeaturesCount(): Int

    @Query("SELECT COUNT(*) FROM features WHERE status IN ('QUEUED', 'IN_PROGRESS')")
    suspend fun getQueuedOrInProgressCount(): Int
}

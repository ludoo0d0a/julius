package fr.geoking.julius.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.geoking.julius.persistence.FeatureDao
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.worker.FeatureSchedulerWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FeatureRepository(
    private val context: Context,
    private val featureDao: FeatureDao,
    private val julesRepository: JulesRepository
) {
    fun getAllFeatures(): Flow<List<FeatureEntity>> = featureDao.getAllFeaturesFlow()

    suspend fun getFeature(id: String): FeatureEntity? = featureDao.getFeature(id)

    suspend fun addFeature(title: String, description: String, priority: Int, sourceName: String) {
        val maxPos = featureDao.getMaxPosition() ?: -1
        val feature = FeatureEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            priority = priority,
            position = maxPos + 1,
            sourceName = sourceName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        featureDao.insertFeature(feature)
    }

    suspend fun updateFeature(feature: FeatureEntity) {
        featureDao.updateFeature(feature.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFeature(id: String) {
        featureDao.deleteFeature(id)
    }

    suspend fun updatePositions(features: List<FeatureEntity>) {
        features.forEachIndexed { index, feature ->
            featureDao.updateFeature(feature.copy(position = index, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun startFeature(featureId: String, apiKeys: List<String>): String {
        val feature = featureDao.getFeature(featureId) ?: throw Exception("Feature not found")
        val sessionId = julesRepository.createSession(
            apiKeys = apiKeys,
            prompt = feature.description,
            source = feature.sourceName,
            title = feature.title
        )
        featureDao.updateFeature(feature.copy(
            sessionId = sessionId,
            status = "QUEUED",
            updatedAt = System.currentTimeMillis()
        ))
        scheduleWorker()
        return sessionId
    }

    fun scheduleWorker() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<FeatureSchedulerWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (e: Exception) {
            android.util.Log.e("FeatureRepository", "Failed to schedule worker", e)
        }
    }
}

package fr.geoking.julius.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.geoking.julius.persistence.FeatureDao
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.queue.AgentAccount
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

    suspend fun addFeature(title: String, description: String, priority: Int, sourceName: String): String {
        val maxPos = featureDao.getMaxPosition() ?: -1
        val id = UUID.randomUUID().toString()
        val feature = FeatureEntity(
            id = id,
            title = title,
            description = description,
            priority = priority,
            position = maxPos + 1,
            sourceName = sourceName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        featureDao.insertFeature(feature)
        scheduleWorker()
        return id
    }

    suspend fun updateFeature(feature: FeatureEntity) {
        featureDao.updateFeature(feature.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFeature(id: String) {
        featureDao.deleteFeature(id)
    }

    suspend fun updatePositions(features: List<FeatureEntity>) {
        val now = System.currentTimeMillis()
        val updated = features.mapIndexed { index, feature ->
            feature.copy(position = index, updatedAt = now)
        }
        featureDao.updateFeatures(updated)
    }

    suspend fun startFeature(featureId: String, account: AgentAccount): String {
        val feature = featureDao.getFeature(featureId) ?: throw Exception("Feature not found")
        val prompt = feature.description.ifBlank { feature.title }
        return startFeatureInternal(feature, account, prompt)
    }

    suspend fun startFeatureWithTitle(featureId: String, account: AgentAccount): String {
        val feature = featureDao.getFeature(featureId) ?: throw Exception("Feature not found")
        return startFeatureInternal(feature, account, feature.title)
    }

    private suspend fun startFeatureInternal(feature: FeatureEntity, account: AgentAccount, prompt: String): String {
        if (feature.sessionId != null) return feature.sessionId

        val now = System.currentTimeMillis()
        val sessionId = julesRepository.createSession(
            account = account,
            prompt = prompt,
            source = feature.sourceName,
            title = feature.title,
            featureId = feature.id,
        )
        featureDao.updateFeature(
            feature.copy(
                sessionId = sessionId,
                assignedAccountId = account.id,
                startedAt = now,
                status = "QUEUED",
                updatedAt = now,
            ),
        )
        scheduleWorker()
        return sessionId
    }

    suspend fun replayFeature(featureId: String, account: AgentAccount): String {
        val feature = featureDao.getFeature(featureId) ?: throw Exception("Feature not found")

        // Collect all user prompts from all sessions linked to this feature
        val sessions = julesRepository.getSessionsByFeature(featureId)
        val allUserPrompts = mutableListOf<String>()

        // Add initial feature description if present
        if (feature.description.isNotBlank()) {
            allUserPrompts.add(feature.description)
        }

        for (session in sessions) {
            val activities = julesRepository.getActivitiesBySession(session.id)
            allUserPrompts.addAll(activities.filter { it.originator == "user" }.map { it.text })
        }

        if (allUserPrompts.isEmpty()) {
            throw Exception("No prompts found to replay for this feature.")
        }

        val combinedPrompt = "Please replay the following developer intent on a fresh branch:\n\n" +
            allUserPrompts.joinToString("\n---\n")

        val now = System.currentTimeMillis()
        val sessionId = julesRepository.createSession(
            account = account,
            prompt = combinedPrompt,
            source = feature.sourceName,
            title = "Replay: ${feature.title}",
            featureId = featureId,
        )

        featureDao.updateFeature(
            feature.copy(
                sessionId = sessionId,
                assignedAccountId = account.id,
                startedAt = now,
                status = "QUEUED",
                updatedAt = now,
            ),
        )
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

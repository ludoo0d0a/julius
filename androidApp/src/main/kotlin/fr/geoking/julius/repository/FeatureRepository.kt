package fr.geoking.julius.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.geoking.julius.persistence.FeatureDao
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.queue.AgentAccount
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.worker.FeatureSchedulerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class FeatureRepository(
    private val context: Context,
    private val featureDao: FeatureDao,
    private val julesRepository: JulesRepository
) {
    private val promotingSessionIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun getAllFeatures(): Flow<List<FeatureEntity>> = featureDao.getAllFeaturesFlow()

    suspend fun getFeature(id: String): FeatureEntity? = featureDao.getFeature(id)

    fun getFeatureFlow(id: String): Flow<FeatureEntity?> = featureDao.getFeatureFlow(id)

    suspend fun addFeature(
        title: String,
        description: String,
        priority: Int,
        sourceName: String,
        status: String = "PENDING",
        sessionId: String? = null,
    ): String {
        val maxPos = featureDao.getMaxPosition() ?: -1
        val id = UUID.randomUUID().toString()
        val feature = FeatureEntity(
            id = id,
            title = title,
            description = description,
            priority = priority,
            position = maxPos + 1,
            sourceName = sourceName,
            status = status,
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        featureDao.insertFeature(feature)
        scheduleWorker()
        return id
    }

    suspend fun updateFeature(feature: FeatureEntity) {
        featureDao.updateFeature(feature.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateFeatureStatus(featureId: String, status: String) {
        val feature = featureDao.getFeature(featureId) ?: return
        featureDao.updateFeature(feature.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleArchiveFeature(featureId: String) {
        val feature = featureDao.getFeature(featureId) ?: return
        featureDao.updateFeature(feature.copy(isArchived = !feature.isArchived, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFeature(id: String) {
        featureDao.deleteFeature(id)
    }

    suspend fun deleteAllFeatures(sourceName: String? = null) {
        featureDao.deleteAllFeatures(sourceName)
    }

    suspend fun retryFailedFeatures(sourceName: String? = null) {
        featureDao.retryFailedFeatures(sourceName)
        scheduleWorker()
    }

    suspend fun archiveCompletedFeatures(sourceName: String? = null) {
        featureDao.archiveCompletedFeatures(sourceName)
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

    fun autoPromoteOrphans(scope: CoroutineScope, sourceName: String, incoming: List<JulesSessionEntity>) {
        val orphans = incoming.filter { it.featureId.isNullOrBlank() }
        for (session in orphans) {
            if (!promotingSessionIds.add(session.id)) continue
            scope.launch {
                try {
                    val initialStatus = calculateFeatureStatus(listOf(session))
                    val featureId = addFeature(
                        title = session.title.ifBlank { "Conversation" },
                        description = session.prompt,
                        priority = 0,
                        sourceName = sourceName,
                        status = initialStatus,
                        sessionId = session.id,
                    )
                    julesRepository.linkSessionToFeature(session.id, featureId)
                } catch (e: Exception) {
                    android.util.Log.e("FeatureRepository", "Auto promotion failed for ${session.id}", e)
                } finally {
                    promotingSessionIds.remove(session.id)
                }
            }
        }
    }

    /**
     * Derives feature status from its associated sessions.
     * Terminated if all sessions are finished.
     */
    fun calculateFeatureStatus(sessions: List<JulesSessionEntity>): String {
        if (sessions.isEmpty()) return "PENDING"

        val allFinished = sessions.all { it.isFinished }
        if (allFinished) {
            val anySuccess = sessions.any {
                it.prState == "merged" || it.sessionState == "COMPLETED"
            }
            return if (anySuccess) "COMPLETED" else "FAILED"
        }

        val anyInProgress = sessions.any {
            it.sessionState in setOf("PLANNING", "ACTIVE", "AWAITING_PLAN_APPROVAL")
        }
        return if (anyInProgress) "IN_PROGRESS" else "QUEUED"
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

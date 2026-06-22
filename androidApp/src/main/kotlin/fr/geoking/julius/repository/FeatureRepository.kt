package fr.geoking.julius.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.geoking.julius.persistence.FeatureDao
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.queue.AgentAccount
import fr.geoking.julius.debug.DbCacheDebugTracker
import fr.geoking.julius.debug.DbEntityKind
import fr.geoking.julius.worker.FeatureSchedulerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

import fr.geoking.julius.SettingsManager

class FeatureRepository(
    private val context: Context,
    private val featureDao: FeatureDao,
    private val julesRepository: JulesRepository,
    private val settingsManager: SettingsManager,
    private val dbCacheDebugTracker: DbCacheDebugTracker,
) {
    private val promotingSessionIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun getAllFeatures(): Flow<List<FeatureEntity>> = featureDao.getAllFeaturesFlow()

    suspend fun getFeature(id: String): FeatureEntity? = featureDao.getFeature(id)

    fun getFeatureFlow(id: String): Flow<FeatureEntity?> = featureDao.getFeatureFlow(id)

    suspend fun addFeature(
        title: String,
        prompt: String,
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
            description = prompt,
            priority = priority,
            position = maxPos + 1,
            sourceName = sourceName,
            status = status,
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        featureDao.insertFeature(feature)
        dbCacheDebugTracker.record(DbEntityKind.FEATURES, saved = 1)
        scheduleWorker()
        return id
    }

    suspend fun updateFeature(feature: FeatureEntity) {
        featureDao.updateFeature(feature.copy(updatedAt = System.currentTimeMillis()))
        dbCacheDebugTracker.record(DbEntityKind.FEATURES, updated = 1)
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

    suspend fun startFeature(
        featureId: String,
        account: AgentAccount,
        requirePlanApproval: Boolean? = null
    ): String {
        val feature = featureDao.getFeature(featureId) ?: throw Exception("Feature not found")
        val prompt = feature.description.ifBlank { feature.title }
        return startFeatureInternal(feature, account, prompt, requirePlanApproval)
    }

    suspend fun startFeatureWithTitle(
        featureId: String,
        account: AgentAccount,
        requirePlanApproval: Boolean? = null
    ): String {
        val feature = featureDao.getFeature(featureId) ?: throw Exception("Feature not found")
        return startFeatureInternal(feature, account, feature.title, requirePlanApproval)
    }

    /**
     * Starts a new conversation on the same feature, using the PR title and description
     * as the session title and initial prompt (conflict replay).
     */
    suspend fun startConversationFromPr(
        sessionId: String,
        githubToken: String,
        account: AgentAccount,
        requirePlanApproval: Boolean? = null,
    ): String {
        val session = julesRepository.getSession(sessionId) ?: throw Exception("Session not found")
        if (session.prUrl.isNullOrBlank()) throw Exception("No PR on this session")

        val featureId = session.featureId
            ?: featureDao.getFeatureBySessionId(sessionId)?.id
            ?: throw Exception("No feature linked to this session")
        val feature = featureDao.getFeature(featureId) ?: throw Exception("Feature not found")

        val (title, prompt) = julesRepository.resolvePrConversationPrompt(session, githubToken)
        return startFeatureInternal(feature, account, prompt, requirePlanApproval, conversationTitle = title)
    }

    private suspend fun startFeatureInternal(
        feature: FeatureEntity,
        account: AgentAccount,
        prompt: String,
        requirePlanApproval: Boolean? = null,
        conversationTitle: String? = null,
    ): String {
        val now = System.currentTimeMillis()

        // 1. Mark feature as QUEUED immediately to avoid race conditions in the queue engine
        // (prevents duplicate session creation if multiple ticks happen during the network call).
        featureDao.updateFeature(
            feature.copy(
                status = "QUEUED",
                assignedAccountId = account.id,
                startedAt = now,
                updatedAt = now,
            ),
        )

        // 2. Actually create the session (network call)
        // A start failure (e.g. Jules 400 invalid_argument) must surface on the conversation,
        // not crash and not mark the feature FAILED. Record a FAILED session instead.
        val sessionId = try {
            julesRepository.createSession(
                account = account,
                prompt = prompt,
                source = feature.sourceName,
                title = conversationTitle ?: feature.title,
                featureId = feature.id,
                requirePlanApproval = requirePlanApproval,
            )
        } catch (e: Exception) {
            android.util.Log.e("FeatureRepository", "createSession failed for ${feature.id}", e)
            julesRepository.createFailedSession(
                feature.sourceName,
                conversationTitle ?: feature.title,
                prompt,
                feature.id,
                e.message,
            )
        }

        // 3. Link the sessionId to the feature
        featureDao.updateFeature(
            feature.copy(
                sessionId = sessionId,
                assignedAccountId = account.id,
                startedAt = now,
                status = "QUEUED",
                updatedAt = System.currentTimeMillis(),
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

    suspend fun promoteOrphanSessions(sourceName: String, incoming: List<JulesSessionEntity>) {
        if (!settingsManager.settings.value.autoCreateFeaturesFromSessions) return
        for (session in incoming.filter { it.featureId.isNullOrBlank() }) {
            if (!promotingSessionIds.add(session.id)) continue
            try {
                val initialStatus = calculateFeatureStatus(listOf(session))
                val featureId = addFeature(
                    title = session.title.ifBlank { "Conversation" },
                    prompt = session.prompt.ifBlank { session.title }.ifBlank { "Conversation" },
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

    fun autoPromoteOrphans(scope: CoroutineScope, sourceName: String, incoming: List<JulesSessionEntity>) {
        scope.launch {
            promoteOrphanSessions(sourceName, incoming)
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

    suspend fun refreshFeatures(sourceName: String?, apiKeys: List<String>, githubToken: String) {
        val op = if (sourceName != null) "refreshFeatures:$sourceName" else "refreshFeatures:all"
        dbCacheDebugTracker.begin(op)
        try {
            julesRepository.syncOfflineData()
            if (sourceName != null) {
                reconcileSourceFeatures(sourceName, apiKeys)
            } else {
                julesRepository.refreshSources(apiKeys)
                val sources = runCatching { julesRepository.getSourcesCached() }.getOrDefault(emptyList())
                for (src in sources) {
                    try {
                        reconcileSourceFeatures(src.name, apiKeys)
                    } catch (e: Exception) {
                        android.util.Log.e("FeatureRepository", "Global reconcile failed for ${src.name}", e)
                    }
                }
            }
            dbCacheDebugTracker.finish()
        } catch (e: Exception) {
            dbCacheDebugTracker.finish(e.message)
            throw e
        }
    }

    /** Lists recent Jules sessions for a repo and materializes orphan sessions as features. */
    private suspend fun reconcileSourceFeatures(sourceName: String, apiKeys: List<String>) {
        julesRepository.refreshSessionsInternal(
            apiKeys = apiKeys,
            sourceName = sourceName,
            pageSize = 10,
            refreshActivities = false,
        )
        val sessions = julesRepository.getSessionsBySource(sourceName)
        promoteOrphanSessions(sourceName, sessions)
    }

    fun scheduleWorker() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<FeatureSchedulerWorker>()
                .setConstraints(constraints)
                .build()
            // Unique work so repeated triggers (e.g. addFeature + a UI re-trigger) collapse
            // into a single serialized worker run instead of two concurrent ticks.
            WorkManager.getInstance(context).enqueueUniqueWork(
                FeatureSchedulerWorker.WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        } catch (e: Exception) {
            android.util.Log.e("FeatureRepository", "Failed to schedule worker", e)
        }
    }
}

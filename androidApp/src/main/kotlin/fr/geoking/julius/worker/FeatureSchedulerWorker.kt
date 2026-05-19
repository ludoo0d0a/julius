package fr.geoking.julius.worker

import android.content.Context
import androidx.work.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.github.parseGitHubPullRequestUrl
import fr.geoking.julius.persistence.FeatureDao
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.repository.JulesRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class FeatureSchedulerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val featureDao: FeatureDao by inject()
    private val julesRepository: JulesRepository by inject()
    private val settingsManager: SettingsManager by inject()
    private val githubClient: GitHubClient by inject()

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.DAYS.toMillis(1)
        val recentlyStartedCount = featureDao.getRecentlyStartedFeaturesCount(since)

        if (recentlyStartedCount < 15) {
            val activeCount = featureDao.getActiveFeaturesCount()
            if (activeCount < 3) {
                val nextFeature = featureDao.getNextPendingFeature()
                if (nextFeature != null) {
                    try {
                        val settings = settingsManager.settings.value
                        val apiKeys = settings.julesKeys
                        if (apiKeys.isNotEmpty()) {
                            val sessionId = julesRepository.createSession(
                                apiKeys = apiKeys,
                                prompt = nextFeature.description,
                                source = nextFeature.sourceName,
                                title = nextFeature.title
                            )
                            featureDao.updateFeature(nextFeature.copy(
                                sessionId = sessionId,
                                status = "QUEUED",
                                updatedAt = System.currentTimeMillis()
                            ))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FeatureSchedulerWorker", "Failed to push feature ${nextFeature.id}", e)
                    }
                }
            }
        }

        // Check status of in-progress features
        val activeFeatures = featureDao.getActiveFeatures()
        val githubToken = settingsManager.settings.value.githubApiKey

        for (feature in activeFeatures) {
            val sessionId = feature.sessionId ?: continue
            try {
                julesRepository.pollSessionStatus(sessionId, githubToken)
                val session = julesRepository.getSession(sessionId)
                if (session != null) {
                    val newStatus = when {
                        session.prState == "merged" -> "COMPLETED"
                        session.sessionState == "COMPLETED" && session.prUrl == null -> "COMPLETED"
                        session.sessionState == "FAILED" -> "FAILED"
                        session.sessionState == "AWAITING_PLAN_APPROVAL" -> "IN_PROGRESS"
                        session.sessionState == "PLANNING" -> "IN_PROGRESS"
                        session.sessionState == "ACTIVE" -> "IN_PROGRESS"
                        else -> feature.status
                    }
                    if (newStatus != feature.status) {
                        featureDao.updateFeature(feature.copy(
                            status = newStatus,
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FeatureSchedulerWorker", "Failed to poll status for feature ${feature.id}", e)
            }
        }

        // Reschedule if there are pending features or if we have active features to poll
        val hasPending = featureDao.getNextPendingFeature() != null
        val hasActive = featureDao.getActiveFeaturesCount() > 0

        if (hasPending || hasActive) {
            reschedule()
        }

        return Result.success()
    }

    private fun reschedule() {
        val request = OneTimeWorkRequestBuilder<FeatureSchedulerWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "FeatureSchedulerWorker",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

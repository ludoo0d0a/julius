package fr.geoking.julius.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.geoking.julius.queue.CodingAgentQueueEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class FeatureSchedulerWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val queueEngine: CodingAgentQueueEngine by inject()

    override suspend fun doWork(): Result {
        queueEngine.tick()
        if (queueEngine.hasWorkToDo()) {
            reschedule()
        }
        return Result.success()
    }

    private fun reschedule() {
        val request = OneTimeWorkRequestBuilder<FeatureSchedulerWorker>()
            .setInitialDelay(2, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "FeatureSchedulerWorker"
    }
}

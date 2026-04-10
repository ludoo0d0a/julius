package fr.geoking.julius.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.geoking.julius.repository.JulesRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class JulesSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: JulesRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            repository.syncOfflineData()
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("JulesSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}

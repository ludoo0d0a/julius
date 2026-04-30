package fr.geoking.julius

import android.content.Context
import coil3.SingletonImageLoader
import fr.geoking.julius.shared.logging.DebugLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CacheManager {
    suspend fun clearAllCaches(context: Context) {
        // 1. Network Debug Logs (Memory-only, fast)
        try {
            DebugLogStore.clearLogs()
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "Error clearing DebugLogStore", e)
        }

        // 2. Disk-intensive operations
        withContext(Dispatchers.IO) {
            // Coil Image Cache
            try {
                val imageLoader = SingletonImageLoader.get(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Error clearing Coil cache", e)
            }

            // Cache Directory (temp files, voice recordings, etc.)
            try {
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    val files = cacheDir.listFiles()
                    if (files != null) {
                        for (file in files) {
                            file.deleteRecursively()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CacheManager", "Error clearing cache directory", e)
            }
        }
    }
}

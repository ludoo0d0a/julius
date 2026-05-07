package fr.geoking.julius

import android.app.Application
import android.system.Os
import android.system.OsConstants
import fr.geoking.julius.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class JuliusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("JuliusApplication", "onCreate start")
        runCatching {
            val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE)
            android.util.Log.i("JuliusApplication", "Device page size: ${pageSize} bytes")
            if (pageSize >= 16 * 1024) {
                android.util.Log.w(
                    "JuliusApplication",
                    "Running on 16KB page size device. Ensure all native dependencies ship 16KB-compatible .so (Gradle task: checkNative16kPageSize)."
                )
            }
        }.onFailure { t ->
            android.util.Log.w("JuliusApplication", "Failed to read device page size", t)
        }
        try {
            startKoin {
                androidContext(this@JuliusApplication)
                modules(appModule)
            }
            android.util.Log.d("JuliusApplication", "Koin started OK")
        } catch (e: Throwable) {
            initError = e
            android.util.Log.e("JuliusApplication", "Koin/DI init failed", e)
        }
    }

    companion object {
        /** Set when startKoin or module init fails; MainActivity shows this instead of crashing. */
        @Volatile
        var initError: Throwable? = null
            private set
    }
}

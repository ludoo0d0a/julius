package fr.geoking.julius

import android.app.Application
import fr.geoking.julius.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VoiceApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("VoiceApplication", "onCreate start")
        try {
            startKoin {
                androidContext(this@VoiceApplication)
                modules(appModule)
            }
            android.util.Log.d("VoiceApplication", "Koin started OK")
        } catch (e: Throwable) {
            initError = e
            android.util.Log.e("VoiceApplication", "Koin/DI init failed", e)
        }
    }

    companion object {
        /** Set when startKoin or module init fails; MainActivity shows this instead of crashing. */
        @Volatile
        var initError: Throwable? = null
            private set
    }
}

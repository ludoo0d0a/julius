package fr.geoking.julius

import android.app.Application
import fr.geoking.julius.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VoiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@VoiceApplication)
            modules(appModule)
        }
    }
}

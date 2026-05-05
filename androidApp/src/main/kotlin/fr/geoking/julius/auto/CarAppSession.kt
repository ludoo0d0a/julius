package fr.geoking.julius.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.geoking.julius.JuliusApplication
import fr.geoking.julius.feature.voice.AndroidVoiceManager
import fr.geoking.julius.shared.voice.VoiceManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Android Auto session entry point.
 */
class CarAppSession : Session(), KoinComponent {

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    (get<VoiceManager>() as? AndroidVoiceManager)?.setCarContext(null)
                }
            }
        )
    }

    override fun onCreateScreen(intent: Intent): Screen {
        // Start border monitoring service from Android Auto as well
        try {
            val serviceIntent = Intent(carContext, BorderMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                carContext.startForegroundService(serviceIntent)
            } else {
                carContext.startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("CarAppSession", "Failed to start BorderMonitorService", e)
        }

        (get<VoiceManager>() as? AndroidVoiceManager)?.setCarContext(carContext)

        val initError = JuliusApplication.initError
        if (initError != null) {
            return ErrorScreen(
                carContext,
                errorMessage = "Initialization failed",
                errorDetail = initError.message ?: initError.toString()
            )
        }

        (get<VoiceManager>() as? AndroidVoiceManager)?.setCarContext(carContext)

        return AutoHomeScreen(
            carContext,
            store = get(),
            settingsManager = get(),
            julesClient = get(),
            julesRepository = get()
        )
    }
}

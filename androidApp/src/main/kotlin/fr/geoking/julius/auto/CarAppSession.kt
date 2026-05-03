package fr.geoking.julius.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Template
import fr.geoking.julius.JuliusApplication
import fr.geoking.julius.feature.voice.AndroidVoiceManager
import fr.geoking.julius.shared.voice.VoiceManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Android Auto session entry point.
 */
class CarAppSession : Session(), KoinComponent {
    override fun onCreateScreen(intent: Intent): Screen {
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


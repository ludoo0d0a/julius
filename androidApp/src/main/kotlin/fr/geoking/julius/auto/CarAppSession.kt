package fr.geoking.julius.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Template
import fr.geoking.julius.JuliusApplication
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

        return AutoHomeScreen(
            carContext,
            store = get(),
            settingsManager = get(),
            julesClient = get(),
            julesRepository = get()
        )
    }
}


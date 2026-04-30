package fr.geoking.julius.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geoking.julius.JuliusApplication

/**
 * Android Auto session entry point.
 *
 * POI + vehicle features were removed from the project; this keeps a minimal, stable Auto surface
 * that can be expanded later without bringing back the old POI stack.
 */
class CarAppSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val initError = JuliusApplication.initError
        return if (initError != null) {
            SimpleMessageScreen(
                carContext,
                title = "Initialization failed",
                message = initError.message ?: initError.toString()
            )
        } else {
            SimpleMessageScreen(
                carContext,
                title = "Julius",
                message = "Android Auto UI is currently minimal."
            )
        }
    }
}

private class SimpleMessageScreen(
    carContext: CarContext,
    private val title: String,
    private val message: String
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(message.take(500))
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .build()
    }
}


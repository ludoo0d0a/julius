package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import fr.geoking.julius.poi.Poi

/**
 * Screen showing active navigation guidance.
 * Note: NavigationTemplate is restricted to the NAVIGATION category.
 * This screen now uses MessageTemplate for compliance.
 */
class GuidanceScreen(
    carContext: CarContext,
    private val destination: Poi
) : Screen(carContext) {

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "GuidanceScreen") {
        MessageTemplate.Builder("Navigation to ${destination.name} started.\n\nActive guidance requires the app to be in the NAVIGATION category. Standard apps should use intent-based navigation for a better user experience.")
            .setHeader(
                Header.Builder()
                    .setTitle("Navigation")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Exit")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()
    }
}

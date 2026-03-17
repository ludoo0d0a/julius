package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager

class AutoGeneralFiltersScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Energy Types")
                .addText(settings.selectedMapEnergyTypes.joinToString(", ").take(100))
                .setOnClickListener {
                    screenManager.push(AutoMapEnergySelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Enseigne")
                .addText(settings.mapEnseigneType)
                .setOnClickListener {
                    screenManager.push(AutoMapEnseigneSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Services")
                .addText(settings.selectedMapServices.joinToString(", ").take(100))
                .setOnClickListener {
                    screenManager.push(AutoMapServicesSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("General Filters").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

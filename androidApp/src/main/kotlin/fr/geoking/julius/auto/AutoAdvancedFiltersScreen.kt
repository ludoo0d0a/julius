package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.PoiProviderType

class AutoAdvancedFiltersScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        if (settings.selectedPoiProvider == PoiProviderType.DataGouvElec || settings.selectedPoiProvider == PoiProviderType.OpenChargeMap) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Opérateur")
                    .addText(settings.mapIrveOperator)
                    .setOnClickListener {
                        screenManager.push(AutoMapIrveOperatorSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Min. Power")
                    .addText("${settings.mapMinPowerKw} kW")
                    .setOnClickListener {
                        screenManager.push(AutoMapIrvePowerSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )

            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Connectors")
                    .addText(settings.selectedMapConnectorTypes.joinToString(", ").take(100))
                    .setOnClickListener {
                        screenManager.push(AutoMapConnectorSelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        if (settings.selectedPoiProvider == PoiProviderType.Overpass) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("POI Types")
                    .addText(settings.selectedOverpassAmenityTypes.joinToString(", ").take(100))
                    .setOnClickListener {
                        screenManager.push(AutoMapOverpassAmenitySelectionScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Advanced Filters").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

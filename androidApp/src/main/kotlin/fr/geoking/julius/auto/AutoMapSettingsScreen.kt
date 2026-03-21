package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.PoiProviderType

class AutoMapSettingsScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Data Source")
                .addText(settings.selectedPoiProvider.name)
                .setOnClickListener {
                    screenManager.push(AutoPoiProviderSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Show Traffic")
                .addText("Google traffic layer")
                .setToggle(
                    Toggle.Builder { checked ->
                        settingsManager.setMapTrafficEnabled(checked)
                        invalidate()
                    }.setChecked(settings.mapTrafficEnabled).build()
                )
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Vehicle & Range")
                .addText("${settings.vehicleType.name}, ${settings.evRangeKm} km")
                .setOnClickListener {
                    screenManager.push(AutoVehicleSettingsScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("General Filters")
                .addText("Energy, Brands, Enseigne, Services")
                .setOnClickListener {
                    screenManager.push(AutoGeneralFiltersScreen(carContext, settingsManager))
                }
                .build()
        )

        if (settings.selectedPoiProvider == PoiProviderType.DataGouvElec ||
            settings.selectedPoiProvider == PoiProviderType.OpenChargeMap ||
            settings.selectedPoiProvider == PoiProviderType.Chargy ||
            settings.selectedPoiProvider == PoiProviderType.Overpass) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Advanced Filters")
                    .addText("EV details or POI types")
                    .setOnClickListener {
                        screenManager.push(AutoAdvancedFiltersScreen(carContext, settingsManager))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Map Settings").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

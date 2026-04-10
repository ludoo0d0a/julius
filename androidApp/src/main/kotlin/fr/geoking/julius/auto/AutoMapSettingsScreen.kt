package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.CarMapMode
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.poi.anyProvidesElectric

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
                .addText(if (settings.selectedPoiProviders.isEmpty()) "None" else settings.selectedPoiProviders.joinToString(", ") { it.name })
                .setOnClickListener {
                    screenManager.push(AutoPoiProviderSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Map Mode")
                .addText("Current: ${settings.carMapMode.name}")
                .setOnClickListener {
                    val nextMode = if (settings.carMapMode == CarMapMode.Native) CarMapMode.Custom else CarMapMode.Native
                    settingsManager.setCarMapMode(nextMode)
                    invalidate()
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Show Traffic")
                .addText("Google & Custom maps")
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


        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Map Settings").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

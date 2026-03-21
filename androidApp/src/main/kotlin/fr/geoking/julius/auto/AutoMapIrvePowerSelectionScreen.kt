package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.ui.MAP_IRVE_POWER_OPTIONS

class AutoMapIrvePowerSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        MAP_IRVE_POWER_OPTIONS.forEach { (kw, label) ->
            val isSelected = settings.mapPowerLevels.contains(kw)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(if (isSelected) "Active" else "Inactive")
                    .setOnClickListener {
                        val newLevels = if (settings.mapPowerLevels.contains(kw)) settings.mapPowerLevels - kw else settings.mapPowerLevels + kw
                        settingsManager.setMapPowerLevels(newLevels)
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Min. Power").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

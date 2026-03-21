package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.ui.MAP_ENERGY_OPTIONS

class AutoMapEnergySelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        MAP_ENERGY_OPTIONS.filter { it.first != "electric" }.take(6).forEach { (id, label) ->
            val isSelected = settings.selectedMapEnergyTypes.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(if (isSelected) "Enabled" else "Disabled")
                    .setToggle(
                        Toggle.Builder { checked ->
                            val current = settingsManager.settings.value.selectedMapEnergyTypes
                            val next = if (checked) current + id else current - id
                            settingsManager.setMapEnergyTypes(next)
                            invalidate()
                        }.setChecked(isSelected).build()
                    )
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Energy Types").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

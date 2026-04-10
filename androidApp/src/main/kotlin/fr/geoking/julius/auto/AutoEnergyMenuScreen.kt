package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager

class AutoEnergyMenuScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val types = settings.selectedMapEnergyTypes
        val hasElectric = types.contains("electric")
        val fuels = types - "electric"
        val hasFuel = fuels.isNotEmpty()

        val isElectricMode = hasElectric && !hasFuel
        val isFuelMode = !hasElectric && hasFuel
        val isHybridMode = hasElectric && hasFuel

        val listBuilder = ItemList.Builder()

        // Fuel Row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Fuel")
                .addText(if (fuels.isEmpty()) "None" else fuels.joinToString(", "))
                .setToggle(
                    Toggle.Builder { checked ->
                        if (checked) {
                            val next = if (fuels.isEmpty()) setOf("sp95") else fuels
                            settingsManager.setMapEnergyTypes(next)
                        }
                        invalidate()
                    }.setChecked(isFuelMode).build()
                )
                .setOnClickListener {
                    screenManager.push(AutoMapEnergySelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        // Electric Row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Electric")
                .addText("Power levels and connectors")
                .setToggle(
                    Toggle.Builder { checked ->
                        if (checked) {
                            settingsManager.setMapEnergyTypes(setOf("electric"))
                        }
                        invalidate()
                    }.setChecked(isElectricMode).build()
                )
                .setOnClickListener {
                    screenManager.push(AutoMapIrvePowerSelectionScreen(carContext, settingsManager))
                }
                .build()
        )

        // Hybrid Row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Hybrid")
                .addText("Fuel + Electric")
                .setToggle(
                    Toggle.Builder { checked ->
                        if (checked) {
                            val nextFuels = if (fuels.isEmpty()) setOf("sp95") else fuels
                            settingsManager.setMapEnergyTypes(nextFuels + "electric")
                        }
                        invalidate()
                    }.setChecked(isHybridMode).build()
                )
                .build()
        )

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Energy")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}

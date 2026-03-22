package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.ui.BrandHelper

class AutoMapIrveOperatorSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        val operators = BrandHelper.getElectricBrands()

        operators.forEach { (id, label) ->
            val isSelected = settings.mapIrveOperators.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(if (isSelected) "Active" else "Inactive")
                    .setOnClickListener {
                        val newOps = if (settings.mapIrveOperators.contains(id)) settings.mapIrveOperators - id else settings.mapIrveOperators + id
                        settingsManager.setMapIrveOperators(newOps)
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Opérateur").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

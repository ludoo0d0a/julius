package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.ui.BrandHelper

class AutoMapBrandSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()
        val brands = BrandHelper.getGasBrands()

        brands.forEach { (id, label) ->
            val isSelected = settings.mapBrands.contains(id)
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText(if (isSelected) "Active" else "Inactive")
                    .setOnClickListener {
                        val current = settingsManager.settings.value.mapBrands
                        val next = if (isSelected) current - id else current + id
                        settingsManager.setMapBrands(next)
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Brands").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.PoiProviderType

class AutoPoiProviderSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val options = listOf(
        PoiProviderType.Routex to "Routex",
        PoiProviderType.Etalab to "Prix carburants (instantané)",
        PoiProviderType.GasApi to "gas-api.ovh",
        PoiProviderType.DataGouv to "data.gouv.fr (fuel, quotidien)",
        PoiProviderType.DataGouvElec to "data.gouv.fr (EV)",
        PoiProviderType.OpenChargeMap to "Open Charge Map",
        PoiProviderType.Chargy to "Chargy (Luxembourg)",
        PoiProviderType.OpenVanCamp to "OpenVan.camp (LU fuel + OSM)",
        PoiProviderType.Overpass to "Overpass",
        PoiProviderType.Hybrid to "Hybrid (Gas + EV)"
    )

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        options.forEach { (type, label) ->
            val isSelected = settings.selectedPoiProviders.contains(type)
            val displayLabel = if (isSelected) "$label (Selected)" else label
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayLabel)
                    .setOnClickListener {
                        settingsManager.togglePoiProviderType(type)
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Data Source").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

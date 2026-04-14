package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.poi.isUserSelectablePoiDataSource
import fr.geoking.julius.poi.getDisplayGroup

class AutoPoiProviderSelectionScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val options = listOf(
        PoiProviderType.Routex to "Routex",
        PoiProviderType.Etalab to "Prix carburant (France official)",
        PoiProviderType.GasApi to "gas-api.ovh",
        PoiProviderType.DataGouv to "data.gouv (France official)",
        PoiProviderType.DataGouvElec to "data.gouv.fr (EV)",
        PoiProviderType.OpenChargeMap to "Open Charge Map",
        PoiProviderType.Chargy to "Chargy (Luxembourg)",
        PoiProviderType.OpenVanCamp to "OpenVan.camp (Europe-wide)",
        PoiProviderType.SpainMinetur to "Spain Minetur (official)",
        PoiProviderType.GermanyTankerkoenig to "Tankerkönig (Germany)",
        PoiProviderType.AustriaEControl to "E-Control (Austria)",
        PoiProviderType.BelgiumOfficial to "Belgium (official)",
        PoiProviderType.PortugalDgeg to "Portugal DGEG (official)",
        PoiProviderType.MadeiraOfficial to "Madeira (official)",
        PoiProviderType.NetherlandsAnwb to "Netherlands/Luxembourg (ANWB)",
        PoiProviderType.SloveniaGoriva to "Slovenia (Goriva.si)",
        PoiProviderType.RomaniaPeco to "Romania (Peco Online)",
        PoiProviderType.Fuelo to "CEE / Turkey (Fuelo.net)",
        PoiProviderType.GreeceFuelGR to "Greece (FuelGR)",
        PoiProviderType.SerbiaNis to "Serbia (NIS)",
        PoiProviderType.CroatiaMzoe to "Croatia (MZOE)",
        PoiProviderType.DrivstoffAppen to "Nordics (DrivstoffAppen)",
        PoiProviderType.DenmarkFuelprices to "Denmark (Fuelprices.dk)",
        PoiProviderType.FinlandPolttoaine to "Finland (Polttoaine.net)",
        PoiProviderType.ArgentinaEnergia to "Argentina (Energia)",
        PoiProviderType.MexicoCRE to "Mexico (CRE)",
        PoiProviderType.MoldovaAnre to "Moldova (ANRE)",
        PoiProviderType.AustraliaFuel to "Australia (FuelWatch/Check)",
        PoiProviderType.IrelandPickAPump to "Ireland (Pick A Pump)",
        PoiProviderType.Overpass to "Overpass",
        PoiProviderType.Hybrid to "Hybrid (Gas + EV)"
    ).filter { (type, _) -> type.isUserSelectablePoiDataSource() }

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value
        val listBuilder = ItemList.Builder()

        val grouped = options.groupBy { (type, _) -> type.getDisplayGroup() }
        grouped.forEach { (group, providers) ->
            providers.forEach { (type, label) ->
                val isSelected = settings.selectedPoiProviders.contains(type)
                val displayLabel = if (isSelected) "$label (Selected)" else label
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(displayLabel)
                        .addText(group)
                        .setOnClickListener {
                            settingsManager.togglePoiProviderType(type)
                            invalidate()
                        }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Data Source").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

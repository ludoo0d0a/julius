package fr.geoking.julius.providers

import android.util.Log
import fr.geoking.julius.SettingsManager

/**
 * Delegates to the currently selected [PoiProvider] (Routex, Etalab, GasApi, DataGouv or DataGouvElec)
 * based on [SettingsManager.settings].selectedPoiProvider.
 */
class SelectorPoiProvider(
    private val routex: PoiProvider,
    private val etalab: PoiProvider,
    private val gasApi: PoiProvider,
    private val dataGouv: PoiProvider,
    private val dataGouvElec: PoiProvider,
    private val openChargeMap: PoiProvider,
    private val settingsManager: SettingsManager
) : PoiProvider {

    private fun currentProvider(): PoiProvider = when (settingsManager.settings.value.selectedPoiProvider) {
        PoiProviderType.Routex -> routex
        PoiProviderType.Etalab -> etalab
        PoiProviderType.GasApi -> gasApi
        PoiProviderType.DataGouv -> dataGouv
        PoiProviderType.DataGouvElec -> dataGouvElec
        PoiProviderType.OpenChargeMap -> openChargeMap
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val settings = settingsManager.settings.value
        val provider = settings.selectedPoiProvider
        var result = currentProvider().getGasStations(latitude, longitude, viewport)
        val selectedEnergies = settings.selectedMapEnergyTypes
        if (selectedEnergies.isNotEmpty()) {
            result = result.filter { MapPoiFilter.matchesEnergyFilter(it, selectedEnergies) }
        }
        if (provider == PoiProviderType.DataGouvElec) {
            if (settings.mapMinPowerKw > 0) {
                val minKw = settings.mapMinPowerKw
                result = result.filter { poi ->
                    poi.powerKw == null || poi.powerKw!! >= minKw
                }
            }
            if (settings.mapIrveOperator != "all") {
                val op = settings.mapIrveOperator.trim().lowercase()
                if (op.isNotEmpty()) {
                    result = result.filter { poi ->
                        poi.operator?.trim()?.lowercase()?.contains(op) == true
                    }
                }
            }
        }
        if (provider == PoiProviderType.DataGouvElec || provider == PoiProviderType.OpenChargeMap) {
            if (settings.selectedMapConnectorTypes.isNotEmpty()) {
                val connectorSet = settings.selectedMapConnectorTypes
                result = result.filter { poi ->
                    poi.irveDetails?.connectorTypes?.any { it in connectorSet } == true
                }
            }
        }
        Log.d("SelectorPoiProvider", "selected=$provider lat=$latitude lon=$longitude -> ${result.size} pois (energy+power+operator+connector filter)")
        return result
    }
}

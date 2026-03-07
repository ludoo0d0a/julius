package fr.geoking.julius.providers

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
    private val settingsManager: SettingsManager
) : PoiProvider {

    private fun currentProvider(): PoiProvider = when (settingsManager.settings.value.selectedPoiProvider) {
        PoiProviderType.Routex -> routex
        PoiProviderType.Etalab -> etalab
        PoiProviderType.GasApi -> gasApi
        PoiProviderType.DataGouv -> dataGouv
        PoiProviderType.DataGouvElec -> dataGouvElec
    }

    override suspend fun getGasStations(latitude: Double, longitude: Double): List<Poi> {
        return currentProvider().getGasStations(latitude, longitude)
    }
}

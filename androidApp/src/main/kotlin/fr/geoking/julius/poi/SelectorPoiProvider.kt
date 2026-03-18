package fr.geoking.julius.poi

import android.util.Log
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.VehicleType

/**
 * Delegates to the currently selected [PoiProvider] (Routex, Etalab, GasApi, DataGouv, DataGouvElec, OpenChargeMap, Overpass)
 * based on [SettingsManager.settings].selectedPoiProvider.
 */
class SelectorPoiProvider(
    private val routex: PoiProvider,
    private val etalab: PoiProvider,
    private val gasApi: PoiProvider,
    private val dataGouv: PoiProvider,
    private val dataGouvElec: PoiProvider,
    private val openChargeMap: PoiProvider,
    private val chargy: PoiProvider,
    private val overpass: PoiProvider,
    private val dataGouvCamping: PoiProvider?,
    private val settingsManager: SettingsManager
) : PoiProvider {

    private fun currentProvider(): PoiProvider = when (settingsManager.settings.value.selectedPoiProvider) {
        PoiProviderType.Routex -> routex
        PoiProviderType.Etalab -> etalab
        PoiProviderType.GasApi -> gasApi
        PoiProviderType.DataGouv -> dataGouv
        PoiProviderType.DataGouvElec -> dataGouvElec
        PoiProviderType.OpenChargeMap -> openChargeMap
        PoiProviderType.Chargy -> chargy
        PoiProviderType.Overpass -> overpass
    }

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val settings = settingsManager.settings.value
        val provider = settings.selectedPoiProvider
        val vehicleType = settings.vehicleType
        val categories = when (provider) {
            PoiProviderType.Overpass -> {
                val fromSettings = settings.selectedOverpassAmenityTypes.mapNotNull { id ->
                    when (id) {
                        "toilets" -> PoiCategory.Toilet
                        "drinking_water" -> PoiCategory.DrinkingWater
                        "camp_site" -> PoiCategory.Camping
                        "caravan_site" -> PoiCategory.CaravanSite
                        "picnic_site" -> PoiCategory.PicnicSite
                        "truck_stop" -> PoiCategory.TruckStop
                        "rest_area" -> PoiCategory.RestArea
                        "restaurant" -> PoiCategory.Restaurant
                        "fast_food" -> PoiCategory.FastFood
                        else -> null
                    }
                }.toSet()
                val defaultOverpass = fromSettings.ifEmpty {
                    setOf(PoiCategory.Toilet, PoiCategory.DrinkingWater)
                }
                when (vehicleType) {
                    VehicleType.Truck -> defaultOverpass + setOf(PoiCategory.TruckStop, PoiCategory.RestArea, PoiCategory.Gas)
                    VehicleType.Motorhome -> defaultOverpass + setOf(PoiCategory.CaravanSite, PoiCategory.Camping, PoiCategory.PicnicSite)
                    else -> defaultOverpass
                }
            }
            else -> {
                when (vehicleType) {
                    VehicleType.Truck -> setOf(PoiCategory.Gas, PoiCategory.TruckStop, PoiCategory.RestArea)
                    else -> setOf(PoiCategory.Gas, PoiCategory.Irve)
                }
            }
        }
        val effectiveRequest = request.copy(categories = categories)
        var result = currentProvider().search(effectiveRequest)
        if (provider == PoiProviderType.Overpass && PoiCategory.CaravanSite in categories && dataGouvCamping != null) {
            val extra = dataGouvCamping.search(effectiveRequest)
            val seenIds = result.mapTo(mutableSetOf()) { it.id }
            result = result + extra.filter { it.id !in seenIds }
        }
        if (PoiCategory.Irve in categories &&
            request.latitude in 49.4..50.2 && request.longitude in 5.7..6.6 &&
            provider != PoiProviderType.Chargy) {
            val extra = chargy.search(effectiveRequest)
            val seenIds = result.mapTo(mutableSetOf()) { it.id }
            result = result + extra.filter { it.id !in seenIds }
        }

        if (provider != PoiProviderType.Overpass) {
            val selectedEnergies = settings.selectedMapEnergyTypes
            if (selectedEnergies.isNotEmpty()) {
                result = result.filter { MapPoiFilter.matchesEnergyFilter(it, selectedEnergies) }
            }
            if (provider == PoiProviderType.DataGouvElec) {
                if (settings.mapMinPowerKw > 0) {
                    val minKw = settings.mapMinPowerKw
                    result = result.filter { poi -> poi.powerKw == null || poi.powerKw!! >= minKw }
                }
                if (settings.mapIrveOperator != "all") {
                    val op = settings.mapIrveOperator.trim().lowercase()
                    if (op.isNotEmpty()) {
                        result = result.filter { poi -> poi.operator?.trim()?.lowercase()?.contains(op) == true }
                    }
                }
            }
            if (provider == PoiProviderType.DataGouvElec || provider == PoiProviderType.OpenChargeMap || provider == PoiProviderType.Chargy) {
                if (settings.selectedMapConnectorTypes.isNotEmpty()) {
                    val connectorSet = settings.selectedMapConnectorTypes
                    result = result.filter { poi -> poi.irveDetails?.connectorTypes?.any { it in connectorSet } == true }
                }
            }
        }
        Log.d("SelectorPoiProvider", "search provider=$provider categories=$categories -> ${result.size} pois")
        return result
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val settings = settingsManager.settings.value
        val provider = settings.selectedPoiProvider
        var result = currentProvider().getGasStations(latitude, longitude, viewport)

        if (latitude in 49.4..50.2 && longitude in 5.7..6.6 && provider != PoiProviderType.Chargy) {
            val extra = chargy.getGasStations(latitude, longitude, viewport)
            val seenIds = result.mapTo(mutableSetOf()) { it.id }
            result = result + extra.filter { it.id !in seenIds }
        }

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
        if (provider == PoiProviderType.DataGouvElec || provider == PoiProviderType.OpenChargeMap || provider == PoiProviderType.Chargy) {
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

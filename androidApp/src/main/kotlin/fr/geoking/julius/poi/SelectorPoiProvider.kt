package fr.geoking.julius.poi

import android.util.Log
import fr.geoking.julius.StationMapFilters
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.VehicleType
import fr.geoking.julius.api.openvan.OpenVanCampClient
import fr.geoking.julius.api.openvan.OpenVanCampProvider
import fr.geoking.julius.parking.ParkingRegion

/**
 * Delegates to the currently selected [PoiProvider] (Routex, DataGouv prix carburant instantané, GasApi, …)
 * based on [SettingsManager.settings].selectedPoiProvider.
 */
class SelectorPoiProvider(
    private val routex: PoiProvider,
    private val dataGouvPrixCarburant: PoiProvider,
    private val gasApi: PoiProvider,
    private val dataGouv: PoiProvider,
    private val dataGouvElec: PoiProvider,
    private val openChargeMap: PoiProvider,
    private val chargy: PoiProvider,
    private val openVanCamp: PoiProvider,
    private val openVanCampClient: OpenVanCampClient,
    private val overpass: PoiProvider,
    private val dataGouvCamping: PoiProvider?,
    private val settingsManager: SettingsManager
) : PoiProvider {

    private val hybridProvider by lazy { HybridPoiProvider(dataGouv, dataGouvElec) }

    private fun getProvider(type: PoiProviderType): PoiProvider = when (type) {
        PoiProviderType.Routex -> routex
        PoiProviderType.Etalab -> dataGouvPrixCarburant
        PoiProviderType.GasApi -> gasApi
        PoiProviderType.DataGouv -> dataGouv
        PoiProviderType.DataGouvElec -> dataGouvElec
        PoiProviderType.OpenChargeMap -> openChargeMap
        PoiProviderType.Chargy -> chargy
        PoiProviderType.OpenVanCamp -> openVanCamp
        PoiProviderType.Overpass -> overpass
        PoiProviderType.Hybrid -> hybridProvider
    }

    private class HybridPoiProvider(
        private val gasProvider: PoiProvider,
        private val elecProvider: PoiProvider
    ) : PoiProvider {
        override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas, PoiCategory.Irve)
        override suspend fun search(request: PoiSearchRequest): List<Poi> {
            val gasResult = gasProvider.search(request.copy(categories = setOf(PoiCategory.Gas)))
            val elecResult = elecProvider.search(request.copy(categories = setOf(PoiCategory.Irve)))
            return gasResult + elecResult
        }
        override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
            val gasResult = gasProvider.getGasStations(latitude, longitude, viewport)
            val elecResult = elecProvider.getGasStations(latitude, longitude, viewport)
            return gasResult + elecResult
        }
    }

    override suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
        val settings = settingsManager.settings.value
        val providers = if (settings.useVehicleFilter && settings.fuelCard == fr.geoking.julius.FuelCard.Routex && (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid")) {
            setOf(PoiProviderType.Routex)
        } else {
            settings.selectedPoiProviders
        }

        if (providers.isEmpty()) return PoiSearchResult()

        val vehicleType = settings.vehicleType
        val categories = providers.flatMap { provider ->
            when (provider) {
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
                        "speed_camera" -> PoiCategory.Radar
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
        }.toSet()

        val effectiveRequest = request.copy(categories = categories)

        val allPois = mutableListOf<Poi>()
        val errors = mutableListOf<PoiProviderError>()

        providers.forEach { providerType ->
            val activeProvider = getProvider(providerType)
            val searchResult = activeProvider.searchResult(effectiveRequest)
            allPois.addAll(searchResult.pois)
            errors.addAll(searchResult.errors)

            if (providerType == PoiProviderType.Overpass && PoiCategory.CaravanSite in categories && dataGouvCamping != null) {
                try {
                    val extra = dataGouvCamping.search(effectiveRequest)
                    allPois.addAll(extra)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    errors.add(PoiProviderError("DataGouv Camping", e.message ?: "Unknown error"))
                }
            }
        }

        if (PoiCategory.Irve in categories &&
            request.latitude in 49.4..50.2 && request.longitude in 5.7..6.6 &&
            PoiProviderType.Chargy !in providers) {
            try {
                val extra = chargy.search(effectiveRequest)
                allPois.addAll(extra)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errors.add(PoiProviderError("Chargy", e.message ?: "Unknown error"))
            }
        }

        var result = mergePois(allPois)
        result = enrichLuxembourgOpenVanReferencePrices(
            pois = result,
            providers = providers,
            centerLat = request.latitude,
            centerLon = request.longitude
        )
        result = StationMapFilters.apply(
            settings = settings,
            pois = result,
            providers = providers,
            skipWhenOnlyOverpass = true,
        )
        Log.d("SelectorPoiProvider", "search providers=$providers categories=$categories -> ${result.size} pois")
        return PoiSearchResult(pois = result, errors = errors)
    }

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        return searchResult(request).pois
    }

    private fun mergePois(pois: List<Poi>): List<Poi> {
        val merged = mutableListOf<Poi>()
        val sorted = pois.sortedBy { it.id }

        for (poi in sorted) {
            val existing = merged.find {
                it.id == poi.id || (
                    Math.abs(it.latitude - poi.latitude) < 0.0005 &&
                        Math.abs(it.longitude - poi.longitude) < 0.0005 &&
                        it.name.lowercase().take(5) == poi.name.lowercase().take(5)
                    )
            }

            if (existing != null) {
                val index = merged.indexOf(existing)
                merged[index] = existing.copy(
                    fuelPrices = (existing.fuelPrices.orEmpty() + poi.fuelPrices.orEmpty())
                        .distinctBy { it.fuelName },
                    irveDetails = run {
                        val e = existing.irveDetails
                        val p = poi.irveDetails
                        if (e != null && p != null) {
                            e.copy(
                                connectorTypes = e.connectorTypes + p.connectorTypes
                            )
                        } else e ?: p
                    },
                    source = if (existing.source != null && poi.source != null && existing.source != poi.source) {
                        "${existing.source} + ${poi.source}"
                    } else existing.source ?: poi.source,
                    brand = existing.brand ?: poi.brand,
                    powerKw = existing.powerKw ?: poi.powerKw,
                    operator = existing.operator ?: poi.operator,
                    chargePointCount = (existing.chargePointCount ?: 0).coerceAtLeast(poi.chargePointCount ?: 0)
                )
            } else {
                merged.add(poi)
            }
        }
        return merged
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val settings = settingsManager.settings.value
        val providers = if (settings.useVehicleFilter && settings.fuelCard == fr.geoking.julius.FuelCard.Routex && (settings.vehicleEnergy == "gas" || settings.vehicleEnergy == "hybrid")) {
            setOf(PoiProviderType.Routex)
        } else {
            settings.selectedPoiProviders
        }

        if (providers.isEmpty()) return emptyList()

        val allPois = mutableListOf<Poi>()
        providers.forEach { providerType ->
            val activeProvider = getProvider(providerType)
            allPois.addAll(activeProvider.getGasStations(latitude, longitude, viewport))
        }

        if (latitude in 49.4..50.2 && longitude in 5.7..6.6 && PoiProviderType.Chargy !in providers) {
            val extra = chargy.getGasStations(latitude, longitude, viewport)
            allPois.addAll(extra)
        }

        var result = mergePois(allPois)
        result = enrichLuxembourgOpenVanReferencePrices(
            pois = result,
            providers = providers,
            centerLat = latitude,
            centerLon = longitude
        )
        result = StationMapFilters.apply(
            settings = settings,
            pois = result,
            providers = providers,
            skipWhenOnlyOverpass = false,
        )
        Log.d("SelectorPoiProvider", "selected=$providers lat=$latitude lon=$longitude -> ${result.size} pois (energy+power+operator+connector filter)")
        return result
    }

    /**
     * When OpenVan.camp is enabled, attach Luxembourg weekly reference prices to gas POIs inside the
     * country that do not already have per-station prices (e.g. Routex locations).
     */
    private suspend fun enrichLuxembourgOpenVanReferencePrices(
        pois: List<Poi>,
        providers: Set<PoiProviderType>,
        centerLat: Double,
        centerLon: Double
    ): List<Poi> {
        if (PoiProviderType.OpenVanCamp !in providers) return pois
        if (!OpenVanCampProvider.searchCenterMayIncludeLuxembourg(centerLat, centerLon)) return pois
        val prices = try {
            openVanCampClient.getLuxembourgFuelPrices()?.takeIf { it.isNotEmpty() } ?: return pois
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return pois
        }
        return pois.map { p ->
            if (p.isElectric) return@map p
            val cat = p.poiCategory ?: PoiCategory.Gas
            if (cat != PoiCategory.Gas) return@map p
            if (!ParkingRegion.Luxembourg.contains(p.latitude, p.longitude)) return@map p
            if (!p.fuelPrices.isNullOrEmpty()) return@map p
            p.copy(
                fuelPrices = prices,
                source = when (val s = p.source) {
                    null -> "OpenVan.camp (LU weekly avg.)"
                    else -> "$s + OpenVan.camp (LU weekly avg.)"
                }
            )
        }
    }
}

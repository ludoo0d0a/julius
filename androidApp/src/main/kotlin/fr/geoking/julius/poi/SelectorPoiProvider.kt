package fr.geoking.julius.poi

import android.util.Log
import fr.geoking.julius.StationMapFilters
import fr.geoking.julius.effectiveProviders
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.VehicleType
import fr.geoking.julius.parking.ParkingRegion
import fr.geoking.julius.api.openvan.OpenVanCampClient
import fr.geoking.julius.api.openvan.OpenVanCampProvider
import fr.geoking.julius.poi.PoiMerger
import fr.geoking.julius.shared.location.haversineKm
import fr.geoking.julius.shared.location.approxDistanceKm
import fr.geoking.julius.api.routex.radiusKmFromMapViewport
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.FlowCollector

/**
 * Delegates to the currently selected [PoiProvider] (Routex, DataGouv fuel, …). Etalab / GasApi stay wired for tests;
 * user selection is sanitized to DataGouv in [SettingsManager].
 * based on [SettingsManager.settings].selectedPoiProvider.
 */
class SelectorPoiProvider(
    private val routex: PoiProvider,
    private val dataGouvPrixCarburant: PoiProvider,
    private val gasApi: PoiProvider,
    private val dataGouv: PoiProvider,
    private val dataGouvElec: PoiProvider,
    private val openChargeMap: PoiProvider,
    private val ecoMovement: PoiProvider,
    private val chargy: PoiProvider,
    private val openVanCamp: PoiProvider,
    private val spainMinetur: PoiProvider,
    private val germanyTankerkoenig: PoiProvider,
    private val austriaEControl: PoiProvider,
    private val belgiumOfficial: PoiProvider,
    private val portugalDgeg: PoiProvider,
    private val ionity: PoiProvider,
    private val fastned: PoiProvider,
    private val unitedKingdomCma: PoiProvider,
    private val italyMimit: PoiProvider,
    private val netherlandsAnwb: PoiProvider,
    private val sloveniaGoriva: PoiProvider,
    private val romaniaPeco: PoiProvider,
    private val fuelo: PoiProvider,
    private val greeceFuelGR: PoiProvider,
    private val serbiaNis: PoiProvider,
    private val croatiaMzoe: PoiProvider,
    private val drivstoffAppen: PoiProvider,
    private val denmarkFuelprices: PoiProvider,
    private val finlandPolttoaine: PoiProvider,
    private val argentinaEnergia: PoiProvider,
    private val mexicoCRE: PoiProvider,
    private val moldovaAnre: PoiProvider,
    private val australiaFuelWatch: PoiProvider,
    private val australiaFuelCheck: PoiProvider,
    private val irelandPickAPump: PoiProvider,
    private val openVanCampClient: OpenVanCampClient,
    private val overpass: PoiProvider,
    private val dataGouvCamping: PoiProvider?,
    private val settingsManager: SettingsManager
) : PoiProvider {

    private data class LoadedPoiRegion(
        val centerLat: Double,
        val centerLng: Double,
        val maxRadiusKmLoaded: Int,
        val loadedAtMs: Long
    )

    private val hybridProvider by lazy { HybridPoiProvider(dataGouv, dataGouvElec) }

    private var cachedPois = listOf<Poi>()
    private val poiSeenAtMs = mutableMapOf<String, Long>()
    private val loadedRegions = mutableListOf<LoadedPoiRegion>()
    private var lastCacheKey: String? = null

    // Core lock for all cache-related state
    private val lock = Any()

    // Track in-flight provider searches to avoid redundant network calls
    private val providerJobsMutex = Mutex()
    private val inFlightProviderJobs = mutableMapOf<String, kotlinx.coroutines.Deferred<PoiSearchResult>>()

    private fun getProvider(type: PoiProviderType): PoiProvider = when (type) {
        PoiProviderType.Routex -> routex
        PoiProviderType.Etalab -> dataGouvPrixCarburant
        PoiProviderType.GasApi -> gasApi
        PoiProviderType.DataGouv -> dataGouv
        PoiProviderType.DataGouvElec -> dataGouvElec
        PoiProviderType.OpenChargeMap -> openChargeMap
        PoiProviderType.EcoMovement -> ecoMovement
        PoiProviderType.Chargy -> chargy
        PoiProviderType.OpenVanCamp -> openVanCamp
        PoiProviderType.SpainMinetur -> spainMinetur
        PoiProviderType.GermanyTankerkoenig -> germanyTankerkoenig
        PoiProviderType.AustriaEControl -> austriaEControl
        PoiProviderType.BelgiumOfficial -> belgiumOfficial
        PoiProviderType.PortugalDgeg -> portugalDgeg
        PoiProviderType.Ionity -> ionity
        PoiProviderType.Fastned -> fastned
        PoiProviderType.UnitedKingdomCma -> unitedKingdomCma
        PoiProviderType.ItalyMimit -> italyMimit
        PoiProviderType.NetherlandsAnwb -> netherlandsAnwb
        PoiProviderType.SloveniaGoriva -> sloveniaGoriva
        PoiProviderType.RomaniaPeco -> romaniaPeco
        PoiProviderType.Fuelo -> fuelo
        PoiProviderType.GreeceFuelGR -> greeceFuelGR
        PoiProviderType.SerbiaNis -> serbiaNis
        PoiProviderType.CroatiaMzoe -> croatiaMzoe
        PoiProviderType.DrivstoffAppen -> drivstoffAppen
        PoiProviderType.DenmarkFuelprices -> denmarkFuelprices
        PoiProviderType.FinlandPolttoaine -> finlandPolttoaine
        PoiProviderType.ArgentinaEnergia -> argentinaEnergia
        PoiProviderType.MexicoCRE -> mexicoCRE
        PoiProviderType.MoldovaAnre -> moldovaAnre
        PoiProviderType.AustraliaFuel -> HybridAustraliaProvider(australiaFuelWatch, australiaFuelCheck)
        PoiProviderType.IrelandPickAPump -> irelandPickAPump
        PoiProviderType.Overpass -> overpass
        PoiProviderType.Hybrid -> hybridProvider
    }

    private class HybridPoiProvider(
        private val gasProvider: PoiProvider,
        private val elecProvider: PoiProvider
    ) : PoiProvider {
        override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas, PoiCategory.Irve)
        override suspend fun search(request: PoiSearchRequest): List<Poi> {
            val gasResult = gasProvider.search(request.copy(categories = setOf(PoiCategory.Gas), skipFilters = request.skipFilters))
            val elecResult = elecProvider.search(request.copy(categories = setOf(PoiCategory.Irve), skipFilters = request.skipFilters))
            return gasResult + elecResult
        }
        override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
            val gasResult = gasProvider.getGasStations(latitude, longitude, viewport)
            val elecResult = elecProvider.getGasStations(latitude, longitude, viewport)
            return gasResult + elecResult
        }
    }

    override fun searchFlow(request: PoiSearchRequest): Flow<PoiSearchResult> = channelFlow {
        performSearch(request, collector = object : FlowCollector<PoiSearchResult> {
            override suspend fun emit(value: PoiSearchResult) {
                send(value)
            }
        })
    }

    override suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
        val results = mutableListOf<PoiSearchResult>()
        performSearch(request, collector = object : FlowCollector<PoiSearchResult> {
            override suspend fun emit(value: PoiSearchResult) {
                synchronized(results) { results.add(value) }
            }
        })
        return results.lastOrNull() ?: PoiSearchResult()
    }

    private suspend fun performSearch(
        request: PoiSearchRequest,
        collector: FlowCollector<PoiSearchResult>
    ) {
        val settings = settingsManager.settings.value
        val providers = try {
            settings.effectiveProviders(
                latitude = request.latitude,
                longitude = request.longitude,
                zoom = request.viewport?.zoom
            )
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to resolve providers from settings", e)
            settings.selectedPoiProviders
        }

        // Use selected providers for cache key so zooming out doesn't clear the cache.
        val cacheProviders = settings.selectedPoiProviders
        val poiFetchKey = buildString {
            append(cacheProviders.sortedBy { it.name }.joinToString(",") { it.name })
            append("|vehicleFilter=").append(settings.useVehicleFilter)
            append("|fuelCard=").append(settings.fuelCard)
            append("|vehicleType=").append(settings.vehicleType)
            append("|vehicleEnergy=").append(settings.vehicleEnergy)
            append("|overpassAmenities=").append(settings.selectedOverpassAmenityTypes.sorted().joinToString(","))
        }

        val nowMs = System.currentTimeMillis()
        val ttlMs = 5L * 60L * 1000L
        val expiresBeforeMs = nowMs - ttlMs
        val requiredRadiusKm = request.viewport?.let { v ->
            radiusKmFromMapViewport(
                request.latitude,
                request.longitude,
                v.zoom,
                v.mapWidthPx,
                v.mapHeightPx
            ).coerceIn(1, 50)
        } ?: 10

        // 1. Immediate emission from cache
        val (alreadyCovered, initialPois) = synchronized(lock) {
            if (lastCacheKey != poiFetchKey) {
                Log.d("SelectorPoiProvider", "Cache key changed from $lastCacheKey to $poiFetchKey. Clearing cache.")
                loadedRegions.clear()
                cachedPois = emptyList()
                poiSeenAtMs.clear()
                lastCacheKey = poiFetchKey
            }

            // TTL eviction
            loadedRegions.removeAll { it.loadedAtMs < expiresBeforeMs }
            if (poiSeenAtMs.isNotEmpty()) {
                val expiredPoiIds = poiSeenAtMs
                    .filter { (_, seenAt) -> seenAt < expiresBeforeMs }
                    .keys
                    .toSet()
                if (expiredPoiIds.isNotEmpty()) {
                    poiSeenAtMs.keys.removeAll(expiredPoiIds)
                    cachedPois = cachedPois.filterNot { it.id in expiredPoiIds }
                }
            }

            val filtered = applyPostFilters(cachedPois, request, providers)
            if (filtered.isNotEmpty()) {
                Log.d("SelectorPoiProvider", "Found ${filtered.size} matching POIs in cache for ${request.latitude},${request.longitude}")
            }

            val viewportCovered = loadedRegions.any { region ->
                region.maxRadiusKmLoaded >= requiredRadiusKm &&
                        haversineKm(
                            request.latitude,
                            request.longitude,
                            region.centerLat,
                            region.centerLng
                        ) <= (region.maxRadiusKmLoaded - requiredRadiusKm).toDouble() + 0.5
            }

            viewportCovered to filtered
        }

        if (initialPois.isNotEmpty()) {
            collector.emit(PoiSearchResult(pois = initialPois))
        }

        if (alreadyCovered || providers.isEmpty()) {
            if (alreadyCovered) Log.d("SelectorPoiProvider", "Location ${request.latitude},${request.longitude} already covered by cache.")
            return
        }

        // 2. Incremental fetch with provider-level deduplication
        val allPois = mutableListOf<Poi>()
        val errors = mutableSetOf<PoiProviderError>()

        val vehicleType = settings.vehicleType
        val categories = try {
            providers.map { providerType ->
                when (providerType) {
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
            }.flatten().toSet()
        } catch (e: Exception) {
            Log.e("SelectorPoiProvider", "Failed to map categories", e)
            setOf(PoiCategory.Gas, PoiCategory.Irve)
        }

        val effectiveRequest = request.copy(categories = categories, skipFilters = true)

        coroutineScope {
            providers.forEach { providerType ->
                launch {
                    val providerKey = "$poiFetchKey|$providerType|${request.latitude}|${request.longitude}|$requiredRadiusKm"

                    val searchResult = providerJobsMutex.withLock {
                        inFlightProviderJobs[providerKey]?.let { return@withLock it }

                        // Create a new deferred for this provider request
                        val deferred = this.async {
                            val activeProvider = getProvider(providerType)
                            try {
                                val res = activeProvider.searchResult(effectiveRequest)
                                if (providerType == PoiProviderType.Overpass && PoiCategory.CaravanSite in categories && dataGouvCamping != null) {
                                    try {
                                        val extra = dataGouvCamping.search(effectiveRequest)
                                        res.copy(pois = res.pois + extra)
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        res.copy(errors = res.errors + PoiProviderError("DataGouv Camping", e.message ?: "Unknown error"))
                                    }
                                } else {
                                    res
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                PoiSearchResult(errors = listOf(PoiProviderError(providerType.name, e.message ?: "Unknown error")))
                            }
                        }
                        inFlightProviderJobs[providerKey] = deferred
                        deferred
                    }.await()

                    // Cleanup job after await
                    providerJobsMutex.withLock {
                        inFlightProviderJobs.remove(providerKey)
                    }

                    val enriched = enrichNationalReferencePrices(
                        pois = searchResult.pois,
                        providers = providers,
                        centerLat = request.latitude,
                        centerLon = request.longitude
                    )

                    // Merge into global cache incrementally
                    val resultToEmit = synchronized(lock) {
                        allPois.addAll(enriched)
                        errors.addAll(searchResult.errors)

                        cachedPois = PoiMerger.mergeInto(cachedPois, enriched)
                        val mergedNow = System.currentTimeMillis()
                        enriched.forEach { poiSeenAtMs[it.id] = mergedNow }
                        cachedPois.forEach { p ->
                            if (poiSeenAtMs[p.id] == null) poiSeenAtMs[p.id] = mergedNow
                        }

                        val filteredNow = applyPostFilters(cachedPois, request, providers)
                        PoiSearchResult(pois = filteredNow, errors = errors.toList())
                    }
                    collector.emit(resultToEmit)
                }
            }
        }

        // After all finish, finalize cache regions and bounds.
        synchronized(lock) {
            val finalNow = System.currentTimeMillis()
            loadedRegions.add(
                LoadedPoiRegion(
                    centerLat = request.latitude,
                    centerLng = request.longitude,
                    maxRadiusKmLoaded = requiredRadiusKm,
                    loadedAtMs = finalNow
                )
            )

            // Keep the region cache bounded.
            val maxRegions = 8
            while (loadedRegions.size > maxRegions) {
                val farthest = loadedRegions.maxBy { r ->
                    haversineKm(r.centerLat, r.centerLng, request.latitude, request.longitude)
                }
                loadedRegions.remove(farthest)
            }

            // Keep the POI cache bounded: keep closest POIs to current center.
            val maxPoisInCache = 1200
            if (cachedPois.size > maxPoisInCache) {
                cachedPois = cachedPois
                    .asSequence()
                    .map { p -> p to approxDistanceKm(request.latitude, request.longitude, p.latitude, p.longitude) }
                    .sortedBy { it.second }
                    .take(maxPoisInCache)
                    .map { it.first }
                    .toList()
                val keepIds = cachedPois.map { it.id }.toSet()
                poiSeenAtMs.keys.retainAll(keepIds)
            }

            Log.d("SelectorPoiProvider", "Search completed for ${request.latitude},${request.longitude} radius=$requiredRadiusKm. Cache size: ${cachedPois.size}")
        }
    }

    private fun applyPostFilters(pois: List<Poi>, request: PoiSearchRequest, providers: Set<PoiProviderType>): List<Poi> {
        val centerLat = request.latitude
        val centerLng = request.longitude
        val displayRadiusKm = request.viewport?.let { v ->
            radiusKmFromMapViewport(
                centerLat,
                centerLng,
                v.zoom,
                v.mapWidthPx,
                v.mapHeightPx
            ).coerceIn(1, 50)
        } ?: 10

        val raw = pois.filter { poi ->
            approxDistanceKm(centerLat, centerLng, poi.latitude, poi.longitude) <= displayRadiusKm * 1.05
        }

        return if (!request.skipFilters) {
            StationMapFilters.apply(
                settings = settingsManager.settings.value,
                pois = raw,
                providers = providers,
                skipWhenOnlyOverpass = true,
                limit = 200,
                centerLat = centerLat,
                centerLng = centerLng
            )
        } else {
            raw
        }
    }

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        return searchResult(request).pois
    }

    override fun clearCache() {
        lastCacheKey = null
        synchronized(lock) {
            cachedPois = emptyList()
            poiSeenAtMs.clear()
            loadedRegions.clear()
        }

        routex.clearCache()
        dataGouvPrixCarburant.clearCache()
        gasApi.clearCache()
        dataGouv.clearCache()
        dataGouvElec.clearCache()
        openChargeMap.clearCache()
        ecoMovement.clearCache()
        chargy.clearCache()
        openVanCamp.clearCache()
        spainMinetur.clearCache()
        germanyTankerkoenig.clearCache()
        austriaEControl.clearCache()
        belgiumOfficial.clearCache()
        portugalDgeg.clearCache()
        ionity.clearCache()
        fastned.clearCache()
        unitedKingdomCma.clearCache()
        italyMimit.clearCache()
        netherlandsAnwb.clearCache()
        sloveniaGoriva.clearCache()
        romaniaPeco.clearCache()
        fuelo.clearCache()
        greeceFuelGR.clearCache()
        serbiaNis.clearCache()
        croatiaMzoe.clearCache()
        drivstoffAppen.clearCache()
        denmarkFuelprices.clearCache()
        finlandPolttoaine.clearCache()
        argentinaEnergia.clearCache()
        mexicoCRE.clearCache()
        moldovaAnre.clearCache()
        australiaFuelWatch.clearCache()
        australiaFuelCheck.clearCache()
        irelandPickAPump.clearCache()
        overpass.clearCache()
    }

    private class HybridAustraliaProvider(
        private val watch: PoiProvider,
        private val check: PoiProvider
    ) : PoiProvider {
        override suspend fun getGasStations(latitude: Double, longitude: Double, viewport: MapViewport?): List<Poi> {
            return watch.getGasStations(latitude, longitude, viewport) +
                   check.getGasStations(latitude, longitude, viewport)
        }
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        return searchResult(PoiSearchRequest(latitude, longitude, viewport)).pois
    }

    /**
     * When OpenVan.camp is enabled, attach reference fuel prices (weekly averages) to gas POIs
     * in countries known for having reference prices (e.g. Luxembourg, Portugal, Italy, etc.).
     */
    private suspend fun enrichNationalReferencePrices(
        pois: List<Poi>,
        providers: Set<PoiProviderType>,
        centerLat: Double,
        centerLon: Double
    ): List<Poi> {
        if (PoiProviderType.OpenVanCamp !in providers) return pois

        // Determine target country from search center
        val region = ParkingRegion.containing(centerLat, centerLon) ?: return pois
        val iso = region.countryCode

        if (!FuelPriceRegistry.hasReferencePrice(iso)) return pois

        val prices = try {
            openVanCampClient.getReferenceFuelPrices(iso)?.takeIf { it.isNotEmpty() } ?: return pois
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return pois
        }

        return pois.map { p ->
            if (p.isElectric) return@map p
            val cat = p.poiCategory ?: PoiCategory.Gas
            if (cat != PoiCategory.Gas) return@map p
            if (!region.contains(p.latitude, p.longitude)) return@map p
            if (!p.fuelPrices.isNullOrEmpty()) return@map p

            p.copy(
                fuelPrices = prices,
                source = when (val s = p.source) {
                    null -> "OpenVan.camp ($iso official price)"
                    else -> "$s + OpenVan.camp ($iso official price)"
                }
            )
        }
    }
}

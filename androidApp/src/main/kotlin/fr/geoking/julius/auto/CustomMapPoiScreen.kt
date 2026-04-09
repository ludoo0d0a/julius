package fr.geoking.julius.auto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.AppManager
import androidx.lifecycle.DefaultLifecycleObserver
import fr.geoking.julius.poi.PoiProviderType
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.AppSettings
import fr.geoking.julius.FuelCard
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.StationMapFilters
import fr.geoking.julius.VehicleType
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiSearchRequest
import fr.geoking.julius.poi.PoiProviderError
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.belib.StationAvailabilitySummary
import fr.geoking.julius.api.geocoding.GeocodingClient
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.effectiveIrvePowerLevels
import fr.geoking.julius.effectiveMapEnergyFilterIds
import fr.geoking.julius.effectiveProviders
import fr.geoking.julius.feature.location.LocationHelper
import fr.geoking.julius.toll.TollCalculator
import kotlinx.coroutines.flow.collectLatest
import fr.geoking.julius.api.belib.matchAvailabilityToPois
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class CustomMapPoiScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider,
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    private val settingsManager: SettingsManager,
    private val routePlanner: RoutePlanner? = null,
    private val routingClient: RoutingClient? = null,
    private val tollCalculator: TollCalculator? = null,
    private val trafficProviderFactory: TrafficProviderFactory? = null,
    private val geocodingClient: GeocodingClient? = null,
    private val communityRepo: CommunityPoiRepository? = null,
    private val favoritesRepo: FavoritesRepository? = null
) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private var pois: List<Poi> = emptyList()
    private var errors: List<PoiProviderError> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var favoriteIds: Set<String> = emptySet()
    private var isLoading = true
    private var searchLat: Double = 48.8566
    private var searchLon: Double = 2.3522

    private var surfaceRenderer: AutoSurfaceRenderer? = null

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    PoiFetchSettings(
                        s.effectiveProviders(),
                        s.useVehicleFilter,
                        s.fuelCard,
                        s.vehicleType,
                        s.vehicleEnergy,
                        s.selectedOverpassAmenityTypes
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    loadPois()
                }
        }
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    PoiFilterSettings(
                        s.selectedMapEnergyTypes,
                        s.mapPowerLevels,
                        s.mapIrveOperators,
                        s.mapBrands,
                        s.selectedMapConnectorTypes,
                        s.vehicleGasTypes,
                        s.vehiclePowerLevels
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    invalidate()
                }
        }
    }

    private data class PoiFetchSettings(
        val providers: Set<PoiProviderType>,
        val useVehicleFilter: Boolean,
        val fuelCard: FuelCard,
        val vehicleType: VehicleType,
        val vehicleEnergy: String,
        val amenities: Set<String>
    )

    private data class PoiFilterSettings(
        val energies: Set<String>,
        val powerLevels: Set<Int>,
        val operators: Set<String>,
        val brands: Set<String>,
        val connectors: Set<String>,
        val vehicleGasTypes: Set<String>,
        val vehiclePowerLevels: Set<Int>
    )

    private fun getFilteredPois(currentSettings: AppSettings): List<Poi> {
        val effectiveProviders = currentSettings.effectiveProviders()
        return StationMapFilters.apply(
            settings = currentSettings,
            pois = pois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )
    }

    private fun loadPois() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()

            var lat = 48.8566
            var lon = 2.3522

            if (carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                carContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val location = LocationHelper.getCurrentLocation(carContext)
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                }
            }

            searchLat = lat
            searchLon = lon
            Log.d("CustomMapPoiScreen", "loadPois search center lat=$lat lon=$lon")

            surfaceRenderer?.updateUserLocation(searchLat, searchLon)

            try {
                val settings = settingsManager.settings.value
                val result = poiProvider.searchResult(PoiSearchRequest(lat, lon, null, emptySet(), skipFilters = true))
                pois = result.pois
                errors = result.errors

                val filteredPois = getFilteredPois(settings)
                surfaceRenderer?.let { renderer ->
                    renderer.updateLocation(searchLat, searchLon)
                    renderer.updateUserLocation(searchLat, searchLon)
                    renderer.updatePois(
                        newPois = filteredPois,
                        effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                        effectivePowerLevels = settings.effectiveIrvePowerLevels()
                    )
                }

                Log.d("CustomMapPoiScreen", "pois loaded: ${pois.size}, errors: ${errors.size}")
                favoriteIds = favoritesRepo?.getFavorites()?.map { it.id }?.toSet() ?: emptySet()
                val provider = availabilityProviderFactory.getProvider(lat, lon)
                if (provider != null) {
                    try {
                        val availabilities = provider.getAvailability(lat, lon, 10)
                        availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        availabilityByPoiId = emptyMap()
                    }
                } else {
                    availabilityByPoiId = emptyMap()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("CustomMapPoiScreen", "getGasStations failed", e)
                pois = emptyList()
                errors = listOf(PoiProviderError("System", e.message ?: "Unknown error", isCritical = true))
                availabilityByPoiId = emptyMap()
            }
            isLoading = false
            invalidate()
        }
    }

    /**
     * MapWithContentTemplate with a surface renderer allows at most one action on the top [ActionStrip].
     * Extra actions (e.g. API errors) belong on the nested template [Header].
     */
    private fun pushApiErrorsDetailScreen() {
        val errorMsg = errors.joinToString("\n") { "${it.providerName}: ${it.message}" }
        screenManager.push(
            object : Screen(carContext) {
                override fun onGetTemplate(): Template {
                    return MessageTemplate.Builder(errorMsg)
                        .setHeader(
                            Header.Builder()
                                .setTitle("API Errors")
                                .setStartHeaderAction(Action.BACK)
                                .build()
                        )
                        .addAction(
                            Action.Builder()
                                .setTitle("Retry")
                                .setOnClickListener {
                                    screenManager.pop()
                                    loadPois()
                                }
                                .build()
                        )
                        .build()
                }
            }
        )
    }

    private fun mapContentHeaderBuilder(title: String): Header.Builder {
        val builder = Header.Builder()
            .setTitle(title)
            .setStartHeaderAction(Action.BACK)
        if (errors.isNotEmpty()) {
            builder.addEndHeaderAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_error_outline)).build())
                    .setOnClickListener { pushApiErrorsDetailScreen() }
                    .build()
            )
        }
        return builder
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("CustomMapPoiScreen", "onSurfaceAvailable")
        surfaceRenderer?.stop()
        val surface = surfaceContainer.surface
        if (surface == null) {
            // Some head units/emulators can report an available container before the Surface is ready.
            // Avoid crashing; we'll get called again when the Surface is non-null.
            Log.w("CustomMapPoiScreen", "SurfaceContainer.surface is null; skipping renderer start")
            surfaceRenderer = null
            return
        }
        surfaceRenderer = AutoSurfaceRenderer(
            carContext,
            surface,
            surfaceContainer.width,
            surfaceContainer.height
        ).apply {
            updateLocation(searchLat, searchLon)
            val settings = settingsManager.settings.value
            val filteredPois = getFilteredPois(settings)
            updateUserLocation(searchLat, searchLon)
            updatePois(
                newPois = filteredPois,
                effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                effectivePowerLevels = settings.effectiveIrvePowerLevels()
            )
            start()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("CustomMapPoiScreen", "onSurfaceDestroyed")
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    override fun onGetTemplate(): Template {
        return try {
            val actionStrip = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Home")
                        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                        .setOnClickListener { screenManager.popToRoot() }
                        .build()
                )
                .build()

            val title = "Nearby POIs (Custom)"

            if (isLoading) {
                return MapWithContentTemplate.Builder()
                    .setContentTemplate(
                        ListTemplate.Builder()
                            .setLoading(true)
                            .setHeader(mapContentHeaderBuilder(title).build())
                            .build()
                    )
                    .setActionStrip(actionStrip)
                    .build()
            }

            // Build POI rows.
            val itemListBuilder = ItemList.Builder()
                .setNoItemsMessage("No POIs found")

            // 1) Functional rows (Recenter, External Map, Settings)
            itemListBuilder.addItem(
                androidx.car.app.model.Row.Builder()
                    .setTitle("Recenter")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener { loadPois() }
                    .build()
            )

            itemListBuilder.addItem(
                androidx.car.app.model.Row.Builder()
                    .setTitle("Open in External Map")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener {
                        val intent = Intent(CarContext.ACTION_NAVIGATE).apply {
                            data = Uri.parse("geo:$searchLat,$searchLon?q=${Uri.encode("Map")}")
                        }
                        carContext.startCarApp(intent)
                    }
                    .build()
            )

            itemListBuilder.addItem(
                androidx.car.app.model.Row.Builder()
                    .setTitle("Settings")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                    .setOnClickListener { screenManager.push(AutoMapSettingsScreen(carContext, settingsManager)) }
                    .build()
            )

            // 2) Optional rows
            val hasRoutePlanning = routePlanner != null && routingClient != null && tollCalculator != null && geocodingClient != null
            val hasCommunity = settingsManager.settings.value.isLoggedIn && communityRepo != null
            if (hasCommunity) {
                itemListBuilder.addItem(
                    androidx.car.app.model.Row.Builder()
                        .setTitle("Add POI")
                        .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_jules)).build())
                        .setOnClickListener {
                            lifecycleScope.launch {
                                val loc = LocationHelper.getCurrentLocation(carContext)
                                val clat = loc?.latitude ?: searchLat
                                val clon = loc?.longitude ?: searchLon
                                screenManager.push(AddPoiAutoScreen(carContext, communityRepo, clat, clon) { loadPois() })
                            }
                        }
                        .build()
                )
            } else if (hasRoutePlanning) {
                itemListBuilder.addItem(
                    androidx.car.app.model.Row.Builder()
                        .setTitle("Plan route")
                        .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_swap_horiz)).build())
                        .setOnClickListener {
                            screenManager.push(
                                AutoRoutePlanningScreen(
                                    carContext = carContext,
                                    routePlanner = routePlanner,
                                    routingClient = routingClient,
                                    poiProvider = poiProvider,
                                    geocodingClient = geocodingClient,
                                    settingsManager = settingsManager
                                )
                            )
                        }
                        .build()
                )
            }

            val currentSettings = settingsManager.settings.value
            val filteredPois = getFilteredPois(currentSettings)

            surfaceRenderer?.let { renderer ->
                renderer.updateLocation(searchLat, searchLon)
                renderer.updatePois(
                    newPois = filteredPois,
                    effectiveEnergyTypes = currentSettings.effectiveMapEnergyFilterIds(),
                    effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()
                )
            }

            val limitedPois = filteredPois.take(6)
            val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()
            val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()
            limitedPois.forEach { poi ->
                val availability = availabilityByPoiId[poi.id]
                itemListBuilder.addItem(
                    AutoPoiUiHelper.buildPoiRow(
                        carContext = carContext,
                        poi = poi,
                        availability = availability,
                        effectiveEnergyTypes = effectiveEnergies,
                        effectivePowerLevels = effectivePowerLevels,
                        distanceFromLatLon = searchLat to searchLon
                    ) {
                        screenManager.push(
                            PoiDetailScreen(
                                carContext = carContext,
                                poi = poi,
                                availabilitySummary = availability,
                                rating = null
                            )
                        )
                    }
                )
            }

            val listTemplate = ListTemplate.Builder()
                .setHeader(mapContentHeaderBuilder(title).build())
                .setSingleList(itemListBuilder.build())
                .build()

            return MapWithContentTemplate.Builder()
                .setContentTemplate(listTemplate)
                .setActionStrip(actionStrip)
                .build()
        } catch (e: Exception) {
            Log.e("CustomMapPoiScreen", "Error building template", e)
            MessageTemplate.Builder("Failed to load map: ${e.message}")
                .setHeader(
                    Header.Builder()
                        .setTitle("Error")
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .build()
        }
    }
}

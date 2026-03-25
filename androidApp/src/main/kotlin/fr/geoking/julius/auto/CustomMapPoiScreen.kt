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
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiSearchRequest
import fr.geoking.julius.poi.PoiProviderError
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.api.availability.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.availability.StationAvailabilitySummary
import fr.geoking.julius.api.geocoding.GeocodingClient
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.toll.TollCalculator
import kotlinx.coroutines.flow.collectLatest
import fr.geoking.julius.api.availability.matchAvailabilityToPois
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
                    PoiRelatedSettings(
                        s.selectedPoiProviders,
                        s.selectedMapEnergyTypes,
                        s.mapEnseigneType,
                        s.selectedMapServices,
                        s.mapPowerLevels,
                        s.mapIrveOperators,
                        s.mapBrands,
                        s.selectedMapConnectorTypes,
                        s.selectedOverpassAmenityTypes,
                        s.vehicleType
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    loadPois()
                }
        }
    }

    private data class PoiRelatedSettings(
        val providers: Set<PoiProviderType>,
        val energies: Set<String>,
        val enseigne: String,
        val services: Set<String>,
        val powerLevels: Set<Int>,
        val operators: Set<String>,
        val brands: Set<String>,
        val connectors: Set<String>,
        val amenities: Set<String>,
        val vehicleType: fr.geoking.julius.VehicleType
    )

    private fun loadPois() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()

            var lat = 48.8566
            var lon = 2.3522

            if (carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                carContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val location = fr.geoking.julius.LocationHelper.getCurrentLocation(carContext)
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                }
            }

            searchLat = lat
            searchLon = lon
            Log.d("CustomMapPoiScreen", "loadPois search center lat=$lat lon=$lon")

            try {
                val settings = settingsManager.settings.value
                val result = poiProvider.searchResult(PoiSearchRequest(lat, lon, null, emptySet()))
                pois = result.pois
                surfaceRenderer?.updatePois(
                    newPois = pois,
                    selectedEnergyTypes = settings.selectedMapEnergyTypes,
                    useVehicleFilter = settings.useVehicleFilter,
                    vehicleEnergy = settings.vehicleEnergy,
                    vehicleGasTypes = settings.vehicleGasTypes
                )
                errors = result.errors
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
                errors = listOf(PoiProviderError("System", e.message ?: "Unknown error", true))
                availabilityByPoiId = emptyMap()
            }
            isLoading = false
            invalidate()
        }
    }


    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("CustomMapPoiScreen", "onSurfaceAvailable")
        surfaceRenderer?.stop()
        surfaceRenderer = AutoSurfaceRenderer(
            carContext,
            surfaceContainer.surface!!,
            surfaceContainer.width,
            surfaceContainer.height
        ).apply {
            updateLocation(searchLat, searchLon)
            val settings = settingsManager.settings.value
            updatePois(
                newPois = pois,
                selectedEnergyTypes = settings.selectedMapEnergyTypes,
                useVehicleFilter = settings.useVehicleFilter,
                vehicleEnergy = settings.vehicleEnergy,
                vehicleGasTypes = settings.vehicleGasTypes
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
            val actionStripBuilder = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Home")
                        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                        .setOnClickListener { screenManager.popToRoot() }
                        .build()
                )

            if (errors.isNotEmpty()) {
                actionStripBuilder.addAction(
                    Action.Builder()
                        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_error_outline)).build())
                        .setOnClickListener {
                            val errorMsg = errors.joinToString("\n") { "${it.providerName}: ${it.message}" }
                            screenManager.push(
                                object : Screen(carContext) {
                                    override fun onGetTemplate(): Template {
                                        return MessageTemplate.Builder(errorMsg)
                                            .setTitle("API Errors")
                                            .setHeaderAction(Action.BACK)
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
                        .build()
                )
            }
            val actionStrip = actionStripBuilder.build()

            val title = "Nearby POIs (Custom)"

            if (isLoading) {
                return MapWithContentTemplate.Builder()
                    .setContentTemplate(
                        ListTemplate.Builder()
                            .setLoading(true)
                            .setHeader(
                                Header.Builder()
                                    .setTitle(title)
                                    .setStartHeaderAction(Action.BACK)
                                    .build()
                            )
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
                                val loc = fr.geoking.julius.LocationHelper.getCurrentLocation(carContext)
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

            val limitedPois = pois.take(10)
            val currentSettings = settingsManager.settings.value
            limitedPois.forEach { poi ->
                val availability = availabilityByPoiId[poi.id]
                itemListBuilder.addItem(
                    AutoPoiUiHelper.buildPoiRow(
                        carContext = carContext,
                        poi = poi,
                        availability = availability,
                        selectedEnergyTypes = currentSettings.selectedMapEnergyTypes,
                        useVehicleFilter = currentSettings.useVehicleFilter,
                        vehicleEnergy = currentSettings.vehicleEnergy,
                        vehicleGasTypes = currentSettings.vehicleGasTypes
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
                .setHeader(
                    Header.Builder()
                        .setTitle(title)
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
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

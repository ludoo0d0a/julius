package fr.geoking.julius.auto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Header
import androidx.car.app.model.Metadata
import androidx.car.app.model.MessageTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.AppManager
import androidx.lifecycle.DefaultLifecycleObserver
import fr.geoking.julius.poi.PoiProviderType
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.AppSettings
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
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
import fr.geoking.julius.ui.BrandHelper
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MapPoiScreen(
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
    /** Search center (user location or default) for anchor and POI fetch. */
    private var searchLat: Double = 48.8566
    private var searchLon: Double = 2.3522

    private var surfaceRenderer: AutoSurfaceRenderer? = null

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    PoiRelatedSettings(
                        s.selectedPoiProvider,
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
        val provider: PoiProviderType,
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
            Log.d("MapPoiScreen", "loadPois search center lat=$lat lon=$lon")

            try {
                val result = poiProvider.searchResult(PoiSearchRequest(lat, lon, null, emptySet()))
                pois = result.pois
                errors = result.errors
                Log.d("MapPoiScreen", "pois loaded: ${pois.size}, errors: ${errors.size}")
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
                Log.e("MapPoiScreen", "getGasStations failed", e)
                pois = emptyList()
                errors = listOf(PoiProviderError("System", e.message ?: "Unknown error", true))
                availabilityByPoiId = emptyMap()
            }
            isLoading = false
            invalidate()
        }
    }


    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("MapPoiScreen", "onSurfaceAvailable")
        surfaceRenderer?.stop()
        surfaceRenderer = AutoSurfaceRenderer(
            surfaceContainer.surface!!,
            surfaceContainer.width,
            surfaceContainer.height
        ).apply {
            start()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("MapPoiScreen", "onSurfaceDestroyed")
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

            var errorAction: Action? = null
            if (errors.isNotEmpty()) {
                errorAction = Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_error_outline)).build())
                    .setOnClickListener {
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
                    .build()
            }

            val title = "Nearby POIs"

            if (isLoading) {
                val loadingHeaderBuilder = Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                errorAction?.let { loadingHeaderBuilder.addEndHeaderAction(it) }

                return MapWithContentTemplate.Builder()
                    .setContentTemplate(
                        ListTemplate.Builder()
                            .setLoading(true)
                            .setHeader(loadingHeaderBuilder.build())
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
                Row.Builder()
                    .setTitle("Recenter")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener { loadPois() }
                    .build()
            )

            itemListBuilder.addItem(
                Row.Builder()
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
                Row.Builder()
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
                    Row.Builder()
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
                    Row.Builder()
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
            limitedPois.forEach { poi ->
                val iconResId = when (poi.poiCategory) {
                    PoiCategory.Toilet -> R.drawable.ic_poi_toilet
                    PoiCategory.DrinkingWater -> R.drawable.ic_poi_water
                    PoiCategory.Camping -> R.drawable.ic_poi_camping
                    PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan
                    PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic
                    PoiCategory.Radar -> R.drawable.ic_poi_radar
                    else -> if (poi.isElectric) R.drawable.ic_poi_electric else R.drawable.ic_poi_gas
                }

                val place = Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
                    .setMarker(
                        PlaceMarker.Builder()
                            .setIcon(
                                CarIcon.Builder(IconCompat.createWithResource(carContext, iconResId)).build(),
                                PlaceMarker.TYPE_ICON
                            )
                            .setLabel(if (poi.poiCategory == PoiCategory.Radar) "VMA" else "POI")
                            .build()
                    )
                    .build()

                val availability = availabilityByPoiId[poi.id]

                val row = Row.Builder()
                    .setTitle(poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" })
                    .addText("${poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.ifBlank { "${poi.latitude}, ${poi.longitude}" }} [Source: ${poi.source ?: "Unknown"}]")
                    .setMetadata(Metadata.Builder().setPlace(place).build())
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(
                            PoiDetailScreen(
                                carContext = carContext,
                                poi = poi,
                                availabilitySummary = availability,
                                rating = null
                            )
                        )
                    }
                    .build()

                itemListBuilder.addItem(row)
            }

            val headerBuilder = Header.Builder()
                .setTitle(title)
                .setStartHeaderAction(Action.BACK)
            errorAction?.let { headerBuilder.addEndHeaderAction(it) }

            val listTemplate = ListTemplate.Builder()
                .setHeader(headerBuilder.build())
                .setSingleList(itemListBuilder.build())
                .build()

            return MapWithContentTemplate.Builder()
                .setContentTemplate(listTemplate)
                .setActionStrip(actionStrip)
                .build()
        } catch (e: Exception) {
            Log.e("MapPoiScreen", "Error building template", e)
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

    private fun connectorLabel(id: String): String = when (id) {
        "type_2" -> "Type 2"
        "combo_ccs" -> "CCS"
        "chademo" -> "CHAdeMO"
        "ef" -> "E/F"
        "autre" -> "Autre"
        else -> id
    }
}

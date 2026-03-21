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
import androidx.car.app.navigation.model.MapTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import fr.geoking.julius.poi.PoiProviderType
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.AppSettings
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiCategory
import fr.geoking.julius.poi.PoiSearchRequest
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
) : Screen(carContext) {

    private var pois: List<Poi> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var favoriteIds: Set<String> = emptySet()
    private var isLoading = true
    /** Search center (user location or default) for anchor and POI fetch. */
    private var searchLat: Double = 48.8566
    private var searchLon: Double = 2.3522

    init {
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    PoiRelatedSettings(
                        s.selectedPoiProvider,
                        s.selectedMapEnergyTypes,
                        s.mapEnseigneType,
                        s.selectedMapServices,
                        s.mapMinPowerKw,
                        s.mapIrveOperator,
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
        val minPower: Int,
        val operator: String,
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
                pois = poiProvider.search(PoiSearchRequest(lat, lon, null, emptySet()))
                Log.d("MapPoiScreen", "pois loaded: ${pois.size}")
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
                availabilityByPoiId = emptyMap()
            }
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            val actions = mutableListOf<Action>()

            // 1) Always available actions (keep <= 4 for this template).
            actions += Action.Builder()
                .setTitle("Home")
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                .setOnClickListener { screenManager.popToRoot() }
                .build()

            actions += Action.Builder()
                .setTitle("Settings")
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                .setOnClickListener { screenManager.push(AutoMapSettingsScreen(carContext, settingsManager)) }
                .build()

            actions += Action.Builder()
                .setTitle("Recenter")
                .setOnClickListener { loadPois() }
                .build()

            actions += Action.Builder()
                .setTitle("Open map")
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .setOnClickListener {
                    val intent = Intent(CarContext.ACTION_NAVIGATE).apply {
                        data = Uri.parse("geo:$searchLat,$searchLon?q=${Uri.encode("Map")}")
                    }
                    carContext.startCarApp(intent)
                }
                .build()

            // 2) One optional action depending on availability.
            val hasRoutePlanning = routePlanner != null && routingClient != null && tollCalculator != null && geocodingClient != null
            val hasCommunity = settingsManager.settings.value.isLoggedIn && communityRepo != null
            if (hasCommunity) {
                actions += Action.Builder()
                    .setTitle("Add POI")
                    .setOnClickListener {
                        lifecycleScope.launch {
                            val loc = fr.geoking.julius.LocationHelper.getCurrentLocation(carContext)
                            val clat = loc?.latitude ?: searchLat
                            val clon = loc?.longitude ?: searchLon
                            screenManager.push(AddPoiAutoScreen(carContext, communityRepo, clat, clon) { loadPois() })
                        }
                    }
                    .build()
            } else if (hasRoutePlanning) {
                actions += Action.Builder()
                    .setTitle("Plan route")
                    .setOnClickListener {
                        screenManager.push(
                            AutoRoutePlanningScreen(
                                carContext = carContext,
                                routePlanner = routePlanner!!,
                                routingClient = routingClient!!,
                                poiProvider = poiProvider,
                                geocodingClient = geocodingClient!!,
                                settingsManager = settingsManager
                            )
                        )
                    }
                    .build()
            }

            val actionStripBuilder = ActionStrip.Builder()
            actions.take(4).forEach { actionStripBuilder.addAction(it) }
            val actionStrip = actionStripBuilder.build()

            val title = "Nearby POIs"
            val anchorPlace = Place.Builder(CarLocation.create(searchLat, searchLon)).build()

            if (isLoading) {
                return MapTemplate.Builder()
                    .setHeader(Header.Builder().setTitle(title).setStartHeaderAction(Action.BACK).build())
                    .setActionStrip(actionStrip)
                    .build()
            }

            // Build POI rows.
            // Marker metadata is still useful if the template supports host-rendered markers on top of our surface,
            // but MapTemplate usually expects the app to render markers on the surface.
            val itemListBuilder = ItemList.Builder()
                .setNoItemsMessage("No POIs found")

            val limitedPois = pois.take(10)
            limitedPois.forEach { poi ->
                val place = Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
                    .setMarker(
                        PlaceMarker.Builder()
                            .setIcon(
                                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_poi_gas)).build(),
                                PlaceMarker.TYPE_ICON
                            )
                            .setLabel("POI")
                            .build()
                    )
                    .build()

                val availability = availabilityByPoiId[poi.id]

                val row = Row.Builder()
                    .setTitle(poi.siteName?.takeIf { it.isNotBlank() } ?: poi.name.ifBlank { "POI" })
                    .addText(poi.addressLocal?.takeIf { it.isNotBlank() } ?: poi.address.ifBlank { "${poi.latitude}, ${poi.longitude}" })
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

            return MapTemplate.Builder()
                .setHeader(Header.Builder().setTitle(title).setStartHeaderAction(Action.BACK).build())
                .setActionStrip(actionStrip)
                .setItemList(itemListBuilder.build())
                .build()
        } catch (e: Exception) {
            Log.e("MapPoiScreen", "Error building template", e)
            MessageTemplate.Builder("Failed to load map: ${e.message}")
                .setHeader(Header.Builder().setTitle("Error").setStartHeaderAction(Action.BACK).build())
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

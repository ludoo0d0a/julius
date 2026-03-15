package fr.geoking.julius.auto

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
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
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
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
import fr.geoking.julius.api.availability.matchAvailabilityToPois
import fr.geoking.julius.ui.BrandHelper
import kotlinx.coroutines.launch

class MapPoiScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider,
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    private val settingsManager: SettingsManager,
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
        loadPois()
    }

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
            val builder = PlaceListMapTemplate.Builder()
                .setTitle("Gas Stations")
                .setHeaderAction(Action.BACK)
                .setCurrentLocationEnabled(
                    carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    carContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                )

            if (isLoading) {
                builder.setLoading(true)
            } else {
                // Anchor at search center so the map shows both user area and POI markers
                builder.setAnchor(
                    Place.Builder(CarLocation.create(searchLat, searchLon))
                        .setMarker(PlaceMarker.Builder().build())
                        .build()
                )

                val listBuilder = ItemList.Builder()
                    .setNoItemsMessage("No gas stations found")

                for (poi in pois) {
                    val iconResId = when (poi.poiCategory) {
                        PoiCategory.Toilet -> R.drawable.ic_poi_toilet_rounded
                        PoiCategory.DrinkingWater -> R.drawable.ic_poi_water_rounded
                        PoiCategory.Camping -> R.drawable.ic_poi_camping_rounded
                        PoiCategory.CaravanSite -> R.drawable.ic_poi_caravan_rounded
                        PoiCategory.PicnicSite -> R.drawable.ic_poi_picnic_rounded
                        else -> when {
                            poi.isElectric -> R.drawable.ic_poi_electric_rounded
                            else -> BrandHelper.getBrandInfo(poi.brand)?.roundedIconResId ?: R.drawable.ic_poi_gas_rounded
                        }
                    }
                    val carIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, iconResId)).build()

                    val metadata = Metadata.Builder()
                        .setPlace(
                            Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
                                .setMarker(PlaceMarker.Builder()
                                    .setIcon(carIcon, PlaceMarker.TYPE_ICON)
                                    .build())
                                .build()
                        )
                        .build()

                    val title = (if (poi.id in favoriteIds) "★ " else "") + (poi.name.ifBlank { " -no name- " })
                    val rowBuilder = Row.Builder()
                        .setTitle(title)
                        .addText(poi.address.ifBlank { " -no address- " })
                        .setMetadata(metadata)
                        .setBrowsable(true)
                        .setOnClickListener { screenManager.push(PoiDetailScreen(carContext, poi, availabilityByPoiId[poi.id], settingsManager.getPoiRating(poi.id))) }

                    poi.fuelPrices?.takeIf { it.isNotEmpty() }?.let { prices ->
                        val priceLine = prices.joinToString(" · ") { fp ->
                            if (fp.outOfStock) "${fp.fuelName}: —" else "${fp.fuelName}: €%.3f".format(fp.price)
                        }
                        rowBuilder.addText(priceLine)
                    }
                    if (poi.isElectric) {
                        listOfNotNull(
                            poi.operator?.takeIf { it.isNotBlank() },
                            if (poi.isOnHighway) "Autoroute" else null,
                            poi.chargePointCount?.let { n ->
                                if (n == 1) "1 point de charge" else "$n points de charge"
                            }
                        ).joinToString(" • ").takeIf { it.isNotBlank() }?.let { rowBuilder.addText(it) }
                        poi.irveDetails?.let { d ->
                            val parts = mutableListOf<String>()
                            if (d.connectorTypes.isNotEmpty()) {
                                parts.add(d.connectorTypes.sorted().joinToString(", ") { connectorLabel(it) })
                            }
                            if (d.gratuit == true) parts.add("Gratuit")
                            parts.joinToString(" • ").takeIf { it.isNotBlank() }?.let { rowBuilder.addText(it) }
                        }
                    }

                    listBuilder.addItem(rowBuilder.build())
                }
                builder.setItemList(listBuilder.build())
            }

            val actionStripBuilder = ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setIcon(CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithResource(carContext, fr.geoking.julius.R.drawable.ic_map)).build())
                        .setOnClickListener { loadPois() }
                        .build()
                )
            if (settingsManager.settings.value.isLoggedIn && communityRepo != null) {
                actionStripBuilder.addAction(
                    Action.Builder()
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
                )
            }
            builder.setActionStrip(actionStripBuilder.build())

            builder.build()
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

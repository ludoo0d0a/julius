package fr.geoking.julius.auto

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Template
import androidx.car.app.model.PlaceListMapTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiSearchRequest
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.belib.StationAvailabilitySummary
import fr.geoking.julius.api.belib.matchAvailabilityToPois
import fr.geoking.julius.effectiveIrvePowerLevels
import fr.geoking.julius.effectiveMapEnergyFilterIds
import fr.geoking.julius.feature.location.LocationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NativeMapPoiScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider,
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    private val settingsManager: SettingsManager,
    private val communityRepo: CommunityPoiRepository? = null,
    private val favoritesRepo: FavoritesRepository? = null
) : Screen(carContext), DefaultLifecycleObserver {

    private var pois: List<Poi> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var favoriteIds: Set<String> = emptySet()
    private var isLoading = true
    private var searchLat: Double = 48.8566
    private var searchLon: Double = 2.3522

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    s.selectedPoiProviders to s.selectedMapEnergyTypes
                }
                .distinctUntilChanged()
                .collectLatest {
                    loadPois()
                }
        }
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

            try {
                val result = poiProvider.searchResult(PoiSearchRequest(lat, lon, null, emptySet()))
                pois = result.pois
                favoriteIds = favoritesRepo?.getFavorites()?.map { it.id }?.toSet() ?: emptySet()
                val provider = availabilityProviderFactory.getProvider(lat, lon)
                if (provider != null) {
                    val availabilities = try {
                        provider.getAvailability(lat, lon, 10)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                }
            } catch (e: Exception) {
                Log.e("NativeMapPoiScreen", "loadPois failed", e)
            }
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Home")
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        val itemListBuilder = ItemList.Builder()
            .setNoItemsMessage("No POIs found")

        val currentSettings = settingsManager.settings.value
        val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()
        val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()
        pois.take(6).forEach { poi ->
            val availability = availabilityByPoiId[poi.id]
            itemListBuilder.addItem(
                AutoPoiUiHelper.buildPoiRow(
                    carContext = carContext,
                    poi = poi,
                    availability = availability,
                    effectiveEnergyTypes = effectiveEnergies,
                    effectivePowerLevels = effectivePowerLevels
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

        val anchorPlace = Place.Builder(CarLocation.create(searchLat, searchLon))
            .setMarker(PlaceMarker.Builder().build())
            .build()

        return PlaceListMapTemplate.Builder()
            .setTitle("Nearby POIs")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setLoading(isLoading)
            .setItemList(itemListBuilder.build())
            .setAnchor(anchorPlace)
            .build()
    }
}

package fr.geoking.julius.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Alert
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.geocoding.GeocodingClient
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.api.weather.WeatherProviderFactory
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.di.MapModuleLoader
import fr.geoking.julius.intent.IntentNavigationHelper
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.repository.FuelForecastRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkStatus
import fr.geoking.julius.toll.TollCalculator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

/**
 * Android Auto session without wiring the phone voice manager into the car (no car STT/TTS session hook).
 * Maps, routes, fuel outlook, and car-safe settings only.
 */
class CarAppSession : Session(), KoinComponent {

    private val settingsManager: SettingsManager by inject()
    private val networkService: NetworkService by inject()
    private val fuelForecastRepository: FuelForecastRepository by inject()
    private val store: ConversationStore by inject()
    private val julesClient: JulesClient by inject()
    private val julesRepository: JulesRepository by inject()

    private var cachedMapDeps: MapDeps? = null

    private var lastCountryCode: String? = null
    private var lastIsRoaming: Boolean? = null

    init {
        lifecycleScope.launch {
            networkService.status.collectLatest { status ->
                handleNetworkStatusChange(status)
            }
        }
    }

    private fun handleNetworkStatusChange(status: NetworkStatus) {
        val country = status.countryName ?: status.countryCode

        // Country change detection
        if (status.countryCode != null && status.countryCode != lastCountryCode) {
            if (lastCountryCode != null) {
                // Only show "welcome" if it's a real change, not the first detection
                // Actually the user said "When app detect user enter in a new country"
                // Usually this means crossing a border.
                val alert = Alert.Builder(NETWORK_ALERT_ID, CarText.create("welcome in \"$country\""), 5000)
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .build()

                carContext.getCarService(AppManager::class.java).showAlert(alert)
            }
            lastCountryCode = status.countryCode
        }

        // Roaming change detection
        if (status.isRoaming != lastIsRoaming) {
            if (lastIsRoaming != null) {
                val roamingText = if (status.isRoaming) "on" else "off"
                val networkName = status.operatorName ?: "Unknown"
                val text = "you're in \"$country\", roaming :$roamingText, network : \"$networkName\""

                val alert = Alert.Builder(NETWORK_ALERT_ID, CarText.create(text), 5000)
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .build()

                carContext.getCarService(AppManager::class.java).showAlert(alert)
            }
            lastIsRoaming = status.isRoaming
        }
    }

    fun getMapDeps(): MapDeps? {
        if (cachedMapDeps == null) {
            try {
                MapModuleLoader.ensureLoaded()
                cachedMapDeps = MapDeps(
                    poiProvider = get<PoiProvider>(),
                    availabilityProviderFactory = get<BorneAvailabilityProviderFactory>(),
                    communityRepo = get<CommunityPoiRepository>(),
                    favoritesRepo = get<FavoritesRepository>(),
                    trafficProviderFactory = get<TrafficProviderFactory>(),
                    weatherProviderFactory = get<WeatherProviderFactory>(),
                    routePlanner = get<RoutePlanner>(),
                    routingClient = get<RoutingClient>(),
                    tollCalculator = get<TollCalculator>(),
                    geocodingClient = get<GeocodingClient>()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load map dependencies", e)
                return null
            }
        }
        return cachedMapDeps
    }

    override fun onNewIntent(intent: Intent) {
        val nav = IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            val mapDeps = getMapDeps()
            if (mapDeps == null) {
                Log.e(TAG, "onNewIntent: mapDeps is null")
                return
            }
            val destQuery = nav.address ?: nav.latitude?.let { "${nav.latitude}, ${nav.longitude}" } ?: ""
            carContext.getCarService(androidx.car.app.ScreenManager::class.java).push(
                AutoRoutePlanningScreen(
                    carContext = carContext,
                    routePlanner = mapDeps.routePlanner,
                    routingClient = mapDeps.routingClient,
                    poiProvider = mapDeps.poiProvider,
                    geocodingClient = mapDeps.geocodingClient,
                    settingsManager = settingsManager,
                    initialDestinationQuery = destQuery,
                    initialDestination = nav
                )
            )
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val nav = IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            val mapDeps = getMapDeps()
            if (mapDeps == null) {
                return ErrorScreen(
                    carContext,
                    errorMessage = "Failed to load map components.",
                    errorDetail = "Dependencies could not be initialized."
                )
            }
            val destQuery = nav.address ?: nav.latitude?.let { "${nav.latitude}, ${nav.longitude}" } ?: ""
            return AutoRoutePlanningScreen(
                carContext = carContext,
                routePlanner = mapDeps.routePlanner,
                routingClient = mapDeps.routingClient,
                poiProvider = mapDeps.poiProvider,
                geocodingClient = mapDeps.geocodingClient,
                settingsManager = settingsManager,
                initialDestinationQuery = destQuery,
                initialDestination = nav
            )
        }
        return try {
            if (BuildConfig.IS_PLAYSTORE_DISTRIBUTION) {
                AutoPlaystoreDashboardScreen(
                    carContext = carContext,
                    settingsManager = settingsManager,
                    networkService = networkService,
                    fuelForecastRepository = fuelForecastRepository,
                    store = store,
                    julesClient = julesClient,
                    julesRepository = julesRepository,
                    getMapDeps = this::getMapDeps
                )
            } else {
                AutoDashboardScreen(
                    carContext = carContext,
                    settingsManager = settingsManager,
                    networkService = networkService,
                    fuelForecastRepository = fuelForecastRepository,
                    store = store,
                    julesClient = julesClient,
                    julesRepository = julesRepository,
                    getMapDeps = this::getMapDeps
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Android Auto root screen", e)
            ErrorScreen(
                carContext,
                errorMessage = e.message ?: e.toString(),
                errorDetail = e.stackTraceToString().take(300)
            )
        }
    }

    companion object {
        private const val TAG = "CarAppSession"
        private const val NETWORK_ALERT_ID = 1001
    }
}

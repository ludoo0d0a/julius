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
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.di.MapModuleLoader
import fr.geoking.julius.intent.IntentNavigationHelper
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.agents.ConversationalAgent
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
    private val conversationStore: ConversationStore by inject()
    private val julesClient: JulesClient by inject()
    private val julesRepository: JulesRepository by inject()

    private var cachedMapDeps: MapDeps? = null

    init {
        lifecycleScope.launch {
            networkService.status.collectLatest { status ->
                handleNetworkStatusChange(status)
            }
        }
    }

    private var lastIsRoaming: Boolean? = null

    private fun handleNetworkStatusChange(status: NetworkStatus) {
        val settings = settingsManager.settings.value
        val country = status.countryName ?: status.countryCode
        val lastCountryCode = settings.lastCountryCode
        val lastIsConnected = settings.lastIsConnected

        var updatedSettings = settings

        // Country change detection (Cross-border)
        if (status.countryCode != null && status.countryCode != lastCountryCode) {
            // Only show "welcome" if it's a real change detected after the first initialization (settings.lastCountryCode != null)
            // User requested: "When app is opening be sure a toast appears 10s at the top of Android auto, when cross border."
            if (lastCountryCode != null) {
                val alert = Alert.Builder(NETWORK_ALERT_ID, CarText.create("Welcome in \"$country\""), 10000)
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .build()

                carContext.getCarService(AppManager::class.java).showAlert(alert)
            }
            updatedSettings = updatedSettings.copy(lastCountryCode = status.countryCode)
        }

        // Connectivity change detection
        if (lastIsConnected != null && status.isConnected != lastIsConnected) {
            val text = if (status.isConnected) "Network is back online" else "Network is offline"
            val alert = Alert.Builder(NETWORK_ALERT_ID, CarText.create(text), 10000)
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .build()

            carContext.getCarService(AppManager::class.java).showAlert(alert)
        }

        if (status.isConnected != lastIsConnected) {
            updatedSettings = updatedSettings.copy(lastIsConnected = status.isConnected)
        }

        if (updatedSettings != settings) {
            settingsManager.saveSettings(updatedSettings)
        }

        // Roaming change detection (legacy, kept with 5s duration as not explicitly requested for 10s)
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
        val initError = fr.geoking.julius.JuliusApplication.initError
        if (initError != null) {
            return ErrorScreen(
                carContext,
                errorMessage = "Initialization Failed",
                errorDetail = initError.message ?: initError.toString()
            )
        }

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
            AutoHomeScreen(
                carContext = carContext,
                settingsManager = settingsManager,
                getMapDeps = this::getMapDeps,
                store = conversationStore,
                julesClient = julesClient,
                julesRepository = julesRepository
            )
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

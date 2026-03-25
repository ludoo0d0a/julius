package fr.geoking.julius.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.di.MapModuleLoader
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.julius.api.routing.RoutePlanner
import fr.geoking.julius.api.routing.RoutingClient
import fr.geoking.julius.api.traffic.TrafficProviderFactory
import fr.geoking.julius.api.weather.WeatherProviderFactory
import fr.geoking.julius.api.geocoding.GeocodingClient
import fr.geoking.julius.community.CommunityPoiRepository
import fr.geoking.julius.community.FavoritesRepository
import fr.geoking.julius.poi.PoiProvider
import fr.geoking.julius.toll.TollCalculator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.component.get

class VoiceSession : Session(), KoinComponent {

    private val store: ConversationStore by inject()
    private val settingsManager: SettingsManager by inject()
    private val voiceManager: fr.geoking.julius.shared.VoiceManager by inject()
    private val julesClient: JulesClient by inject()
    private val conversationalAgent: ConversationalAgent by inject()

    /** Map/POI deps are loaded only when user opens the Map tab. */
    private var cachedMapDeps: MapDeps? = null

    fun getMapDeps(): MapDeps {
        if (cachedMapDeps == null) {
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
        }
        return cachedMapDeps!!
    }

    override fun onNewIntent(intent: Intent) {
        val nav = fr.geoking.julius.IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            val mapDeps = getMapDeps()
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
        (voiceManager as? fr.geoking.julius.AndroidVoiceManager)?.setCarContext(carContext)
        val nav = fr.geoking.julius.IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            val mapDeps = getMapDeps()
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
            MainScreen(carContext, store, settingsManager, julesClient, this::getMapDeps, conversationalAgent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MainScreen", e)
            ErrorScreen(
                carContext,
                errorMessage = e.message ?: e.toString(),
                errorDetail = e.stackTraceToString().take(300)
            )
        }
    }

    companion object {
        private const val TAG = "VoiceSession"
    }
}

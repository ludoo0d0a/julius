package fr.geoking.julius

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.AppSettings
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.agents.AgentResponse
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.conversation.ConversationState
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.platform.PermissionManager
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkStatus
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.di.MapModuleLoader
import fr.geoking.julius.ui.JulesScreen
import fr.geoking.julius.ui.MapScreen
import fr.geoking.julius.ui.map.maplibre.VectorMapScreen
import fr.geoking.julius.ui.map.maplibre.DirectionsMapScreen
import fr.geoking.julius.ui.PhoneMainScreen
import fr.geoking.julius.ui.PhoneNetworkLocationScreen
import fr.geoking.julius.ui.PhoneDashboardScreen
import fr.geoking.julius.ui.PlaystoreTheme
import fr.geoking.julius.ui.RoutePlanningScreen
import fr.geoking.julius.api.routing.RouteResult
import fr.geoking.julius.ui.HistoryScreen
import fr.geoking.julius.ui.SettingsScreen
import fr.geoking.julius.ui.SettingsScreenPage
import fr.geoking.julius.ui.FuelForecastScreen
import fr.geoking.julius.agents.LlamatikModelHelper
import fr.geoking.julius.ui.agentConfigSettingsPages
import fr.geoking.julius.ui.evaluateAgentSetup
import fr.geoking.julius.api.github.GitHubClient
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesActivityEntity
import fr.geoking.julius.persistence.JulesDao
import fr.geoking.julius.persistence.JulesSessionEntity
import com.google.firebase.auth.FirebaseAuth
import fr.geoking.julius.poi.MockPoiProvider
import fr.geoking.julius.poi.Poi
import fr.geoking.julius.poi.PoiProviderType
import fr.geoking.julius.repository.FuelForecastRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.ui.UpdateAvailableDialog
import fr.geoking.julius.ui.anim.AnimationPalettes
import com.google.android.play.core.appupdate.AppUpdateInfo
import fr.geoking.julius.update.InAppUpdateHelper
import fr.geoking.julius.feature.auth.GoogleAuthManager
import fr.geoking.julius.feature.permission.AndroidPermissionManager
import fr.geoking.julius.intent.IntentNavigationHelper
import fr.geoking.julius.intent.NavDestination
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.android.get
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private var permissionDeferred: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionDeferred?.complete(isGranted)
        permissionDeferred = null
    }

    private val inAppUpdateHelper by lazy { InAppUpdateHelper(applicationContext) }
    private val mapDepsState = MutableStateFlow<MapDeps?>(null)
    private val pendingNavDestination = MutableStateFlow<NavDestination?>(null)
    /** Set from [handleIntent] when the host opens [julius://map/libremap] (e.g. Android Auto lab). */
    private val pendingLibreMapLab = MutableStateFlow(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val nav = IntentNavigationHelper.parseNavIntent(intent)
        if (nav != null) {
            pendingNavDestination.value = nav
        }
        val data = intent.data
        if (data?.scheme == "julius" && data.host == "map" && data.path == "/libremap") {
            pendingLibreMapLab.value = true
        }
    }

    private fun ensureMapDeps() {
        if (mapDepsState.value != null) return
        try {
            MapModuleLoader.ensureLoaded()
            mapDepsState.value = MapDeps(
                poiProvider = get(),
                availabilityProviderFactory = get(),
                communityRepo = get(),
                favoritesRepo = get(),
                trafficProviderFactory = get(),
                weatherProviderFactory = get(),
                routePlanner = get(),
                routingClient = get(),
                tollCalculator = get(),
                geocodingClient = get()
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "ensureMapDeps: failed to load map dependencies", e)
        }
    }

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // User cancelled or update failed; can check again later
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate start")

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val appError = JuliusApplication.initError
        if (appError != null) {
            android.util.Log.e("MainActivity", "Showing startup error (Koin failed)", appError)
            try {
                setContent { StartupErrorContent(appError) }
            } catch (ce: Throwable) {
                android.util.Log.e("MainActivity", "setContent failed for StartupErrorContent", ce)
            }
            return
        }

        handleIntent(intent)
        try {
            android.util.Log.d("MainActivity", "Resolving Koin dependencies...")
            val store: ConversationStore = get()
            val settingsManager: SettingsManager = get()
            val authManager: GoogleAuthManager = get()
            val permissionManager: PermissionManager = get()
            val julesClient: JulesClient = get()
            val julesRepository: JulesRepository = get()
            val voiceManager: VoiceManager = get()
            val conversationalAgent: ConversationalAgent = get()
            val networkService: NetworkService = get()
            val fuelForecastRepository: FuelForecastRepository = get()
            android.util.Log.d("MainActivity", "Dependencies resolved successfully.")

            if (!BuildConfig.IS_PLAYSTORE_DISTRIBUTION) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                (permissionManager as? AndroidPermissionManager)?.setOnPermissionRequest { permission, deferred ->
                    permissionDeferred = deferred
                    permissionLauncher.launch(permission)
                }
            }

            android.util.Log.d("MainActivity", "Calling setContent...")
            installMainComposeContent(
                store = store,
                settingsManager = settingsManager,
                authManager = authManager,
                julesClient = julesClient,
                julesRepository = julesRepository,
                voiceManager = voiceManager,
                conversationalAgent = conversationalAgent,
                networkService = networkService,
                fuelForecastRepository = fuelForecastRepository,
                isPlaystoreDistribution = BuildConfig.IS_PLAYSTORE_DISTRIBUTION
            )
            android.util.Log.d("MainActivity", "setContent called successfully.")
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Startup failed", e)
            try {
                setContent { StartupErrorContent(e) }
            } catch (ce: Throwable) {
                android.util.Log.e("MainActivity", "setContent failed for StartupErrorContent (fallback)", ce)
            }
        }
    }

    private fun installMainComposeContent(
        store: ConversationStore,
        settingsManager: SettingsManager,
        authManager: GoogleAuthManager,
        julesClient: JulesClient,
        julesRepository: JulesRepository,
        voiceManager: VoiceManager,
        conversationalAgent: ConversationalAgent,
        networkService: NetworkService,
        fuelForecastRepository: FuelForecastRepository,
        isPlaystoreDistribution: Boolean
    ) {
        try {
            setContent {
                MainActivityComposeRoot(
                    store = store,
                    settingsManager = settingsManager,
                    authManager = authManager,
                    mapDepsState = mapDepsState,
                    onRequestMapDeps = { ensureMapDeps() },
                    julesClient = julesClient,
                    julesRepository = julesRepository,
                    voiceManager = voiceManager,
                    conversationalAgent = conversationalAgent,
                    networkService = networkService,
                    fuelForecastRepository = fuelForecastRepository,
                    inAppUpdateHelper = inAppUpdateHelper,
                    updateResultLauncher = updateResultLauncher,
                    pendingNavDestination = pendingNavDestination,
                    pendingLibreMapLab = pendingLibreMapLab,
                    isPlaystoreDistribution = isPlaystoreDistribution
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "installMainComposeContent: setContent crashed", e)
            try {
                setContent { StartupErrorContent(e) }
            } catch (ce: Throwable) {
                android.util.Log.e("MainActivity", "installMainComposeContent: fallback setContent crashed", ce)
            }
        }
    }

    override fun onDestroy() {
        inAppUpdateHelper.unregister()
        super.onDestroy()
    }
}

@Composable
private fun MainActivityComposeRoot(
    store: ConversationStore,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager,
    mapDepsState: kotlinx.coroutines.flow.MutableStateFlow<MapDeps?>,
    onRequestMapDeps: () -> Unit,
    julesClient: JulesClient,
    julesRepository: JulesRepository,
    voiceManager: VoiceManager,
    conversationalAgent: ConversationalAgent,
    networkService: NetworkService,
    fuelForecastRepository: FuelForecastRepository,
    inAppUpdateHelper: InAppUpdateHelper,
    updateResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
    pendingNavDestination: MutableStateFlow<NavDestination?>,
    pendingLibreMapLab: MutableStateFlow<Boolean>,
    isPlaystoreDistribution: Boolean
) {
    android.util.Log.d("MainActivity", "Compose setContent block running")
    val state by store.state.collectAsState()
    val settings by settingsManager.settings.collectAsState()

    LaunchedEffect(Unit) {
        android.util.Log.d("MainActivity", "Compose first frame")
    }
    LaunchedEffect(settings.googleUserName) {
        store.userName = settings.googleUserName
    }

    // Sync settings on app start if logged in
    LaunchedEffect(Unit) {
        if (settings.isLoggedIn) {
            settingsManager.triggerPullAndMerge()
        }
    }

    val context = LocalContext.current
    val hasLocationPermission = remember(context) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    MainUI(
        state = state,
        store = store,
        settingsManager = settingsManager,
        authManager = authManager,
        mapDepsState = mapDepsState,
        onRequestMapDeps = onRequestMapDeps,
        julesClient = julesClient,
        julesRepository = julesRepository,
        voiceManager = voiceManager,
        conversationalAgent = conversationalAgent,
        networkService = networkService,
        fuelForecastRepository = fuelForecastRepository,
        inAppUpdateHelper = inAppUpdateHelper,
        onStartUpdate = { info -> inAppUpdateHelper.startUpdate(info, updateResultLauncher) },
        pendingNavDestinationFlow = pendingNavDestination,
        pendingLibreMapLab = pendingLibreMapLab,
        isPlaystoreDistribution = isPlaystoreDistribution,
        hasLocationPermission = hasLocationPermission
    )
}

@Composable
fun MainUI(
    state: ConversationState,
    store: ConversationStore,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager,
    mapDepsState: kotlinx.coroutines.flow.StateFlow<MapDeps?>,
    onRequestMapDeps: () -> Unit,
    julesClient: JulesClient,
    julesRepository: JulesRepository,
    voiceManager: VoiceManager,
    conversationalAgent: ConversationalAgent,
    networkService: NetworkService,
    fuelForecastRepository: FuelForecastRepository? = null,
    inAppUpdateHelper: InAppUpdateHelper? = null,
    onStartUpdate: (AppUpdateInfo) -> Unit = {},
    pendingNavDestinationFlow: kotlinx.coroutines.flow.MutableStateFlow<NavDestination?>? = null,
    pendingLibreMapLab: MutableStateFlow<Boolean>? = null,
    isPlaystoreDistribution: Boolean = false,
    hasLocationPermission: Boolean = false
) {
    val pendingNavFlow = pendingNavDestinationFlow ?: remember { MutableStateFlow<NavDestination?>(null) }
    val mapDeps by mapDepsState.collectAsState()
    val networkStatus by networkService.status.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    /** Play Store flavor uses a dashboard home first; full flavor starts on the voice screen. */
    var showMap by remember { mutableStateOf(false) }
    var showPlaystoreNetworkInfo by remember { mutableStateOf(false) }
    var showPlaystoreSettings by remember { mutableStateOf(false) }
    var playstoreSettingsInitialStack by remember { mutableStateOf<List<SettingsScreenPage>?>(null) }
    var showRoutePlanning by remember { mutableStateOf(false) }
    var showDirectionsMap by remember { mutableStateOf(false) }
    var showFuelForecast by remember { mutableStateOf(false) }
    var routeForDirections by remember { mutableStateOf<RouteResult?>(null) }
    var stationsForDirections by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var initialNavDestination by remember { mutableStateOf<NavDestination?>(null) }
    var initialMapCenter by remember { mutableStateOf<com.google.android.gms.maps.model.LatLng?>(null) }
    var settingsInitialStack by remember { mutableStateOf<List<SettingsScreenPage>?>(null) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        pendingNavFlow.collect { nav ->
            if (nav != null) {
                initialNavDestination = nav
                showRoutePlanning = true
                showMap = true
                pendingNavFlow.value = null
            }
        }
    }

    val libreMapLabFlow = pendingLibreMapLab ?: remember { MutableStateFlow(false) }
    LaunchedEffect(Unit) {
        libreMapLabFlow.collect { open ->
            if (open) {
                settingsManager.setPhoneMapEngine(MapEngine.MapLibre)
                showMap = true
                libreMapLabFlow.value = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val intent = (context as? Activity)?.intent
        if (intent?.data?.scheme == "julius" && intent.data?.host == "map") {
            val path = intent.data?.path
            val currentSettings = settingsManager.settings.value
            if (path == "/gas_stations") {
                if (currentSettings.useVehicleFilter && (currentSettings.vehicleEnergy == "gas" || currentSettings.vehicleEnergy == "hybrid")) {
                    // Already configured for car, just show map
                } else if (currentSettings.vehicleBrand.isNotEmpty() && (currentSettings.vehicleEnergy == "gas" || currentSettings.vehicleEnergy == "hybrid")) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Routex))
                    settingsManager.setUseVehicleFilter(false)
                }
            } else if (path == "/electric_stations") {
                if (currentSettings.useVehicleFilter && (currentSettings.vehicleEnergy == "electric" || currentSettings.vehicleEnergy == "hybrid")) {
                    // Already configured
                } else if (currentSettings.vehicleBrand.isNotEmpty() && (currentSettings.vehicleEnergy == "electric" || currentSettings.vehicleEnergy == "hybrid")) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouvElec))
                    settingsManager.setUseVehicleFilter(false)
                }
            } else if (path == "/libremap") {
                settingsManager.setPhoneMapEngine(MapEngine.MapLibre)
            }
            showMap = true
        }
    }
    var showJules by remember { mutableStateOf(false) }
    val settings by settingsManager.settings.collectAsState()
    val llamatikModelHelper = remember(context) { LlamatikModelHelper(context.applicationContext) }
    val setupIssue = remember(settings, llamatikModelHelper, conversationalAgent) {
        evaluateAgentSetup(settings, llamatikModelHelper, conversationalAgent)
    }

    LaunchedEffect(showMap, showRoutePlanning, isPlaystoreDistribution) {
        if (showMap || showRoutePlanning || isPlaystoreDistribution) onRequestMapDeps()
    }
    val paletteIndex by AnimationPalettes.index.collectAsState()
    val palette = remember(paletteIndex) { AnimationPalettes.paletteFor(paletteIndex) }
    val fallbackUpdateFlow = remember { MutableStateFlow<AppUpdateInfo?>(null) }
    val updateAvailable by (inAppUpdateHelper?.updateAvailable ?: fallbackUpdateFlow).collectAsState(initial = null)

    if (inAppUpdateHelper != null) {
        LaunchedEffect(Unit) {
            delay(500)
            inAppUpdateHelper.checkForUpdate()
        }
    }
    if (updateAvailable != null) {
        UpdateAvailableDialog(
            onCancel = { inAppUpdateHelper?.dismissUpdate() },
            onUpdate = { updateAvailable?.let { onStartUpdate(it) } }
        )
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0F172A))) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                isPlaystoreDistribution && showPlaystoreNetworkInfo -> {
                    BackHandler { showPlaystoreNetworkInfo = false }
                    PhoneNetworkLocationScreen(
                        networkService = networkService,
                        onBack = { showPlaystoreNetworkInfo = false }
                    )
                }
                isPlaystoreDistribution && showPlaystoreSettings -> {
                    BackHandler { showPlaystoreSettings = false }
                    SettingsScreen(
                        settingsManager = settingsManager,
                        authManager = authManager,
                        errorLog = state.errorLog,
                        onDismiss = { showPlaystoreSettings = false },
                        initialScreenStack = playstoreSettingsInitialStack,
                        onInitialRouteConsumed = { playstoreSettingsInitialStack = null }
                    )
                }
                isPlaystoreDistribution && showFuelForecast -> {
                    BackHandler { showFuelForecast = false }
                    FuelForecastScreen(
                        repository = fuelForecastRepository!!,
                        onBack = { showFuelForecast = false }
                    )
                }
                isPlaystoreDistribution && showDirectionsMap && mapDeps != null -> {
                    BackHandler { showDirectionsMap = false }
                    DirectionsMapScreen(
                        route = routeForDirections,
                        pois = stationsForDirections,
                        settingsManager = settingsManager,
                        onBack = { showDirectionsMap = false }
                    )
                }
                isPlaystoreDistribution && showMap && showRoutePlanning && mapDeps != null -> {
                    RoutePlanningScreen(
                        routePlanner = mapDeps!!.routePlanner,
                        routingClient = mapDeps!!.routingClient,
                        tollCalculator = mapDeps!!.tollCalculator,
                        trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                        poiProvider = mapDeps!!.poiProvider,
                        geocodingClient = mapDeps!!.geocodingClient,
                        settingsManager = settingsManager,
                        onBack = { showRoutePlanning = false; initialNavDestination = null },
                        onShowOnMap = { route, pois ->
                            routeForDirections = route
                            stationsForDirections = pois
                            showDirectionsMap = true
                        },
                        initialDestination = initialNavDestination
                    )
                }
                isPlaystoreDistribution && showMap && mapDeps == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                isPlaystoreDistribution && showMap && mapDeps != null -> {
                    BackHandler { showMap = false }
                    if (settings.phoneMapEngine == MapEngine.MapLibre) {
                        VectorMapScreen(
                            poiProvider = mapDeps!!.poiProvider,
                            availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                            trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                            settingsManager = settingsManager,
                            authManager = authManager,
                            store = store,
                            palette = palette,
                            initialCenter = initialMapCenter?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) },
                            onBack = { showMap = false; initialMapCenter = null },
                            onPlanRoute = { showRoutePlanning = true },
                            communityRepo = mapDeps!!.communityRepo,
                            favoritesRepo = mapDeps!!.favoritesRepo
                        )
                    } else {
                        MapScreen(
                            poiProvider = mapDeps!!.poiProvider,
                            availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                            trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                            settingsManager = settingsManager,
                            authManager = authManager,
                            store = store,
                            palette = palette,
                            initialCenter = initialMapCenter,
                            onBack = { showMap = false; initialMapCenter = null },
                            onPlanRoute = { showRoutePlanning = true },
                            communityRepo = mapDeps!!.communityRepo,
                            favoritesRepo = mapDeps!!.favoritesRepo
                        )
                    }
                }
                showJules -> {
                    JulesScreen(
                        onBack = { showJules = false },
                        julesClient = julesClient,
                        julesRepository = julesRepository,
                        settingsManager = settingsManager,
                        voiceManager = voiceManager
                    )
                }
                isPlaystoreDistribution && !showMap -> {
                    PhoneDashboardScreen(
                        settingsManager = settingsManager,
                        poiProvider = mapDeps?.poiProvider,
                        hasLocationPermission = hasLocationPermission,
                        geocodingClient = mapDeps?.geocodingClient,
                        favoritesRepo = mapDeps?.favoritesRepo,
                        mapDepsReady = mapDeps != null,
                        fuelForecastRepository = fuelForecastRepository,
                        onOpenMap = { center ->
                            initialMapCenter = center
                            showMap = true
                        },
                        onOpenRoutes = {
                            showRoutePlanning = true
                            showMap = true
                        },
                        onOpenJules = { showJules = true },
                        onOpenNetworkDiagnostics = { showPlaystoreNetworkInfo = true },
                        onOpenFuelForecast = { showFuelForecast = true },
                        onOpenSettings = { stack ->
                            playstoreSettingsInitialStack = stack
                            showPlaystoreSettings = true
                        }
                    )
                }
                showSettings && !isPlaystoreDistribution -> {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        authManager = authManager,
                        errorLog = state.errorLog,
                        onDismiss = { showSettings = false },
                        initialScreenStack = settingsInitialStack,
                        onInitialRouteConsumed = { settingsInitialStack = null }
                    )
                }
                showHistory && !isPlaystoreDistribution -> {
                    HistoryScreen(state = state, store = store, onBack = { showHistory = false })
                }
                showDirectionsMap && mapDeps != null -> {
                    BackHandler { showDirectionsMap = false }
                    DirectionsMapScreen(
                        route = routeForDirections,
                        pois = stationsForDirections,
                        settingsManager = settingsManager,
                        onBack = { showDirectionsMap = false }
                    )
                }
                showMap && showRoutePlanning && mapDeps != null -> {
                    RoutePlanningScreen(
                        routePlanner = mapDeps!!.routePlanner,
                        routingClient = mapDeps!!.routingClient,
                        tollCalculator = mapDeps!!.tollCalculator,
                        trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                        poiProvider = mapDeps!!.poiProvider,
                        geocodingClient = mapDeps!!.geocodingClient,
                        settingsManager = settingsManager,
                        onBack = { showRoutePlanning = false; initialNavDestination = null },
                        onShowOnMap = { route, pois ->
                            routeForDirections = route
                            stationsForDirections = pois
                            showDirectionsMap = true
                        },
                        initialDestination = initialNavDestination
                    )
                }
                showMap -> {
                    BackHandler { showMap = false }
                    if (mapDeps != null) {
                        if (settings.phoneMapEngine == MapEngine.MapLibre) {
                            VectorMapScreen(
                                poiProvider = mapDeps!!.poiProvider,
                                availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                                trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                                settingsManager = settingsManager,
                                authManager = authManager,
                                store = store,
                                palette = palette,
                            initialCenter = initialMapCenter?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) },
                            onBack = { showMap = false; initialMapCenter = null },
                                onPlanRoute = { showRoutePlanning = true },
                                communityRepo = mapDeps!!.communityRepo,
                                favoritesRepo = mapDeps!!.favoritesRepo
                            )
                        } else {
                            MapScreen(
                                poiProvider = mapDeps!!.poiProvider,
                                availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                                trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                                settingsManager = settingsManager,
                                authManager = authManager,
                                store = store,
                                palette = palette,
                            initialCenter = initialMapCenter,
                            onBack = { showMap = false; initialMapCenter = null },
                                onPlanRoute = { showRoutePlanning = true },
                                communityRepo = mapDeps!!.communityRepo,
                                favoritesRepo = mapDeps!!.favoritesRepo
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                else -> {
                    PhoneMainScreen(
                        state = state,
                        settings = settings,
                        palette = palette,
                        settingsManager = settingsManager,
                        store = store,
                        networkStatus = networkStatus,
                        onSettingsClick = {
                            settingsInitialStack = null
                            showSettings = true
                        },
                        onHistoryClick = { showHistory = true },
                        onMapClick = { showMap = true },
                        onJulesClick = { showJules = true },
                        setupIssue = setupIssue,
                        onOpenAgentSettings = {
                            settingsInitialStack = agentConfigSettingsPages()
                            showSettings = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupErrorContent(error: Throwable) {
    val message = error.message ?: error.toString()
    val fullDetail = buildStartupErrorDetail(error)
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0F172A))) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Startup error",
                        color = Color(0xFFF87171),
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp
                    )
                    if (fullDetail.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            fullDetail,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun buildStartupErrorDetail(error: Throwable): String {
    val sb = StringBuilder()
    var t: Throwable? = error
    var depth = 0
    while (t != null && depth < 10) {
        if (depth > 0) sb.append("\n\nCaused by: ")
        sb.append(t.javaClass.name).append(": ").append(t.message ?: "(no message)")
        val stack = t.stackTrace
        val limit = (stack.size).coerceAtMost(20)
        for (i in 0 until limit) {
            sb.append("\n    at ").append(stack[i].toString())
        }
        if (stack.size > limit) sb.append("\n    ... ${stack.size - limit} more")
        t = t.cause
        depth++
    }
    return sb.toString()
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
fun MainUIPreview() {
    val context = LocalContext.current
    val mockSettingsManager = rememberMockSettingsManager()
    val mockState = ConversationState(
        messages = emptyList(),
        status = VoiceEvent.Silence,
        currentTranscript = "",
        lastError = null
    )
    val mockStore = rememberMockStore()
        val mockAuthManager = GoogleAuthManager(context, mockSettingsManager, { mockStore }, FirebaseAuth.getInstance())

    val mapDepsFlow = remember { MutableStateFlow<MapDeps?>(null) }
    MainUI(
        state = mockState,
        store = mockStore,
        settingsManager = mockSettingsManager,
        authManager = mockAuthManager,
        mapDepsState = mapDepsFlow,
        onRequestMapDeps = {},
        julesClient = remember { JulesClient(HttpClient(OkHttp) {}) },
        julesRepository = remember {
            JulesRepository(
                context,
                JulesClient(HttpClient(OkHttp) {}),
                GitHubClient(HttpClient(OkHttp) {}),
                object : JulesDao {
                    override suspend fun insertSessions(sessions: List<JulesSessionEntity>) {}
                    override suspend fun getSessionsBySource(sourceName: String): List<JulesSessionEntity> = emptyList()
                    override suspend fun getSession(sessionId: String): JulesSessionEntity? = null
                    override suspend fun archiveSession(sessionId: String) {}
                    override suspend fun updateSessionPrStatus(sessionId: String, state: String, mergeable: Boolean?) {}
                    override suspend fun updateSessionState(sessionId: String, state: String?) {}
                    override suspend fun updateSessionLastUpdated(sessionId: String, lastUpdated: Long) {}
                    override suspend fun getPendingOfflineSessions(): List<JulesSessionEntity> = emptyList()
                    override suspend fun getPendingOfflineActivities(): List<JulesActivityEntity> = emptyList()
                    override suspend fun deleteSession(sessionId: String) {}
                    override suspend fun updateActivitiesSessionId(oldSessionId: String, newSessionId: String) {}
                    override suspend fun insertActivities(activities: List<JulesActivityEntity>) {}
                    override suspend fun getActivitiesBySession(sessionId: String): List<JulesActivityEntity> = emptyList()
                    override suspend fun clearActivitiesBySession(sessionId: String) {}
                },
                object : NetworkService {
                    override val status = MutableStateFlow(NetworkStatus())
                    override suspend fun getCurrentStatus() = status.value
                },
                mockSettingsManager
            )
        },
        voiceManager = mockStore.voiceManager,
        conversationalAgent = remember {
            object : ConversationalAgent {
                override suspend fun process(input: String) =
                    AgentResponse("Mock response", null, null)
            }
        },
        networkService = remember {
            object : NetworkService {
                override val status = MutableStateFlow(NetworkStatus())
                override suspend fun getCurrentStatus() = status.value
            }
        }
    )
}

@Composable
private fun MapScreenPreview() {
    val mockSettingsManager = rememberMockSettingsManager()
    val mockStore = rememberMockStore()
    val context = LocalContext.current
    MapScreen(
        poiProvider = remember { MockPoiProvider() },
        availabilityProviderFactory = null,
        settingsManager = mockSettingsManager,
        authManager = GoogleAuthManager(context, mockSettingsManager, { mockStore }, FirebaseAuth.getInstance()),
        store = mockStore,
        palette = AnimationPalettes.paletteFor(0),
        onBack = {}
    )
}

@Composable
private fun rememberMockStore(): ConversationStore = remember {
    object : ConversationStore(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
        agent = object : ConversationalAgent {
            override suspend fun process(input: String) = AgentResponse("Mock response", null, null)
        },
        voiceManager = object : VoiceManager {
            private val _events = kotlinx.coroutines.flow.MutableStateFlow(VoiceEvent.Silence)
            private val _transcribedText = kotlinx.coroutines.flow.MutableStateFlow("")
            private val _partialText = kotlinx.coroutines.flow.MutableStateFlow("")
            override val events = _events
            override val transcribedText = _transcribedText
            override val partialText = _partialText
            override fun startListening() {}
            override fun stopListening() {}
            override fun speak(text: String, languageTag: String?, isInterruptible: Boolean) {}
            override fun playAudio(bytes: ByteArray) {}
            override fun stopSpeaking() {}
            override fun setTranscriber(transcriber: suspend (ByteArray) -> String?) {}
        },
        actionExecutor = null
    ) {}
}

@Composable
private fun rememberMockSettingsManager(): SettingsManager {
    val context = LocalContext.current
    return remember(context) {
        object : SettingsManager(context) {
            private val mockSettings = kotlinx.coroutines.flow.MutableStateFlow(AppSettings())
            override val settings = mockSettings
            override fun setPoiProviderTypes(types: Set<PoiProviderType>) {
                mockSettings.value = mockSettings.value.copy(selectedPoiProviders = types)
            }
            override fun saveSettings(settings: AppSettings) {}
        }
    }
}

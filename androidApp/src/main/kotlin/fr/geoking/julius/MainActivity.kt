package fr.geoking.julius

import android.Manifest
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
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.shared.PermissionManager
import fr.geoking.julius.shared.NetworkService
import fr.geoking.julius.shared.NetworkStatus
import fr.geoking.julius.shared.VoiceManager
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.di.MapModuleLoader
import fr.geoking.julius.ui.JulesScreen
import fr.geoking.julius.ui.MapScreen
import fr.geoking.julius.ui.PhoneMainScreen
import fr.geoking.julius.ui.RoutePlanningScreen
import fr.geoking.julius.ui.HistoryScreen
import fr.geoking.julius.ui.SettingsScreen
import fr.geoking.julius.ui.SettingsScreenPage
import fr.geoking.julius.agents.LlamatikModelHelper
import fr.geoking.julius.ui.agentConfigSettingsPages
import fr.geoking.julius.ui.evaluateAgentSetup
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.ui.UpdateAvailableDialog
import fr.geoking.julius.ui.anim.AnimationPalettes
import com.google.android.play.core.appupdate.AppUpdateInfo
import fr.geoking.julius.update.InAppUpdateHelper
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
    }

    private fun ensureMapDeps() {
        if (mapDepsState.value != null) return
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

        val appError = VoiceApplication.initError
        if (appError != null) {
            android.util.Log.e("MainActivity", "Showing startup error (Koin failed)", appError)
            setContent { StartupErrorContent(appError) }
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
            val voiceManager: VoiceManager = get()
            val conversationalAgent: ConversationalAgent = get()
            val networkService: NetworkService = get()
            // Map/route deps are resolved lazily when user opens map (see mapDepsState / ensureMapDeps)

            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            (permissionManager as? AndroidPermissionManager)?.setOnPermissionRequest { permission, deferred ->
                permissionDeferred = deferred
                permissionLauncher.launch(permission)
            }

            android.util.Log.d("MainActivity", "Calling setContent...")
            installMainComposeContent(
                store = store,
                settingsManager = settingsManager,
                authManager = authManager,
                julesClient = julesClient,
                voiceManager = voiceManager,
                conversationalAgent = conversationalAgent,
                networkService = networkService
            )
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Startup failed", e)
            setContent { StartupErrorContent(e) }
        }
    }

    private fun installMainComposeContent(
        store: ConversationStore,
        settingsManager: SettingsManager,
        authManager: GoogleAuthManager,
        julesClient: JulesClient,
        voiceManager: VoiceManager,
        conversationalAgent: ConversationalAgent,
        networkService: NetworkService
    ) {
        setContent {
            MainActivityComposeRoot(
                store = store,
                settingsManager = settingsManager,
                authManager = authManager,
                mapDepsState = mapDepsState,
                onRequestMapDeps = { ensureMapDeps() },
                julesClient = julesClient,
                voiceManager = voiceManager,
                conversationalAgent = conversationalAgent,
                networkService = networkService,
                inAppUpdateHelper = inAppUpdateHelper,
                updateResultLauncher = updateResultLauncher,
                pendingNavDestination = pendingNavDestination
            )
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
    voiceManager: VoiceManager,
    conversationalAgent: ConversationalAgent,
    networkService: NetworkService,
    inAppUpdateHelper: InAppUpdateHelper,
    updateResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
    pendingNavDestination: MutableStateFlow<NavDestination?>
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

    MainUI(
        state = state,
        store = store,
        settingsManager = settingsManager,
        authManager = authManager,
        mapDepsState = mapDepsState,
        onRequestMapDeps = onRequestMapDeps,
        julesClient = julesClient,
        voiceManager = voiceManager,
        conversationalAgent = conversationalAgent,
        networkService = networkService,
        inAppUpdateHelper = inAppUpdateHelper,
        onStartUpdate = { info -> inAppUpdateHelper.startUpdate(info, updateResultLauncher) },
        pendingNavDestinationFlow = pendingNavDestination
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
    voiceManager: VoiceManager,
    conversationalAgent: ConversationalAgent,
    networkService: NetworkService,
    inAppUpdateHelper: InAppUpdateHelper? = null,
    onStartUpdate: (AppUpdateInfo) -> Unit = {},
    pendingNavDestinationFlow: kotlinx.coroutines.flow.MutableStateFlow<NavDestination?>? = null
) {
    val pendingNavFlow = pendingNavDestinationFlow ?: remember { MutableStateFlow<NavDestination?>(null) }
    val mapDeps by mapDepsState.collectAsState()
    val networkStatus by networkService.status.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var showRoutePlanning by remember { mutableStateOf(false) }
    var initialNavDestination by remember { mutableStateOf<NavDestination?>(null) }
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
                    settingsManager.setPoiProviderTypes(setOf(fr.geoking.julius.poi.PoiProviderType.Routex))
                    settingsManager.setUseVehicleFilter(false)
                }
            } else if (path == "/electric_stations") {
                if (currentSettings.useVehicleFilter && (currentSettings.vehicleEnergy == "electric" || currentSettings.vehicleEnergy == "hybrid")) {
                    // Already configured
                } else if (currentSettings.vehicleBrand.isNotEmpty() && (currentSettings.vehicleEnergy == "electric" || currentSettings.vehicleEnergy == "hybrid")) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setPoiProviderTypes(setOf(fr.geoking.julius.poi.PoiProviderType.DataGouvElec))
                    settingsManager.setUseVehicleFilter(false)
                }
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

    LaunchedEffect(showMap, showRoutePlanning) {
        if (showMap || showRoutePlanning) onRequestMapDeps()
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
                showSettings -> {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        authManager = authManager,
                        errorLog = state.errorLog,
                        onDismiss = { showSettings = false },
                        initialScreenStack = settingsInitialStack,
                        onInitialRouteConsumed = { settingsInitialStack = null }
                    )
                }
                showHistory -> {
                    HistoryScreen(state = state, store = store, onBack = { showHistory = false })
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
                        initialDestination = initialNavDestination
                    )
                }
                showMap -> {
                    BackHandler { showMap = false }
                    if (mapDeps != null) {
                        MapScreen(
                            poiProvider = mapDeps!!.poiProvider,
                            availabilityProviderFactory = mapDeps!!.availabilityProviderFactory,
                            trafficProviderFactory = mapDeps!!.trafficProviderFactory,
                            settingsManager = settingsManager,
                            store = store,
                            onBack = { showMap = false },
                            onPlanRoute = { showRoutePlanning = true },
                            communityRepo = mapDeps!!.communityRepo,
                            favoritesRepo = mapDeps!!.favoritesRepo
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                showJules -> {
                    JulesScreen(
                        onBack = { showJules = false },
                        julesClient = julesClient,
                        settingsManager = settingsManager,
                        voiceManager = voiceManager
                    )
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
    val mockState = ConversationState(
        messages = emptyList(),
        status = VoiceEvent.Silence,
        currentTranscript = "",
        lastError = null
    )
    val mockStore = rememberMockStore()
    val mockSettingsManager = rememberMockSettingsManager()

    val context = LocalContext.current
    val mockAuthManager = remember { GoogleAuthManager(context, mockSettingsManager, { mockStore }) }

    val mapDepsFlow = remember { MutableStateFlow<MapDeps?>(null) }
    MainUI(
        state = mockState,
        store = mockStore,
        settingsManager = mockSettingsManager,
        authManager = mockAuthManager,
        mapDepsState = mapDepsFlow,
        onRequestMapDeps = {},
        julesClient = remember { JulesClient(HttpClient(OkHttp) {}) },
        voiceManager = mockStore.voiceManager,
        conversationalAgent = remember {
            object : ConversationalAgent {
                override suspend fun process(input: String) =
                    fr.geoking.julius.agents.AgentResponse("Mock response", null, null)
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
    MapScreen(
        poiProvider = remember { fr.geoking.julius.poi.MockPoiProvider() },
        availabilityProviderFactory = null,
        settingsManager = mockSettingsManager,
        store = rememberMockStore(),
        onBack = {}
    )
}

@Composable
private fun rememberMockStore(): ConversationStore = remember {
    object : ConversationStore(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
        agent = object : fr.geoking.julius.agents.ConversationalAgent {
            override suspend fun process(input: String) = fr.geoking.julius.agents.AgentResponse("Mock response", null, null)
        },
        voiceManager = object : fr.geoking.julius.shared.VoiceManager {
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
            override fun setPoiProviderTypes(types: Set<fr.geoking.julius.poi.PoiProviderType>) {
                mockSettings.value = mockSettings.value.copy(selectedPoiProviders = types)
            }
            override fun saveSettings(settings: AppSettings) {}
        }
    }
}

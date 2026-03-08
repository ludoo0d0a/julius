package fr.geoking.julius

import android.Manifest
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.shared.PermissionManager
import fr.geoking.julius.providers.MockPoiProvider
import fr.geoking.julius.providers.PoiProvider
import fr.geoking.julius.ui.JulesScreen
import fr.geoking.julius.ui.MapScreen
import fr.geoking.julius.ui.PhoneMainScreen
import fr.geoking.julius.ui.SettingsScreen
import fr.geoking.julius.providers.JulesClient
import fr.geoking.julius.ui.UpdateAvailableDialog
import fr.geoking.julius.ui.UpdateDownloadedDialog
import fr.geoking.julius.ui.anim.AnimationPalettes
import com.google.android.play.core.appupdate.AppUpdateInfo
import fr.geoking.julius.update.InAppUpdateHelper
import io.ktor.client.HttpClient
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

    private val inAppUpdateHelper = InAppUpdateHelper(applicationContext)
    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // User cancelled or update failed; can check again later
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appError = VoiceApplication.initError
        if (appError != null) {
            setContent { StartupErrorContent(appError) }
            return
        }

        try {
            val store: ConversationStore = get()
            val settingsManager: SettingsManager = get()
            val permissionManager: PermissionManager = get()
            val poiProvider: PoiProvider = get()
            val julesClient: JulesClient = get()

            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            (permissionManager as? AndroidPermissionManager)?.setOnPermissionRequest { permission, deferred ->
                permissionDeferred = deferred
                permissionLauncher.launch(permission)
            }

            setContent {
                val state by store.state.collectAsState()
                MainUI(
                    state = state,
                    store = store,
                    settingsManager = settingsManager,
                    poiProvider = poiProvider,
                    julesClient = julesClient,
                    inAppUpdateHelper = inAppUpdateHelper,
                    onStartUpdate = { info -> inAppUpdateHelper.startUpdate(info, updateResultLauncher) }
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Startup failed", e)
            setContent { StartupErrorContent(e) }
        }
    }

    override fun onDestroy() {
        inAppUpdateHelper.unregister()
        super.onDestroy()
    }
}

@Composable
fun MainUI(
    state: ConversationState,
    store: ConversationStore,
    settingsManager: SettingsManager,
    poiProvider: PoiProvider,
    julesClient: JulesClient,
    inAppUpdateHelper: InAppUpdateHelper? = null,
    onStartUpdate: (AppUpdateInfo) -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var showJules by remember { mutableStateOf(false) }
    val settings by settingsManager.settings.collectAsState()
    val paletteIndex by AnimationPalettes.index.collectAsState()
    val palette = remember(paletteIndex) { AnimationPalettes.paletteFor(paletteIndex) }
    val fallbackUpdateFlow = remember { MutableStateFlow<AppUpdateInfo?>(null) }
    val updateAvailable by (inAppUpdateHelper?.updateAvailable ?: fallbackUpdateFlow).collectAsState(initial = null)

    if (inAppUpdateHelper != null) {
        LaunchedEffect(Unit) {
            inAppUpdateHelper.checkForUpdate()
        }
    }
    if (updateAvailable != null) {
        UpdateAvailableDialog(
            onCancel = { inAppUpdateHelper?.dismissUpdate() },
            onUpdate = { updateAvailable?.let { onStartUpdate(it) } }
        )
    }
    val fallbackDownloadedFlow = remember { MutableStateFlow(false) }
    val updateDownloaded by (inAppUpdateHelper?.updateDownloaded ?: fallbackDownloadedFlow).collectAsState(initial = false)
    if (updateDownloaded) {
        UpdateDownloadedDialog(
            onRestart = { inAppUpdateHelper?.completeUpdate() }
        )
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0F172A))) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                showSettings -> {
                    SettingsScreen(settingsManager, state.errorLog) { showSettings = false }
                }
                showMap -> {
                    MapScreen(
                        poiProvider = poiProvider,
                        settingsManager = settingsManager,
                        store = store,
                        onBack = { showMap = false }
                    )
                }
                showJules -> {
                    JulesScreen(
                        onBack = { showJules = false },
                        julesClient = julesClient,
                        settingsManager = settingsManager
                    )
                }
                else -> {
                    PhoneMainScreen(
                        state = state,
                        settings = settings,
                        palette = palette,
                        settingsManager = settingsManager,
                        store = store,
                        onSettingsClick = { showSettings = true },
                        onMapClick = { showMap = true },
                        onJulesClick = { showJules = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupErrorContent(error: Throwable) {
    val message = error.message ?: error.toString()
    val detail = error.stackTraceToString().take(800)
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
                    if (detail.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            detail,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
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

    MainUI(
        state = mockState,
        store = mockStore,
        settingsManager = mockSettingsManager,
        poiProvider = remember { MockPoiProvider() },
        julesClient = remember { JulesClient(HttpClient(OkHttp) {}) }
    )
}

@Composable
private fun MapScreenPreview() {
    val mockSettingsManager = rememberMockSettingsManager()
    MapScreen(
        poiProvider = remember { MockPoiProvider() },
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
            override fun speak(text: String, languageTag: String?) {}
            override fun playAudio(bytes: ByteArray) {}
            override fun stopSpeaking() {}
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
            override fun setPoiProviderType(type: fr.geoking.julius.providers.PoiProviderType) {
                mockSettings.value = mockSettings.value.copy(selectedPoiProvider = type)
            }
            override fun saveSettings(
                openAiKey: String,
                elevenLabsKey: String,
                perplexityKey: String,
                geminiKey: String,
                deepgramKey: String,
                firebaseAiKey: String,
                firebaseAiModel: String,
                opencodeZenKey: String,
                opencodeZenModel: String,
                completionsMeKey: String,
                completionsMeModel: String,
                apifreellmKey: String,
                julesKey: String,
                agent: AgentType,
                theme: AppTheme,
                model: IaModel,
                fractalQuality: FractalQuality,
                fractalColorIntensity: FractalColorIntensity,
                extendedActionsEnabled: Boolean,
                localModelPath: String,
                selectedLocalModelVariant: String
            ) {}
        }
    }
}

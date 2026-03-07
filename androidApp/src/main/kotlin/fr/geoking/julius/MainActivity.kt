package fr.geoking.julius

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.shared.PermissionManager
import fr.geoking.julius.ui.MapScreen
import fr.geoking.julius.ui.PhoneMainScreen
import fr.geoking.julius.ui.SettingsScreen
import fr.geoking.julius.ui.anim.AnimationPalettes
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val store: ConversationStore by inject()
    private val settingsManager: SettingsManager by inject()
    private val permissionManager: PermissionManager by inject()

    private var permissionDeferred: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionDeferred?.complete(isGranted)
        permissionDeferred = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                settingsManager = settingsManager
            )
        }
    }
}

@Composable
fun MainUI(
    state: ConversationState,
    store: ConversationStore,
    settingsManager: SettingsManager
) {
    var showSettings by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    val settings by settingsManager.settings.collectAsState()
    val paletteIndex by AnimationPalettes.index.collectAsState()
    val palette = remember(paletteIndex) { AnimationPalettes.paletteFor(paletteIndex) }

    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0F172A))) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                showSettings -> {
                    SettingsScreen(settingsManager, state.errorLog) { showSettings = false }
                }
                showMap -> {
                    MapScreen(onBack = { showMap = false })
                }
                else -> {
                    PhoneMainScreen(
                        state = state,
                        settings = settings,
                        palette = palette,
                        settingsManager = settingsManager,
                        store = store,
                        onSettingsClick = { showSettings = true },
                        onMapClick = { showMap = true }
                    )
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
        settingsManager = mockSettingsManager
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
                agent: AgentType,
                theme: AppTheme,
                model: IaModel,
                fractalQuality: FractalQuality,
                fractalColorIntensity: FractalColorIntensity,
                extendedActionsEnabled: Boolean
            ) {}
        }
    }
}

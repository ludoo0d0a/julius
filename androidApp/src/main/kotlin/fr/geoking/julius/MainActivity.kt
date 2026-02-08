package fr.geoking.julius

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.ui.SettingsScreen
import fr.geoking.julius.ui.anim.phone.TrayLightEffectCanvas
import fr.geoking.julius.ui.components.ThemeBackground
import fr.geoking.julius.ui.components.VoiceControlButton
import fr.geoking.julius.ui.components.VoiceStatusContent
import fr.geoking.julius.ui.components.SettingsButton
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val store: ConversationStore by inject()
    private val settingsManager: SettingsManager by inject()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        
        setContent {
            val state by store.state.collectAsState()
            MainUI(
                state = state,
                store = store,
                settingsManager = settingsManager,
                onSettingsClick = { /* handled in MainUI */ }
            )
        }
    }
}

@Composable
fun MainUI(
    state: ConversationState,
    store: ConversationStore,
    settingsManager: SettingsManager,
    onSettingsClick: () -> Unit = {}
) {
    var showSettings by remember { mutableStateOf(false) }
    val settings by settingsManager.settings.collectAsState()
    val selectedTheme = settings.selectedTheme
    
    MaterialTheme(
        colorScheme = darkColorScheme(background = Color(0xFF0F172A))
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showSettings) {
                SettingsScreen(settingsManager, state.errorLog) { showSettings = false }
            } else {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // 1. Background Theme Effect (Applied from settings)
                    // Use key to ensure proper recomposition when theme changes
                    key(selectedTheme) {
                        ThemeBackground(
                            theme = selectedTheme,
                            isActive = state.status == VoiceEvent.Listening || state.status == VoiceEvent.Speaking
                        )
                    }
                    
                    // Responsive Container
                    Box(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                    ) {
                        VoiceStatusContent(
                            agentName = settings.selectedAgent.name,
                            status = state.status,
                            displayText = if (state.status == VoiceEvent.Listening) state.currentTranscript else state.messages.lastOrNull()?.text ?: "Hi, how can I help?",
                            lastError = state.lastError,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        TrayLightEffectCanvas(
                            isActive = state.status == VoiceEvent.Listening,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                        VoiceControlButton(
                            isListening = state.status == VoiceEvent.Listening,
                            onClick = { if (state.status == VoiceEvent.Listening) store.stopListening() else store.startListening() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp)
                        )
                        SettingsButton(
                            onClick = { showSettings = true },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 24.dp, bottom = 48.dp)
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
    
    // Create a mock store for preview (minimal implementation)
    val mockStore = remember {
        object : ConversationStore(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
            agent = object : fr.geoking.julius.agents.ConversationalAgent {
                override suspend fun process(input: String): fr.geoking.julius.agents.AgentResponse {
                    return fr.geoking.julius.agents.AgentResponse("Mock response", null, null)
                }
            },
            voiceManager = object : fr.geoking.julius.shared.VoiceManager {
                private val _events = MutableStateFlow(VoiceEvent.Silence)
                private val _transcribedText = MutableStateFlow("")
                override val events: kotlinx.coroutines.flow.Flow<VoiceEvent> = _events
                override val transcribedText: kotlinx.coroutines.flow.Flow<String> = _transcribedText
                override fun startListening() {}
                override fun stopListening() {}
                override fun speak(text: String) {}
                override fun playAudio(bytes: ByteArray) {}
                override fun stopSpeaking() {}
            },
            actionExecutor = null
        ) {
            // Override methods to prevent actual execution in preview
        }
    }
    
    val context = LocalContext.current
    val mockSettingsManager = remember(context) {
        object : SettingsManager(context) {
            private val mockSettings = MutableStateFlow(
                AppSettings()
            )
            override val settings: StateFlow<AppSettings> = mockSettings.asStateFlow()
            override fun saveSettings(
                openAiKey: String,
                elevenLabsKey: String,
                perplexityKey: String,
                geminiKey: String,
                deepgramKey: String,
                genkitApiKey: String,
                genkitEndpoint: String,
                firebaseAiKey: String,
                firebaseAiModel: String,
                agent: AgentType,
                theme: AppTheme,
                model: IaModel
            ) {
                // No-op for preview
            }
        }
    }
    
    MainUI(
        state = mockState,
        store = mockStore,
        settingsManager = mockSettingsManager
    )
}


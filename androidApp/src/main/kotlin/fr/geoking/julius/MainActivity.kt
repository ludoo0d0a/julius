package fr.geoking.julius

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.shared.PermissionManager
import fr.geoking.julius.ui.SettingsScreen
import fr.geoking.julius.ui.anim.phone.TrayLightEffectCanvas
import fr.geoking.julius.ui.anim.AnimationPalettes
import fr.geoking.julius.ui.components.MicroMainContent
import fr.geoking.julius.ui.components.ThemeBackground
import fr.geoking.julius.ui.components.VoiceControlButton
import fr.geoking.julius.ui.components.VoiceStatusContent
import fr.geoking.julius.ui.components.SettingsButton
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
    val paletteIndex by AnimationPalettes.index.collectAsState()
    val palette = remember(paletteIndex, selectedTheme) {
        if (selectedTheme == AppTheme.Micro) {
            AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex)
        } else {
            AnimationPalettes.paletteFor(paletteIndex)
        }
    }
    val currentSettings by rememberUpdatedState(settings)
    val currentTheme by rememberUpdatedState(selectedTheme)
    val verticalSwipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val horizontalSwipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    
    val backgroundColor = if (selectedTheme == AppTheme.Micro) Color(0xFF21004C) else Color(0xFF0F172A)
    MaterialTheme(
        colorScheme = darkColorScheme(background = backgroundColor)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (showSettings) {
                SettingsScreen(settingsManager, state.errorLog) { showSettings = false }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(verticalSwipeThresholdPx) {
                            var accumulated = 0f
                            detectVerticalDragGestures(
                                onDragEnd = { accumulated = 0f },
                                onDragCancel = { accumulated = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    accumulated += dragAmount
                                    if (accumulated <= -verticalSwipeThresholdPx) {
                                        AnimationPalettes.step(1)
                                        accumulated = 0f
                                    } else if (accumulated >= verticalSwipeThresholdPx) {
                                        AnimationPalettes.step(-1)
                                        accumulated = 0f
                                    }
                                }
                            )
                        }
                        .pointerInput(currentTheme, currentSettings, horizontalSwipeThresholdPx) {
                            var dragAmount = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragAmount = 0f },
                                onHorizontalDrag = { change, dragAmountX ->
                                    dragAmount += dragAmountX
                                    change.consume()
                                },
                                onDragEnd = {
                                    if (abs(dragAmount) >= horizontalSwipeThresholdPx) {
                                        val themes = AppTheme.entries
                                        val currentIndex = themes.indexOf(currentTheme).let { if (it < 0) 0 else it }
                                        val nextIndex = if (dragAmount < 0f) {
                                            (currentIndex + 1) % themes.size
                                        } else {
                                            (currentIndex - 1 + themes.size) % themes.size
                                        }
                                        val nextTheme = themes[nextIndex]
                                        if (nextTheme != currentTheme) {
                                            val saved = currentSettings
                                            settingsManager.saveSettings(
                                                openAiKey = saved.openAiKey,
                                                elevenLabsKey = saved.elevenLabsKey,
                                                perplexityKey = saved.perplexityKey,
                                                geminiKey = saved.geminiKey,
                                                deepgramKey = saved.deepgramKey,
                                                genkitApiKey = saved.genkitApiKey,
                                                genkitEndpoint = saved.genkitEndpoint,
                                                firebaseAiKey = saved.firebaseAiKey,
                                                firebaseAiModel = saved.firebaseAiModel,
                                                agent = saved.selectedAgent,
                                                theme = nextTheme,
                                                model = saved.selectedModel,
                                                fractalQuality = saved.fractalQuality,
                                                fractalColorIntensity = saved.fractalColorIntensity,
                                                extendedActionsEnabled = saved.extendedActionsEnabled
                                            )
                                        }
                                    }
                                    dragAmount = 0f
                                },
                                onDragCancel = { dragAmount = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 1. Background Theme Effect (Applied from settings)
                    // Use key to ensure proper recomposition when theme changes
                    key(selectedTheme) {
                        ThemeBackground(
                            theme = selectedTheme,
                            status = state.status,
                            palette = palette,
                            settings = settings
                        )
                    }
                    
                    // Responsive Container
                    Box(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                    ) {
                        if (selectedTheme == AppTheme.Micro) {
                            MicroMainContent(
                                status = state.status,
                                displayText = if (state.status == VoiceEvent.Listening) state.currentTranscript else state.messages.lastOrNull()?.text ?: "Hi, how can I help?",
                                lastError = state.lastError,
                                palette = palette,
                                onMicClick = {
                                    when (state.status) {
                                        VoiceEvent.Speaking -> store.stopSpeaking()
                                        VoiceEvent.Listening -> store.stopListening()
                                        else -> store.startListening()
                                    }
                                },
                                onCancelClick = {
                                    when (state.status) {
                                        VoiceEvent.Speaking -> store.stopSpeaking()
                                        VoiceEvent.Listening -> store.stopListening()
                                        else -> Unit
                                    }
                                },
                                onSettingsClick = { showSettings = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            VoiceStatusContent(
                                agentName = settings.selectedAgent.name,
                                status = state.status,
                                displayText = if (state.status == VoiceEvent.Listening) state.currentTranscript else state.messages.lastOrNull()?.text ?: "Hi, how can I help?",
                                lastError = state.lastError,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            TrayLightEffectCanvas(
                                isActive = state.status == VoiceEvent.Listening,
                                palette = palette,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                            VoiceControlButton(
                                status = state.status,
                                onClick = {
                                    when (state.status) {
                                        VoiceEvent.Speaking -> store.stopSpeaking()
                                        VoiceEvent.Listening -> store.stopListening()
                                        else -> store.startListening()
                                    }
                                },
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
                override fun speak(text: String, languageTag: String?) {}
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
                model: IaModel,
                fractalQuality: FractalQuality,
                fractalColorIntensity: FractalColorIntensity,
                extendedActionsEnabled: Boolean
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


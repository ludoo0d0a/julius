package com.antigravity.voiceai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.antigravity.voiceai.shared.ConversationStore
import com.antigravity.voiceai.shared.ConversationState
import com.antigravity.voiceai.shared.VoiceEvent
import com.antigravity.voiceai.ui.SettingsScreen
import com.antigravity.voiceai.ui.components.ParticleEffectCanvas
import com.antigravity.voiceai.ui.components.SphereEffectCanvas
import com.antigravity.voiceai.ui.components.WavesEffectCanvas
import com.antigravity.voiceai.ui.components.TrayLight
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
                    
                        // 3. Chat Content (Overlay)
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Current Agent Display
                            Text(
                                text = settings.selectedAgent.name,
                                color = Color(0xFF6366F1).copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.status.name,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (state.status == VoiceEvent.Listening) state.currentTranscript else state.messages.lastOrNull()?.text ?: "Hi, how can I help?",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium
                            )
                            state.lastError?.let { error ->
                                Spacer(modifier = Modifier.height(16.dp))

                                val errorTitle = when (error.httpCode) {
                                    401 -> "Authentication Error"
                                    403 -> "Permission Denied"
                                    429 -> "Rate Limit Exceeded"
                                    in 500..599 -> "Server Error"
                                    else -> "Connection Error"
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = errorTitle,
                                        color = Color(0xFFF87171),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    error.httpCode?.let {
                                        Text(
                                            text = "HTTP Code: $it",
                                            color = Color(0xFFF87171).copy(alpha = 0.8f),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = error.message,
                                        color = Color(0xFFF87171).copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        // 4. Bottom Tray Light (Constrained)
                        TrayLight(
                            isActive = state.status == VoiceEvent.Listening,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                        
                        // 5. Controls (Centered Mic/Speaker Button)
                        IconButton(
                            onClick = { 
                                if (state.status == VoiceEvent.Listening) store.stopListening() else store.startListening()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp)
                                .size(64.dp)
                        ) {
                             // Rounded background for the button
                             Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF6366F1), shape = androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                             ) {
                                 Icon(
                                     painter = painterResource(id = R.drawable.ic_speaker),
                                     contentDescription = "Speak",
                                     // Microphone icon turns red when listening
                                     tint = if (state.status == VoiceEvent.Listening) Color.Red else Color.White,
                                     modifier = Modifier.size(32.dp)
                                 )
                             }
                        }
                        
                        // 2. Settings Button (Bottom Left)
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start=24.dp, bottom=48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = "Settings",
                                tint = Color.White.copy(alpha=0.7f)
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
            agent = object : com.antigravity.voiceai.agents.ConversationalAgent {
                override suspend fun process(input: String): com.antigravity.voiceai.agents.AgentResponse {
                    return com.antigravity.voiceai.agents.AgentResponse("Mock response", null, null)
                }
            },
            voiceManager = object : com.antigravity.voiceai.shared.VoiceManager {
                private val _events = MutableStateFlow(VoiceEvent.Silence)
                private val _transcribedText = MutableStateFlow("")
                override val events: kotlinx.coroutines.flow.Flow<VoiceEvent> = _events
                override val transcribedText: kotlinx.coroutines.flow.Flow<String> = _transcribedText
                override fun startListening() {}
                override fun stopListening() {}
                override fun speak(text: String) {}
                override fun playAudio(data: ByteArray) {}
                override fun stopSpeaking() {}
            },
            actionExecutor = null
        ) {
            // Override methods to prevent actual execution in preview
        }
    }
    
    val mockSettingsManager = remember {
        object : SettingsManager(null as android.content.Context) {
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

@Composable
fun ThemeBackground(
    theme: AppTheme,
    isActive: Boolean
) {
    when (theme) {
        AppTheme.Particles -> {
            ParticleEffectCanvas(isActive = isActive)
        }
        AppTheme.Sphere -> {
            SphereEffectCanvas(isActive = isActive)
        }
        AppTheme.Waves -> {
            WavesEffectCanvas(isActive = isActive)
        }
    }
}

package fr.geoking.julius.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.feature.voice.VoskModelHelper
import fr.geoking.julius.shared.voice.LocalTranscriber
import fr.geoking.julius.shared.voice.SttEnginePreference
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoskTestScreen(
    voiceManager: VoiceManager,
    settingsManager: SettingsManager,
    localTranscriber: LocalTranscriber,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val events by voiceManager.events.collectAsState(initial = VoiceEvent.Silence)
    val partialText by voiceManager.partialText.collectAsState(initial = "")
    val finalText by voiceManager.transcribedText.collectAsState(initial = "")
    val settings by settingsManager.settings.collectAsState()
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var isPending by remember { mutableStateOf(false) }
    var isManualStop by remember { mutableStateOf(false) }

    val isListening = events == VoiceEvent.Listening
    val isMicSessionActive = events == VoiceEvent.Listening || events == VoiceEvent.Processing

    LaunchedEffect(events) {
        if (isMicSessionActive) {
            isPending = false
        }
    }

    var voskAvailable by remember { mutableStateOf(VoskModelHelper(context).isModelDownloaded()) }
    val useVosk = settings.sttEnginePreference != SttEnginePreference.NativeOnly

    val liveText = remember(finalText, partialText) {
        listOf(finalText.trim(), partialText.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vosk STT Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isManualStop) "Push-to-talk (Dictation): tap Start, speak, then tap Stop when done."
                       else "Tap and Speak: tap mic, speak a phrase, it stops automatically.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = when {
                    !useVosk -> "Engine: Native"
                    voskAvailable -> "Vosk model: ready"
                    else -> "Vosk model: missing"
                },
                color = if (useVosk && voskAvailable) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (useVosk && !voskAvailable) {
                VoskModelSettings(context, onModelReadyChanged = { voskAvailable = it })
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vosk Engine", color = Color.White)
                        Switch(
                            checked = useVosk,
                            onCheckedChange = { isVosk ->
                                val newPref = if (isVosk) SttEnginePreference.LocalOnly else SttEnginePreference.NativeOnly
                                settingsManager.setSttEnginePreference(newPref)
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Manual Stop (Dictation)", color = Color.White)
                        Switch(
                            checked = isManualStop,
                            onCheckedChange = { isManualStop = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 500L) return@Button
                        lastClickTime = now

                        if (isMicSessionActive) {
                            voiceManager.stopListening()
                        } else {
                            isPending = true
                            voiceManager.startListening(isManualStop = isManualStop)
                        }
                    },
                    enabled = (useVosk && voskAvailable) || !useVosk,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMicSessionActive) Color.Red else Color(0xFF3B82F6),
                    disabledContainerColor = Color(0xFF334155)
                ),
                modifier = Modifier.size(120.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isPending && !isMicSessionActive) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                    } else {
                        Icon(
                            imageVector = if (isMicSessionActive) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Text(
                        when {
                            isPending && !isMicSessionActive -> "Starting..."
                            isMicSessionActive -> "Stop"
                            else -> "Start"
                        }
                    )
                }
                }
            }

            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transcription:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        IconButton(onClick = { voiceManager.clearTranscriptionText() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }

                    Text(
                        text = liveText.ifBlank { if (isListening) "(listening…)" else "Speech text will appear here" },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    if (partialText.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))
                        Text("Live Partial:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text(
                            text = partialText,
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (finalText.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))
                        Text("Final History:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Text(
                            text = finalText,
                            color = Color.Cyan,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Status: ${events.name}",
                color = if (isListening) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                fontSize = 14.sp
            )
        }
    }
}

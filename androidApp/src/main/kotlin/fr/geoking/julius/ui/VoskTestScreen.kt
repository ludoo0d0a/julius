package fr.geoking.julius.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.SettingsManager
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
    val events by voiceManager.events.collectAsState(initial = VoiceEvent.Silence)
    val partialText by voiceManager.partialText.collectAsState(initial = "")
    val finalText by voiceManager.transcribedText.collectAsState(initial = "")
    val settings by settingsManager.settings.collectAsState()
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var isPending by remember { mutableStateOf(false) }

    val isListening = events == VoiceEvent.Listening
    val isMicSessionActive = events == VoiceEvent.Listening || events == VoiceEvent.Processing

    LaunchedEffect(events) {
        if (isMicSessionActive) {
            isPending = false
        }
    }

    val voskAvailable = remember(localTranscriber) { localTranscriber.isAvailable() }
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
                text = "Push-to-talk: tap mic to start, tap Stop when done (like dictation).",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = when {
                    !useVosk -> "Engine: Native (switch to Vosk below)"
                    voskAvailable -> "Vosk model: ready"
                    else -> "Vosk model: not found (add under assets/models/vosk/)"
                },
                color = if (useVosk && voskAvailable) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "STT Engine Preference",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Native", color = Color.White)
                Switch(
                    checked = useVosk,
                    onCheckedChange = { isVosk ->
                        val newPref = if (isVosk) SttEnginePreference.LocalOnly else SttEnginePreference.NativeOnly
                        settingsManager.setSttEnginePreference(newPref)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text("Vosk (Local)", color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime < 500L) return@Button
                    lastClickTime = now

                    if (isMicSessionActive) {
                        voiceManager.stopListening()
                    } else {
                        isPending = true
                        voiceManager.startListening()
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

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live (session):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(
                        text = liveText.ifBlank { if (isListening) "(listening…)" else "(tap Start)" },
                        color = Color.White,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))

                    Text("Partial:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(
                        text = partialText.ifBlank { "—" },
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))

                    Text("Committed finals:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(
                        text = finalText.ifBlank { "—" },
                        color = Color.Cyan,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
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

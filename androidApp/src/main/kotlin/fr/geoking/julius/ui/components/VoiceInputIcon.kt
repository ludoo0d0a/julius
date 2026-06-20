package fr.geoking.julius.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager
import kotlinx.coroutines.delay

@Composable
fun VoiceInputIcon(
    voiceManager: VoiceManager,
    onTranscriptionReceived: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    val events by voiceManager.events.collectAsState(initial = VoiceEvent.Silence)
    val transcribedText by voiceManager.transcribedText.collectAsState(initial = "")

    val isListening = events == VoiceEvent.Listening || events == VoiceEvent.Processing
    var sessionActive by remember { mutableStateOf(false) }

    LaunchedEffect(transcribedText, sessionActive) {
        if (sessionActive && transcribedText.isNotBlank()) {
            onTranscriptionReceived(transcribedText)
            voiceManager.clearTranscriptionText()
            sessionActive = false
        }
    }

    LaunchedEffect(events, sessionActive) {
        if (sessionActive && events == VoiceEvent.Silence) {
            delay(200)
            if (sessionActive && transcribedText.isBlank()) {
                sessionActive = false
            }
        }
    }

    IconButton(
        onClick = {
            when {
                sessionActive && isListening -> voiceManager.stopListening()
                !sessionActive -> {
                    voiceManager.clearTranscriptionText()
                    sessionActive = true
                    voiceManager.startListening(isManualStop = true)
                }
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (sessionActive && isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (sessionActive && isListening) "Stop dictation" else "Start dictation",
            tint = if (sessionActive && isListening) Color.Red else tint
        )
    }
}

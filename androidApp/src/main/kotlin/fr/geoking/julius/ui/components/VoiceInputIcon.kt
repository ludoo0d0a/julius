package fr.geoking.julius.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager

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
    var isActiveByMe by remember { mutableStateOf(false) }

    LaunchedEffect(transcribedText) {
        if (isActiveByMe && transcribedText.isNotBlank()) {
            onTranscriptionReceived(transcribedText)
            isActiveByMe = false
            // We don't necessarily want to clear it globally if other things are watching,
            // but for this app it's probably fine.
            voiceManager.clearTranscriptionText()
        }
    }

    LaunchedEffect(isListening) {
        if (!isListening) {
            isActiveByMe = false
        }
    }

    IconButton(
        onClick = {
            if (isListening && isActiveByMe) {
                voiceManager.stopListening()
                isActiveByMe = false
            } else if (!isListening) {
                voiceManager.clearTranscriptionText()
                isActiveByMe = true
                voiceManager.startListening(isManualStop = false)
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isListening && isActiveByMe) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = "Voice Input",
            tint = if (isListening && isActiveByMe) Color.Red else tint
        )
    }
}

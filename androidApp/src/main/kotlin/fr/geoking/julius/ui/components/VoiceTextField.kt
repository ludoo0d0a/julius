package fr.geoking.julius.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager
import kotlinx.coroutines.delay

private class VoiceDictationSession(
    val isActive: Boolean,
    val isListening: Boolean,
    val displayText: String,
    val onMicClick: () -> Unit,
)

@Composable
private fun rememberVoiceDictationSession(
    voiceManager: VoiceManager,
    value: String,
    onValueChange: (String) -> Unit,
): VoiceDictationSession {
    val events by voiceManager.events.collectAsState(initial = VoiceEvent.Silence)
    val partialText by voiceManager.partialText.collectAsState(initial = "")
    val transcribedText by voiceManager.transcribedText.collectAsState(initial = "")

    var sessionActive by remember { mutableStateOf(false) }
    var textAtSessionStart by remember { mutableStateOf("") }

    val isListening = events == VoiceEvent.Listening || events == VoiceEvent.Processing
    val isMicListening = sessionActive && isListening

    val displayText = when {
        sessionActive && partialText.isNotBlank() ->
            listOf(textAtSessionStart, partialText).filter { it.isNotBlank() }.joinToString(" ")
        sessionActive -> textAtSessionStart
        else -> value
    }

    LaunchedEffect(transcribedText, sessionActive) {
        if (sessionActive && transcribedText.isNotBlank()) {
            onValueChange(
                listOf(textAtSessionStart, transcribedText).filter { it.isNotBlank() }.joinToString(" ")
            )
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

    val onMicClick: () -> Unit = {
        when {
            sessionActive && isListening -> voiceManager.stopListening()
            !sessionActive -> {
                textAtSessionStart = value
                voiceManager.clearTranscriptionText()
                sessionActive = true
                voiceManager.startListening(isManualStop = true)
            }
        }
    }

    return VoiceDictationSession(
        isActive = sessionActive,
        isListening = isMicListening,
        displayText = displayText,
        onMicClick = onMicClick,
    )
}

/**
 * Text field with a microphone button for dictation via [VoiceManager] STT.
 * Tap mic to start, speak, tap stop when done — same pattern as Vosk test dictation mode.
 */
@Composable
fun VoiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    voiceManager: VoiceManager,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    micTint: Color = Color.Unspecified,
) {
    val session = rememberVoiceDictationSession(voiceManager, value, onValueChange)
    val resolvedMicTint = if (micTint == Color.Unspecified) {
        Color.White
    } else {
        micTint
    }

    OutlinedTextField(
        value = session.displayText,
        onValueChange = { if (!session.isActive) onValueChange(it) },
        modifier = modifier,
        enabled = enabled && !session.isActive,
        placeholder = placeholder,
        label = label,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        trailingIcon = {
            IconButton(
                onClick = session.onMicClick,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = if (session.isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (session.isListening) "Stop dictation" else "Start dictation",
                    tint = if (session.isListening) Color.Red else resolvedMicTint,
                )
            }
        },
        colors = colors,
    )
}

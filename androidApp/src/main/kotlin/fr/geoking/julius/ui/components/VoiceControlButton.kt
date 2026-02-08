package fr.geoking.julius.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.geoking.julius.R
import fr.geoking.julius.shared.VoiceEvent

/**
 * Central mic/speaker button: purple circle, red tint when listening.
 */
@Composable
fun VoiceControlButton(
    status: VoiceEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = status == VoiceEvent.Listening
    val isSpeaking = status == VoiceEvent.Speaking
    val iconRes = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker
    val contentDescription = if (isSpeaking) "Stop" else "Speak"
    val tint = when {
        isListening -> Color.Red
        else -> Color.White
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(64.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF6366F1), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun VoiceControlButtonIdlePreview() {
    VoiceControlButton(status = VoiceEvent.Silence, onClick = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun VoiceControlButtonListeningPreview() {
    VoiceControlButton(status = VoiceEvent.Listening, onClick = {})
}

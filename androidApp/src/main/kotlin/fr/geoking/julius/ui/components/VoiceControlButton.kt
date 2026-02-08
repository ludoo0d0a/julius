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

/**
 * Central mic/speaker button: purple circle, red tint when listening.
 */
@Composable
fun VoiceControlButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                painter = painterResource(id = R.drawable.ic_speaker),
                contentDescription = "Speak",
                tint = if (isListening) Color.Red else Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun VoiceControlButtonIdlePreview() {
    VoiceControlButton(isListening = false, onClick = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun VoiceControlButtonListeningPreview() {
    VoiceControlButton(isListening = true, onClick = {})
}

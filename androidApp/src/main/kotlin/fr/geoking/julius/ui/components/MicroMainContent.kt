package fr.geoking.julius.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.geoking.julius.R
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes

private val MicroNavBarDark = Color(0xFF1A0038)
private val MicroCancelButtonBg = Color(0xFF333333)

/**
 * Micro theme main content: header, large mic with glow, status, waveform, bottom bar.
 */
@Composable
fun MicroMainContent(
    status: VoiceEvent,
    displayText: String,
    lastError: DetailedError?,
    palette: AnimationPalette,
    onMicClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(palette.primary)
    val isListening = status == VoiceEvent.Listening
    val isSpeaking = status == VoiceEvent.Speaking
    val isActive = isListening || isSpeaking

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header: logo + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Julius Voice Assistant",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Center: large mic with glow + status text
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MicroMicButton(
                status = status,
                accentColor = accentColor,
                onClick = onMicClick
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = when (status) {
                    VoiceEvent.Listening -> "Listening...."
                    VoiceEvent.Speaking -> "Speaking..."
                    VoiceEvent.Processing -> "Processing..."
                    else -> displayText.ifBlank { "Tap to start" }
                },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
            if (status == VoiceEvent.Listening && displayText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayText,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            }
            lastError?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error.message,
                    color = Color(0xFFF87171),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            MicroWaveformCanvas(
                isActive = isActive,
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Bottom bar: Cancel + Settings
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MicroNavBarDark,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isListening || isSpeaking) {
                TextButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = MicroCancelButtonBg
                    )
                ) {
                    Text("Cancel", color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_home),
                        contentDescription = "Home",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_history),
                        contentDescription = "History",
                        tint = accentColor
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MicroMicButton(
    status: VoiceEvent,
    accentColor: Color,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micro_mic_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val isActive = status == VoiceEvent.Listening || status == VoiceEvent.Speaking

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(120.dp)
            .drawBehind {
                if (isActive) {
                    // Glow rings
                    for (i in 1..3) {
                        val radius = 60.dp.toPx() + i * 20.dp.toPx()
                        drawCircle(
                            color = accentColor.copy(alpha = glowAlpha * (1f - i * 0.2f)),
                            radius = radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    }
                }
            }
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(96.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = accentColor.copy(alpha = if (isActive) 1f else 0.9f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = when (status) {
                            VoiceEvent.Speaking -> R.drawable.ic_stop
                            else -> R.drawable.ic_speaker
                        }
                    ),
                    contentDescription = if (status == VoiceEvent.Speaking) "Stop" else "Speak",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF21004C)
@Composable
private fun MicroMainContentListeningPreview() {
    MicroMainContent(
        status = VoiceEvent.Listening,
        displayText = "What's the weather?",
        lastError = null,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex),
        onMicClick = { },
        onCancelClick = { },
        onSettingsClick = { }
    )
}

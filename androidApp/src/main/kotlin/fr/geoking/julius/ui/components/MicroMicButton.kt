package fr.geoking.julius.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.geoking.julius.R
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.ui.anim.AnimationPalettes

/**
 * Micro theme mic button: large animated button with glow rings, processing spinner.
 */
@Composable
fun MicroMicButton(
    status: VoiceEvent,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micro_mic_glow")
    val isActive = status == VoiceEvent.Listening || status == VoiceEvent.Speaking
    val isProcessing = status == VoiceEvent.Processing

    // Continuous glow pulse for background rings
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isActive) 0.8f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 1000 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Pulsing scale for the button itself
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else (if (isProcessing) 1.08f else 1f),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 800 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonScale"
    )

    // Dynamic rotation for processing state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "processingRotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .drawBehind {
                if (isActive || isProcessing) {
                    // Volumetric glow rings
                    val ringCount = if (isActive) 4 else 2
                    for (i in 1..ringCount) {
                        val baseRadius = 65.dp.toPx()
                        val ringPulse = (glowAlpha * 15.dp.toPx() * i)
                        val radius = baseRadius + ringPulse + (if (isProcessing) 5.dp.toPx() else 0f)

                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = glowAlpha * (1f - i * 0.2f)),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = radius
                            ),
                            radius = radius
                        )

                        drawCircle(
                            color = accentColor.copy(alpha = (glowAlpha * 0.5f) * (1f - i * 0.2f)),
                            radius = radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                }
            }
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(96.dp)
                .scale(buttonScale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.8f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.2f),
                            radius = size.width / 2.2f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (status == VoiceEvent.Processing) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer(rotationZ = rotation),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                            )
                            drawArc(
                                color = Color.White,
                                startAngle = 0f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 3.dp.toPx(),
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                    }
                } else {
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
}

@Preview(showBackground = true, backgroundColor = 0xFF21004C)
@Composable
private fun MicroMicButtonIdlePreview() {
    MicroMicButton(
        status = VoiceEvent.Silence,
        accentColor = Color(AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex).primary),
        onClick = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF21004C)
@Composable
private fun MicroMicButtonListeningPreview() {
    MicroMicButton(
        status = VoiceEvent.Listening,
        accentColor = Color(AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex).primary),
        onClick = {}
    )
}

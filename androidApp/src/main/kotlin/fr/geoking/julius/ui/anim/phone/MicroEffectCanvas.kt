package fr.geoking.julius.ui.anim.phone

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Micro theme background: Rotating light rays with volumetric effect.
 * Highly polished visual fidelity with state-aware animations.
 */
@Composable
fun MicroEffectCanvas(
    status: VoiceEvent,
    palette: AnimationPalette,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micro_rays")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val isActive = status == VoiceEvent.Listening || status == VoiceEvent.Speaking
    val isProcessing = status == VoiceEvent.Processing

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.2f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val primaryColor = Color(palette.primary)
    val secondaryColor = Color(palette.secondary)

    // State-dependent base colors
    val baseColor = when (status) {
        VoiceEvent.Processing -> Color(0xFF1E1B4B) // Slightly different blue-purple
        VoiceEvent.Speaking -> Color(0xFF2E1065) // Deep violet
        else -> Color(0xFF1A0038) // Original deep purple
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.width.coerceAtLeast(size.height) * 1.2f

        // 1. Deep background base
        drawRect(color = Color(0xFF0D001A))

        // 2. Rotating light rays
        rotate(rotation, pivot = Offset(centerX, centerY)) {
            val rayCount = 8
            for (i in 0 until rayCount) {
                val angle = (i * 360f / rayCount)
                rotate(angle, pivot = Offset(centerX, centerY)) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = if (isActive) 0.15f else 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, centerY),
                            radius = maxRadius * pulseScale
                        ),
                        alpha = 0.6f
                    )

                    // Thinner, brighter ray streaks
                    val rayWidth = size.width * 0.15f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryColor.copy(alpha = if (isActive) 0.1f else 0.05f),
                                Color.Transparent
                            ),
                            startX = centerX - rayWidth,
                            endX = centerX + rayWidth
                        ),
                        topLeft = Offset(centerX - rayWidth, centerY - maxRadius),
                        size = androidx.compose.ui.geometry.Size(rayWidth * 2, maxRadius * 2)
                    )
                }
            }
        }

        // 3. Counter-rotating secondary rays for complexity
        rotate(-rotation * 0.7f, pivot = Offset(centerX, centerY)) {
            val rayCount = 6
            for (i in 0 until rayCount) {
                val angle = (i * 360f / rayCount) + 15f
                rotate(angle, pivot = Offset(centerX, centerY)) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                secondaryColor.copy(alpha = if (isActive) 0.12f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, centerY),
                            radius = maxRadius * 0.8f * pulseScale
                        ),
                        alpha = 0.5f
                    )
                }
            }
        }

        // 4. Center glow vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = if (isActive) 0.8f else 0.4f),
                    Color.Transparent,
                    Color(0xFF0D001A).copy(alpha = 0.7f)
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius * 0.7f
            )
        )

        // 5. Dynamic Processing pulse
        if (isProcessing) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryColor.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 0.5f * pulseScale
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MicroEffectCanvasIdlePreview() {
    MicroEffectCanvas(
        status = VoiceEvent.Silence,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex)
    )
}

@Preview(showBackground = true)
@Composable
private fun MicroEffectCanvasActivePreview() {
    MicroEffectCanvas(
        status = VoiceEvent.Listening,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex)
    )
}

package fr.geoking.julius.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes
import kotlin.math.sin

/**
 * Micro theme waveform: Siri-style multi-layered ethereal waves.
 * Overlapping paths with dynamic movement and color gradients.
 */
@Composable
fun MicroWaveformCanvas(
    isActive: Boolean,
    palette: AnimationPalette,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micro_waveform")

    // Multiple phases for layered wave movement
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    val amplitudeMultiplier by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.15f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "amplitude"
    )

    val primaryColor = Color(palette.primary)
    val secondaryColor = Color(palette.secondary)
    val tertiaryColor = Color(palette.tertiary)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val centerY = size.height / 2
        val maxHeight = size.height * 0.5f * amplitudeMultiplier

        // Wave 1: Main bold wave
        drawEtherealWave(
            phase = phase1,
            frequency = 0.015f,
            amplitude = maxHeight * 0.9f,
            color = primaryColor,
            alpha = 0.8f,
            centerY = centerY,
            strokeWidth = 4f
        )

        // Wave 2: Secondary fluid wave
        drawEtherealWave(
            phase = phase2,
            frequency = 0.022f,
            amplitude = maxHeight * 0.6f,
            color = secondaryColor,
            alpha = 0.5f,
            centerY = centerY,
            strokeWidth = 3f
        )

        // Wave 3: Background subtle wave
        drawEtherealWave(
            phase = phase3,
            frequency = 0.012f,
            amplitude = maxHeight * 0.4f,
            color = tertiaryColor,
            alpha = 0.3f,
            centerY = centerY,
            strokeWidth = 2f
        )

        // Wave 4: Fast, small highlight wave
        drawEtherealWave(
            phase = -phase1 * 1.5f,
            frequency = 0.035f,
            amplitude = maxHeight * 0.2f,
            color = Color.White,
            alpha = 0.4f,
            centerY = centerY,
            strokeWidth = 1.5f
        )
    }
}

private fun DrawScope.drawEtherealWave(
    phase: Float,
    frequency: Float,
    amplitude: Float,
    color: Color,
    alpha: Float,
    centerY: Float,
    strokeWidth: Float
) {
    val path = Path()
    val segments = size.width.toInt() / 2
    val step = size.width / segments

    // Horizontal fade brush
    val waveBrush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            color.copy(alpha = alpha),
            color.copy(alpha = alpha),
            Color.Transparent
        ),
        startX = 0f,
        endX = size.width
    )

    path.moveTo(0f, centerY)
    for (i in 0..segments) {
        val x = i * step
        // Apply a Gaussian-like envelope so waves taper at ends
        val normalizedX = i.toFloat() / segments
        val envelope = sin(normalizedX * kotlin.math.PI.toFloat())

        val y = centerY + sin(x * frequency + phase) * amplitude * envelope
        path.lineTo(x, y)
    }

    drawPath(
        path = path,
        brush = waveBrush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF21004C)
@Composable
private fun MicroWaveformCanvasPreview() {
    MicroWaveformCanvas(
        isActive = true,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex)
    )
}

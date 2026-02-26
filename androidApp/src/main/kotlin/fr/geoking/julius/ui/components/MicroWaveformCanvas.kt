package fr.geoking.julius.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * Micro theme waveform: overlapping fluid waves in accent purple.
 */
@Composable
fun MicroWaveformCanvas(
    isActive: Boolean,
    palette: AnimationPalette,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micro_waveform")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val amplitude by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "amplitude"
    )

    val accentColor = Color(palette.primary)
    val secondaryColor = Color(palette.secondary)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val centerY = size.height / 2
        val barCount = 40
        val barWidth = size.width / (barCount + 1)
        val maxHeight = size.height * 0.4f * amplitude

        // Draw vertical bars (waveform bars)
        for (i in 0 until barCount) {
            val x = barWidth * (i + 1)
            val barHeight = (sin(phase1 + i * 0.3f) * 0.5f + 0.5f) * maxHeight
            val alpha = if (isActive) 0.6f + sin(phase2 + i * 0.2f) * 0.2f else 0.3f

            val barW = 4.dp.toPx()
            drawRect(
                color = accentColor.copy(alpha = alpha),
                topLeft = Offset(x - barW / 2, centerY - barHeight / 2),
                size = androidx.compose.ui.geometry.Size(barW, barHeight)
            )
        }

        // Overlay wave paths
        if (isActive) {
            drawMicroWave(
                phase = phase1,
                frequency = 0.02f,
                amplitude = maxHeight * 0.8f,
                color = accentColor.copy(alpha = 0.5f),
                centerY = centerY
            )
            drawMicroWave(
                phase = phase2,
                frequency = 0.015f,
                amplitude = maxHeight * 0.6f,
                color = secondaryColor.copy(alpha = 0.35f),
                centerY = centerY
            )
        }
    }
}

private fun DrawScope.drawMicroWave(
    phase: Float,
    frequency: Float,
    amplitude: Float,
    color: Color,
    centerY: Float
) {
    val path = Path()
    val segments = size.width.toInt()
    val step = size.width / segments

    path.moveTo(0f, centerY + sin(phase) * amplitude)
    for (i in 1..segments) {
        val x = i * step
        val y = centerY + sin(x * frequency + phase) * amplitude
        path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF21004C)
@Composable
private fun MicroWaveformCanvasPreview() {
    MicroWaveformCanvas(
        isActive = true,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.size - 1)
    )
}

package com.antigravity.voiceai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

@Composable
fun WavesEffectCanvas(
    isActive: Boolean,
    isLowQuality: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves_loop")
    
    // Wave animation phases
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )
    
    // Amplitude animation for active state
    val amplitudeAnim by animateFloatAsState(
        targetValue = if (isActive) 1.5f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "amplitude"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // Draw background gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F172A),
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                    Color(0xFF020617)
                ),
                startY = 0f,
                endY = size.height
            )
        )
        
        // Draw wave layers
        drawWaveLayer(
            phase = phase1,
            amplitude = 40f * amplitudeAnim,
            frequency = 0.015f,
            color = Color(0xFF6366F1),
            centerY = centerY * 0.3f,
            isActive = isActive,
            alpha = 0.6f
        )
        
        drawWaveLayer(
            phase = phase2,
            amplitude = 50f * amplitudeAnim,
            frequency = 0.012f,
            color = Color(0xFFEC4899),
            centerY = centerY * 0.6f,
            isActive = isActive,
            alpha = 0.5f
        )
        
        drawWaveLayer(
            phase = phase3,
            amplitude = 45f * amplitudeAnim,
            frequency = 0.010f,
            color = Color(0xFF8B5CF6),
            centerY = centerY * 0.9f,
            isActive = isActive,
            alpha = 0.4f
        )
        
        // Draw additional waves when active
        if (isActive && !isLowQuality) {
            drawWaveLayer(
                phase = phase1 * 1.3f,
                amplitude = 35f * amplitudeAnim,
                frequency = 0.018f,
                color = Color(0xFF06B6D4),
                centerY = centerY * 0.45f,
                isActive = true,
                alpha = 0.3f
            )
            
            drawWaveLayer(
                phase = phase2 * 0.8f,
                amplitude = 55f * amplitudeAnim,
                frequency = 0.008f,
                color = Color(0xFF10B981),
                centerY = centerY * 1.15f,
                isActive = true,
                alpha = 0.3f
            )
        }
        
        // Draw radial wave pattern from center
        if (!isLowQuality) {
            drawRadialWaves(
                center = Offset(centerX, centerY),
                phase = phase1,
                isActive = isActive
            )
        }
    }
}

private fun DrawScope.drawWaveLayer(
    phase: Float,
    amplitude: Float,
    frequency: Float,
    color: Color,
    centerY: Float,
    isActive: Boolean,
    alpha: Float
) {
    val path = Path()
    val waveColor = if (isActive) {
        // Interpolate between color and white (30% white)
        Color(
            red = (color.red * 0.7f + 1f * 0.3f).coerceIn(0f, 1f),
            green = (color.green * 0.7f + 1f * 0.3f).coerceIn(0f, 1f),
            blue = (color.blue * 0.7f + 1f * 0.3f).coerceIn(0f, 1f),
            alpha = alpha
        )
    } else {
        color.copy(alpha = alpha)
    }
    
    val segments = size.width.toInt()
    val step = size.width / segments
    
    // Start path
    path.moveTo(0f, centerY)
    
    // Create wave path
    for (i in 0..segments) {
        val x = i * step
        val y = centerY + sin(x * frequency + phase) * amplitude
        path.lineTo(x, y)
    }
    
    // Close path to create filled wave
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    
    // Draw filled wave
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf<Color>(
                waveColor,
                waveColor.copy(alpha = alpha * 0.5f),
                Color.Transparent
            ),
            startY = centerY - amplitude,
            endY = size.height
        )
    )
    
    // Draw wave outline
    val outlinePath = Path()
    outlinePath.moveTo(0f, centerY + sin(phase) * amplitude)
    for (i in 1..segments) {
        val x = i * step
        val y = centerY + sin(x * frequency + phase) * amplitude
        outlinePath.lineTo(x, y)
    }
    
    drawPath(
        path = outlinePath,
        color = waveColor.copy(alpha = (alpha + 0.2f).coerceIn(0f, 1f)),
        style = Stroke(width = 2f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawRadialWaves(
    center: Offset,
    phase: Float,
    isActive: Boolean
) {
    val maxRadius = size.width.coerceAtLeast(size.height) * 0.8f
    val waveCount = if (isActive) 8 else 5
    
    for (i in 0 until waveCount) {
        val radius = (maxRadius / waveCount) * (i + 1) + sin(phase + i) * 20f
        val alpha = (1f - (i / waveCount.toFloat())) * 0.15f
        
        val colors = listOf(
            Color(0xFF6366F1).copy(alpha = alpha),
            Color(0xFFEC4899).copy(alpha = alpha * 0.7f),
            Color.Transparent
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center,
            style = Stroke(
                width = if (isActive) 3f else 2f,
                cap = StrokeCap.Round
            )
        )
    }
}


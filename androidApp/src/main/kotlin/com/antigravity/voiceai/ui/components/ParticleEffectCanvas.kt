package com.antigravity.voiceai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import kotlin.math.*
import kotlin.random.Random

@Composable
fun ParticleEffectCanvas(
    isActive: Boolean, // e.g. when voice is growing
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Generate random particles once
    val particleCount = 20
    val particles = remember { List(particleCount) { RandomParticle() } }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // Draw deep gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
            )
        )

        particles.forEach { p ->
            val activeScale = if (isActive) 1.5f else 0.5f
            
            // Orbit logic
            val currentAngle = p.baseAngle + (time * 2 * PI * p.speed).toFloat()
            val radius = p.radius * (1 + 0.1f * sin(time * 5 * p.speed))
            
            val x = width / 2 + cos(currentAngle) * radius * width.toFloat() / 3
            val y = height / 2 + sin(currentAngle) * radius * height.toFloat() / 3

            drawCircle(
                color = p.color.copy(alpha = 0.6f),
                radius = p.size * activeScale,
                center = Offset(x.toFloat(), y.toFloat())
            )
        }
    }
}

data class RandomParticle(
    val baseAngle: Float = Random.nextFloat() * 6.28f,
    val speed: Float = 0.5f + Random.nextFloat(),
    val radius: Float = 0.3f + Random.nextFloat() * 0.4f,
    val size: Float = 20f + Random.nextFloat() * 40f,
    val color: Color = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4)  // Cyan
    ).random()
)

package fr.geoking.julius.ui.anim.phone

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
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.*
import kotlin.random.Random
import fr.geoking.julius.ui.anim.AnimationPalette

@Composable
fun ParticlesEffectCanvas(
    isActive: Boolean,
    palette: AnimationPalette,
    isLowQuality: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "particle_loop")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    // Pulse for "Vibes" / Active state
    val pulseAnim by animateFloatAsState(
        targetValue = if (isActive) 1.5f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    // Particles State
    val particles = remember { List(if (isLowQuality) 30 else 100) { Particle() } }
    val paletteColors = remember(palette) { palette.colors.map { Color(it) } }
    val rayColor = paletteColors.firstOrNull() ?: Color(0xFF6366F1)
    
    // Rays State
    val rays = remember { List(8) { Ray() } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // 1. Draw Background Vignette / Shadow (Simulates depth)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E1B4B), Color(0xFF020617)),
                center = Offset(centerX, centerY),
                radius = size.width.coerceAtLeast(size.height) * 0.8f
            )
        )

        // 2. Draw Rays (Lights)
        if (!isLowQuality) {
            withTransform({
                rotate(time * 360f, pivot = Offset(centerX, centerY))
                scale(scaleX = pulseAnim, scaleY = pulseAnim, pivot = Offset(centerX, centerY))
            }) {
                rays.forEach { ray ->
                    drawRay(ray, centerX, centerY, isActive, rayColor)
                }
            }
        }

        // 3. Draw Particles (Core + Aura)
        particles.forEach { p ->
            p.update(isActive, time, paletteColors)
            val x = centerX + p.x * (if (isActive) 1.2f else 1f)
            val y = centerY + p.y * (if (isActive) 1.2f else 1f)
            
            // Vibes/Aura (Glow)
            if (!isLowQuality && isActive) {
                drawCircle(
                    color = p.color.copy(alpha = 0.1f * p.alpha),
                    radius = p.size * 4f * pulseAnim,
                    center = Offset(x, y)
                )
            }
            
            // Core Particle
            drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.size * (if (isActive) 1.5f else 1f),
                center = Offset(x, y)
            )
        }
    }
}

// --- Data Classes & Helpers ---

private class Particle {
    var angle = Random.nextFloat() * 2 * PI.toFloat()
    var radius = Random.nextFloat() * 300f + 50f
    var speed = Random.nextFloat() * 2f + 0.5f
    var size = Random.nextFloat() * 4f + 2f
    var alpha = Random.nextFloat() * 0.5f + 0.3f
    private val colorIndex = Random.nextInt()
    
    var color = Color.White
    var x = 0f
    var y = 0f

    fun update(isActive: Boolean, time: Float, paletteColors: List<Color>) {
        val baseColor = if (paletteColors.isNotEmpty()) {
            paletteColors[kotlin.math.abs(colorIndex) % paletteColors.size]
        } else {
            Color(0xFF6366F1)
        }
        val activeSpeedMultiplier = if (isActive) 3f else 1f
        angle += (speed * 0.01f * activeSpeedMultiplier)
        
        // Wobbly radius movement simulating "vibes"
        val wobble = sin(time * 50f + size) * (if (isActive) 20f else 5f)
        val currentRadius = radius + wobble
        
        x = cos(angle) * currentRadius
        y = sin(angle) * currentRadius
        
        color = if (isActive) Color.White else baseColor
    }
}

private class Ray {
    val angleOffset = Random.nextFloat() * 360f
    val width = Random.nextFloat() * 30f + 10f
    val lengthScale = Random.nextFloat() * 0.5f + 0.5f
}

private fun DrawScope.drawRay(
    ray: Ray,
    cx: Float,
    cy: Float,
    isActive: Boolean,
    baseColor: Color
) {
    val color = baseColor.copy(alpha = if (isActive) 0.15f else 0.05f)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(color, Color.Transparent),
            start = Offset(cx, cy),
            end = Offset(cx, 0f) // Simplified ray direction
        ),
        topLeft = Offset(cx - ray.width / 2, 0f), // Rough approximation for rotation effect
        size = Size(ray.width, size.height), // simplified
        alpha = 0.5f
        // Note: Real ray rotation handled by canvas transform
    )
    // Refined: We want radial rays from center.
    // Actually better to draw lines with width cap
    drawLine(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(cx, cy),
            radius = size.width
        ),
        start = Offset(cx, cy),
        end = Offset(
            cx + cos(ray.angleOffset) * size.width,
            cy + sin(ray.angleOffset) * size.width
        ),
        strokeWidth = ray.width * (if (isActive) 2f else 1f),
        cap = StrokeCap.Round
    )
}

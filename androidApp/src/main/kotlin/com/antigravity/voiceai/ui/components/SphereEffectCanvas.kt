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
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.*

@Composable
fun SphereEffectCanvas(
    isActive: Boolean,
    isLowQuality: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sphere_loop")
    
    // Rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Pulsing animation for active state
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Scale animation for active state
    val scaleAnim by animateFloatAsState(
        targetValue = if (isActive) 1.3f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.width.coerceAtMost(size.height) * 0.25f
        
        // Draw background gradient
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                    Color(0xFF020617)
                ),
                center = Offset(centerX, centerY),
                radius = size.width.coerceAtLeast(size.height) * 0.8f
            )
        )
        
        // Main sphere radius with pulse effect
        val baseRadius = maxRadius * scaleAnim
        val activeRadius = if (isActive) {
            baseRadius * (1f + pulse * 0.15f)
        } else {
            baseRadius
        }
        
        // Draw sphere with 3D lighting effect
        drawSphere(
            center = Offset(centerX, centerY),
            radius = activeRadius,
            rotation = rotation,
            isActive = isActive,
            pulse = pulse
        )
        
        // Draw orbiting rings for depth
        if (!isLowQuality) {
            drawOrbitingRings(
                center = Offset(centerX, centerY),
                baseRadius = activeRadius,
                rotation = rotation,
                isActive = isActive,
                count = 3
            )
        }
        
        // Draw glow effect when active
        if (isActive && !isLowQuality) {
            val glowRadius = activeRadius * (1.5f + pulse * 0.3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.3f * pulse),
                        Color(0xFFEC4899).copy(alpha = 0.1f * pulse),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(centerX, centerY)
            )
        }
    }
}

private fun DrawScope.drawSphere(
    center: Offset,
    radius: Float,
    rotation: Float,
    isActive: Boolean,
    pulse: Float
) {
    // Number of circles to create 3D sphere effect
    val circles = if (isActive) 12 else 8
    val primaryColor = if (isActive) Color.White else Color(0xFF6366F1)
    val secondaryColor = if (isActive) Color(0xFFEC4899) else Color(0xFF8B5CF6)
    
    for (i in 0 until circles) {
        val angle = (i * 360f / circles + rotation) * PI.toFloat() / 180f
        val z = cos(angle) // Depth position (-1 to 1)
        val ellipseRadius = radius * abs(z)
        val ellipseY = center.y + sin(angle) * radius * 0.3f
        
        // Alpha based on depth
        val alpha = (0.3f + abs(z) * 0.7f) * (if (isActive) (0.8f + pulse * 0.2f) else 0.6f)
        
        // Color interpolation based on depth and pulse
        val color = when {
            isActive -> {
                Color.lerp(
                    primaryColor.copy(alpha = alpha),
                    secondaryColor.copy(alpha = alpha),
                    abs(z) * 0.5f + pulse * 0.3f
                ) ?: primaryColor.copy(alpha = alpha)
            }
            else -> primaryColor.copy(alpha = alpha)
        }
        
        // Draw ellipse to create sphere illusion
        val ellipseWidth = ellipseRadius * 2
        val ellipseHeight = ellipseRadius * 0.6f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    color,
                    color.copy(alpha = alpha * 0.3f),
                    Color.Transparent
                ),
                center = Offset(center.x, ellipseY),
                radius = ellipseRadius
            ),
            topLeft = Offset(center.x - ellipseRadius, ellipseY - ellipseHeight / 2),
            size = Size(ellipseWidth, ellipseHeight)
        )
    }
    
    // Draw main circle with gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = if (isActive) 0.6f else 0.4f),
                secondaryColor.copy(alpha = if (isActive) 0.3f else 0.2f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawOrbitingRings(
    center: Offset,
    baseRadius: Float,
    rotation: Float,
    isActive: Boolean,
    count: Int
) {
    val colors = listOf(
        Color(0xFF6366F1),
        Color(0xFFEC4899),
        Color(0xFF8B5CF6)
    )
    
    for (i in 0 until count) {
        val ringRadius = baseRadius * (1.3f + i * 0.4f)
        val ringRotation = (rotation * (if (i % 2 == 0) 1f else -1f) + i * 45f) * PI.toFloat() / 180f
        val alpha = if (isActive) 0.4f else 0.2f
        
        // Draw elliptical ring
        val ringPath = Path().apply {
            val segments = 64
            for (j in 0..segments) {
                val angle = (j * 360f / segments + ringRotation) * PI.toFloat() / 180f
                val x = center.x + cos(angle) * ringRadius
                val y = center.y + sin(angle) * ringRadius * 0.6f // Ellipse aspect ratio
                
                if (j == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
            close()
        }
        
        drawPath(
            path = ringPath,
            color = colors[i % colors.size].copy(alpha = alpha),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}


package fr.geoking.julius.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import kotlin.math.*

/**
 * Animated fractal background (Mandelbrot set) with a slow infinite zoom-in effect.
 * Zoom center is at an interesting point on the set boundary for continuous detail.
 */
@Composable
fun FractalEffectCanvas(
    isActive: Boolean,
    isLowQuality: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fractal_zoom")
    // Slow infinite zoom: from 1 to 80 over 50 seconds, then restart
    val zoom by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "zoom"
    )
    // Optional phase for smooth color cycling
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val brightness by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "brightness"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerX = w / 2
        val centerY = h / 2
        // Zoom into a point on the boundary for endless detail (e.g. "valley" near 0.25, 0)
        val cx = 0.25f
        val cy = 0f
        val gridSize = if (isLowQuality) 48 else 72
        val cellW = w / gridSize
        val cellH = h / gridSize
        // Visible range in complex plane: smaller as zoom increases
        val halfSpan = 2f / zoom

        // Dark background
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E1B4B),
                    Color(0xFF0F172A),
                    Color(0xFF020617)
                ),
                center = Offset(centerX, centerY),
                radius = maxOf(w, h) * 0.8f
            )
        )

        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                // Pixel center in screen space
                val sx = (ix + 0.5f) * cellW
                val sy = (iy + 0.5f) * cellH
                // Map to complex plane (y flipped for display)
                val re = cx - halfSpan + (sx / w) * (2f * halfSpan)
                val im = cy - halfSpan + (1f - sy / h) * (2f * halfSpan)
                val (escaped, smooth) = mandelbrotSmooth(re, im, if (isLowQuality) 64 else 128)
                val color = fractalColor(escaped, smooth, phase, brightness, isActive)
                drawRect(
                    color = color,
                    topLeft = Offset(ix * cellW, iy * cellH),
                    size = Size(cellW + 1f, cellH + 1f) // slight overlap to avoid gaps
                )
            }
        }
    }
}

/**
 * Mandelbrot iteration with smooth escape (continuous coloring).
 * Returns (iterations, smooth) where smooth is in [0,1] for band between escape and previous.
 */
private fun mandelbrotSmooth(
    cr: Float,
    ci: Float,
    maxIter: Int
): Pair<Int, Float> {
    var zr = 0.0
    var zi = 0.0
    var n = 0
    while (n < maxIter) {
        val zr2 = zr * zr
        val zi2 = zi * zi
        if (zr2 + zi2 > 4.0) {
            // Smooth iteration count for banding-free color
            val logZn = ln(zr2 + zi2) / 2.0
            val nu = ln(logZn / ln(2.0)) / ln(2.0)
            val smooth = (n + 1 - nu).toFloat().coerceIn(0f, 1f)
            return n to smooth
        }
        val newZi = 2 * zr * zi + ci
        val newZr = zr2 - zi2 + cr
        zi = newZi
        zr = newZr
        n++
    }
    return maxIter to 0f
}

private fun fractalColor(
    iterations: Int,
    smooth: Float,
    phase: Float,
    brightness: Float,
    isActive: Boolean
): Color {
    val maxIter = 128
    if (iterations >= maxIter) {
        return Color(0xFF020617) // Inside: dark
    }
    val t = (iterations + smooth) / maxIter.toFloat()
    // Palette: cycle through indigo, pink, violet, cyan
    val palette = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFF8B5CF6), // Violet
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4)  // Cyan
    )
    val idx = ((t * 3f + phase) % 1f) * palette.size
    val i0 = (idx.toInt() % palette.size).coerceIn(0, palette.size - 1)
    val i1 = ((idx.toInt() + 1) % palette.size).coerceIn(0, palette.size - 1)
    val frac = idx - idx.toInt()
    val c0 = palette[i0]
    val c1 = palette[i1]
    val r = (c0.red * (1 - frac) + c1.red * frac).coerceIn(0f, 1f)
    val g = (c0.green * (1 - frac) + c1.green * frac).coerceIn(0f, 1f)
    val b = (c0.blue * (1 - frac) + c1.blue * frac).coerceIn(0f, 1f)
    val alpha = (0.4f + 0.5f * t) * brightness
    return Color(
        red = (r * brightness).coerceIn(0f, 1f),
        green = (g * brightness).coerceIn(0f, 1f),
        blue = (b * brightness).coerceIn(0f, 1f),
        alpha = if (isActive) (alpha * 1.1f).coerceIn(0f, 1f) else alpha
    )
}

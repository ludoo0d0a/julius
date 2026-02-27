package fr.geoking.julius.ui.anim.phone

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import fr.geoking.julius.FractalColorIntensity
import fr.geoking.julius.FractalQuality
import kotlin.math.*
import fr.geoking.julius.ui.anim.AnimationPalette

enum class FractalType { Mandelbrot, Julia, BurningShip, Tricorn }

/**
 * Animated fractal background with a slow infinite zoom-in effect.
 * Cycles through different fractal types.
 */
@Composable
fun FractalEffectCanvas(
    isActive: Boolean,
    palette: AnimationPalette,
    quality: FractalQuality = FractalQuality.Medium,
    colorIntensity: FractalColorIntensity = FractalColorIntensity.Medium
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fractal_zoom")
    // Slow infinite zoom: from 1 to 80 over 50 seconds, then restart
    val zoomProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "zoomProgress"
    )
    val zoom = 1f + zoomProgress * 79f

    // Track zoom cycles to change fractal type
    var zoomCycleCount by remember { mutableStateOf(0) }
    var lastZoomProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(zoomProgress) {
        if (zoomProgress < lastZoomProgress) {
            zoomCycleCount++
        }
        lastZoomProgress = zoomProgress
    }

    val fractalType = remember(zoomCycleCount) {
        FractalType.entries[zoomCycleCount % FractalType.entries.size]
    }

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

    val paletteColors = remember(palette) { palette.colors.map { Color(it) } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerX = w / 2
        val centerY = h / 2

        // Different zoom targets for different fractals
        val (cx, cy) = when (fractalType) {
            FractalType.Mandelbrot -> 0.25f to 0.0f
            FractalType.Julia -> 0.0f to 0.0f // Classic Julia set usually centered
            FractalType.BurningShip -> -1.75f to -0.02f
            FractalType.Tricorn -> -0.25f to 0.0f
        }

        val gridSize = when (quality) {
            FractalQuality.Low -> 48
            FractalQuality.Medium -> 72
            FractalQuality.High -> 96
        }
        val maxIter = when (quality) {
            FractalQuality.Low -> 64
            FractalQuality.Medium -> 128
            FractalQuality.High -> 256
        }

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

                val (escaped, smooth) = when (fractalType) {
                    FractalType.Mandelbrot -> mandelbrotSmooth(re, im, maxIter)
                    FractalType.Julia -> juliaSmooth(re, im, maxIter)
                    FractalType.BurningShip -> burningShipSmooth(re, im, maxIter)
                    FractalType.Tricorn -> tricornSmooth(re, im, maxIter)
                }

                val color = fractalColor(escaped, smooth, phase, brightness, isActive, paletteColors, colorIntensity, maxIter)
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
 * Mandelbrot iteration with smooth escape.
 */
private fun mandelbrotSmooth(cr: Float, ci: Float, maxIter: Int): Pair<Int, Float> {
    var zr = 0.0
    var zi = 0.0
    var n = 0
    while (n < maxIter) {
        val zr2 = zr * zr
        val zi2 = zi * zi
        if (zr2 + zi2 > 4.0) {
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

private fun juliaSmooth(zrStart: Float, ziStart: Float, maxIter: Int): Pair<Int, Float> {
    var zr = zrStart.toDouble()
    var zi = ziStart.toDouble()
    val cr = -0.7
    val ci = 0.27015
    var n = 0
    while (n < maxIter) {
        val zr2 = zr * zr
        val zi2 = zi * zi
        if (zr2 + zi2 > 4.0) {
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


/**
 * Burning Ship fractal: take absolute value of components each iteration.
 */
private fun burningShipSmooth(cr: Float, ci: Float, maxIter: Int): Pair<Int, Float> {
    var zr = 0.0
    var zi = 0.0
    var n = 0
    while (n < maxIter) {
        val zr2 = zr * zr
        val zi2 = zi * zi
        if (zr2 + zi2 > 4.0) {
            val logZn = ln(zr2 + zi2) / 2.0
            val nu = ln(logZn / ln(2.0)) / ln(2.0)
            val smooth = (n + 1 - nu).toFloat().coerceIn(0f, 1f)
            return n to smooth
        }
        // Burning Ship: z = (|Re(z)| + i|Im(z)|)^2 + c
        val newZr = zr2 - zi2 + cr
        val newZi = abs(2.0 * zr * zi) + ci
        zr = abs(newZr)
        zi = newZi
        n++
    }
    return maxIter to 0f
}

/**
 * Tricorn fractal (Mandelbar): z = conj(z)^2 + c
 */
private fun tricornSmooth(cr: Float, ci: Float, maxIter: Int): Pair<Int, Float> {
    var zr = 0.0
    var zi = 0.0
    var n = 0
    while (n < maxIter) {
        val zr2 = zr * zr
        val zi2 = zi * zi
        if (zr2 + zi2 > 4.0) {
            val logZn = ln(zr2 + zi2) / 2.0
            val nu = ln(logZn / ln(2.0)) / ln(2.0)
            val smooth = (n + 1 - nu).toFloat().coerceIn(0f, 1f)
            return n to smooth
        }
        // conj(z) = (zr, -zi)
        // (zr - i zi)^2 = zr^2 - zi^2 - 2 i zr zi
        val nextZr = zr2 - zi2 + cr
        val nextZi = -2.0 * zr * zi + ci
        zr = nextZr
        zi = nextZi
        n++
    }
    return maxIter to 0f
}

private fun fractalColor(
    iterations: Int,
    smooth: Float,
    phase: Float,
    brightness: Float,
    isActive: Boolean,
    paletteColors: List<Color>,
    intensity: FractalColorIntensity,
    maxIter: Int
): Color {
    if (iterations >= maxIter) {
        return Color(0xFF020617) // Inside: dark
    }
    val t = (iterations + smooth) / maxIter.toFloat()
    val safePalette = if (paletteColors.isNotEmpty()) paletteColors else listOf(Color(0xFF6366F1))

    val multiplier = when (intensity) {
        FractalColorIntensity.Low -> 1.5f
        FractalColorIntensity.Medium -> 3f
        FractalColorIntensity.High -> 6f
    }

    val idx = ((t * multiplier + phase) % 1f) * safePalette.size
    val i0 = (idx.toInt() % safePalette.size).coerceIn(0, safePalette.size - 1)
    val i1 = ((idx.toInt() + 1) % safePalette.size).coerceIn(0, safePalette.size - 1)
    val frac = idx - idx.toInt()
    val c0 = safePalette[i0]
    val c1 = safePalette[i1]
    val r = (c0.red * (1 - frac) + c1.red * frac).coerceIn(0f, 1f)
    val g = (c0.green * (1 - frac) + c1.green * frac).coerceIn(0f, 1f)
    val b = (c0.blue * (1 - frac) + c1.blue * frac).coerceIn(0f, 1f)

    val alphaBase = when (intensity) {
        FractalColorIntensity.Low -> 0.3f + 0.3f * t
        FractalColorIntensity.Medium -> 0.4f + 0.5f * t
        FractalColorIntensity.High -> 0.5f + 0.5f * t
    }
    val alpha = alphaBase * brightness

    return Color(
        red = (r * brightness).coerceIn(0f, 1f),
        green = (g * brightness).coerceIn(0f, 1f),
        blue = (b * brightness).coerceIn(0f, 1f),
        alpha = if (isActive) (alpha * 1.1f).coerceIn(0f, 1f) else alpha
    )
}

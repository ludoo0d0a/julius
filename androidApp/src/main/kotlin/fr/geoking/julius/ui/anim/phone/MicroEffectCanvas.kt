package fr.geoking.julius.ui.anim.phone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes

/**
 * Micro theme background: deep purple with subtle radial vignette.
 * Minimal, clean aesthetic matching the screenshot design.
 */
@Composable
fun MicroEffectCanvas(
    isActive: Boolean,
    palette: AnimationPalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.width.coerceAtLeast(size.height) * 0.9f

        // Deep purple base with subtle center glow when active
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    if (isActive) Color(0xFF2C005F) else Color(0xFF21004C),
                    Color(0xFF1A0038),
                    Color(0xFF0D001A)
                ),
                center = Offset(centerX, centerY),
                radius = maxRadius
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MicroEffectCanvasIdlePreview() {
    MicroEffectCanvas(
        isActive = false,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.size - 1)
    )
}

@Preview(showBackground = true)
@Composable
private fun MicroEffectCanvasActivePreview() {
    MicroEffectCanvas(
        isActive = true,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.size - 1)
    )
}

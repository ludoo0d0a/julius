package fr.geoking.julius.ui.anim.phone

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TrayLightEffectCanvas(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tray")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .blur(radius = 32.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF6366F1).copy(alpha = if (isActive) alpha else 0.2f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp)
            )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun TrayLightEffectCanvasIdlePreview() {
    TrayLightEffectCanvas(isActive = false)
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun TrayLightEffectCanvasActivePreview() {
    TrayLightEffectCanvas(isActive = true)
}


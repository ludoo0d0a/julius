package fr.geoking.julius.designassistant

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Jarvis / Iron Man inspired palette with neon glows and high-tech dark theme. */
object DesignAssistantJarvisColors {
    val CyanNeon = Color(0xFF00E5FF)
    val CyanGlow = Color(0x3300E5FF)
    val OrangeNeon = Color(0xFFFF6D00)
    val GoldNeon = Color(0xFFFFD600)

    val Background = Color(0xFF00050A)
    val SurfaceDark = Color(0xFF0A1929)
    val SurfaceLight = Color(0xFF132F4C)

    val TextPrimary = Color(0xFFE3F2FD)
    val TextSecondary = Color(0xFFB0BEC5)
    val TextCyan = Color(0xFF00E5FF)

    val StatusActive = Color(0xFF00E5FF)
    val StatusWarning = Color(0xFFFFD600)
    val StatusError = Color(0xFFFF1744)
    val StatusSuccess = Color(0xFF00E676)

    val MaterialTheme = darkColorScheme(
        primary = CyanNeon,
        onPrimary = Background,
        secondary = OrangeNeon,
        onSecondary = Background,
        tertiary = GoldNeon,
        background = Background,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
    )
}

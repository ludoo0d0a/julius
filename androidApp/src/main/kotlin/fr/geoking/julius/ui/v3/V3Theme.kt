package fr.geoking.julius.ui.v3

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Console-operator dark palette for the v3 redesign. Mirrors the HTML
 * prototype (prototype/app.html): slate base, single signal-cyan accent,
 * semantic status colors (merge / quota / conflict).
 */
object V3 {
    val Bg = Color(0xFF0E1626)
    val Surface = Color(0xFF172234)
    val SurfaceHi = Color(0xFF1D2A40)
    val Fg = Color(0xFFEEF2F8)
    val Muted = Color(0xFF8B9BB4)
    val Faint = Color(0xFF5D6B82)
    val Border = Color(0x18FFFFFF)
    val Accent = Color(0xFF34E0D0)
    val AccentInk = Color(0xFF04201D)
    val Success = Color(0xFF46D18A)
    val Warn = Color(0xFFF5C469)
    val Danger = Color(0xFFFB7185)
}

private val V3ColorScheme = darkColorScheme(
    primary = V3.Accent,
    onPrimary = V3.AccentInk,
    primaryContainer = V3.SurfaceHi,
    onPrimaryContainer = V3.Accent,
    background = V3.Bg,
    onBackground = V3.Fg,
    surface = V3.Surface,
    onSurface = V3.Fg,
    surfaceVariant = V3.SurfaceHi,
    onSurfaceVariant = V3.Muted,
    secondaryContainer = V3.SurfaceHi,
    onSecondaryContainer = V3.Accent,
    outline = Color(0x26FFFFFF),
    outlineVariant = V3.Border,
    error = V3.Danger,
    onError = Color(0xFF2A0F14),
)

@Composable
fun JuliusV3Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = V3ColorScheme, content = content)
}

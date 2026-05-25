package fr.geoking.julius.designassistant

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Navy / white palette aligned with Jules Design Assistant mockup (#1A237E). */
object DesignAssistantColors {
    val Navy = Color(0xFF1A237E)
    val NavyDark = Color(0xFF0D1642)
    val NavyLight = Color(0xFF283593)
    val White = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF5F7FF)
    val SurfaceCard = Color(0xFFFFFFFF)
    val TextOnNavy = Color.White
    val TextPrimary = Color(0xFF1A237E)
    val TextSecondary = Color(0xFF5C6BC0)
    val Accent = Color(0xFF3949AB)
    val StatusDone = Color(0xFF43A047)
    val StatusInProgress = Color(0xFFFFB300)
    val StatusTodo = Color(0xFFB0BEC5)
    val StatusReady = Color(0xFF26A69A)
    val CodeBlockBg = Color(0xFF263238)
    val UserBubble = Color(0xFFE8EAF6)
    val CiSuccess = Color(0xFF2E7D32)

    val MaterialTheme = darkColorScheme(
        primary = Navy,
        onPrimary = White,
        secondary = Accent,
        background = NavyDark,
        surface = Surface,
        onSurface = TextPrimary,
    )
}

package fr.geoking.julius.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fr.geoking.julius.AgentType
import fr.geoking.julius.AppSettings
import fr.geoking.julius.AppTheme
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes
import fr.geoking.julius.ui.components.ThemeBackground
import fr.geoking.julius.ui.components.VoiceMainContent
import kotlin.math.abs

/**
 * Main phone screen with swipe gestures (vertical: palette, horizontal: theme)
 * and theme background.
 */
@Composable
fun PhoneMainScreen(
    state: ConversationState,
    settings: AppSettings,
    palette: AnimationPalette,
    settingsManager: SettingsManager,
    store: ConversationStore,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onMapClick: () -> Unit,
    onJulesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSettings = rememberUpdatedState(settings)
    val currentTheme = rememberUpdatedState(settings.selectedTheme)
    val verticalThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val horizontalThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .paletteSwipeModifier(verticalThresholdPx)
            .themeSwipeModifier(
                currentTheme = currentTheme.value,
                currentSettings = currentSettings.value,
                settingsManager = settingsManager,
                horizontalThresholdPx = horizontalThresholdPx
            ),
        contentAlignment = Alignment.Center
    ) {
        key(settings.selectedTheme) {
            ThemeBackground(
                theme = settings.selectedTheme,
                status = state.status,
                palette = palette,
                settings = settings
            )
        }
        VoiceMainContent(
            state = state,
            settings = settings,
            palette = palette,
            store = store,
            onSettingsClick = onSettingsClick,
            onHistoryClick = onHistoryClick,
            onMapClick = onMapClick,
            onJulesClick = onJulesClick,
            onAgentClick = {
                val agents = AgentType.entries
                val currentIndex = agents.indexOf(settings.selectedAgent).coerceAtLeast(0)
                val nextIndex = (currentIndex + 1) % agents.size
                val nextAgent = agents[nextIndex]
                settingsManager.saveSettings(settings.copy(selectedAgent = nextAgent))
            }
        )
    }
}

private fun Modifier.paletteSwipeModifier(thresholdPx: Float) = pointerInput(thresholdPx) {
    var accumulated = 0f
    detectVerticalDragGestures(
        onDragEnd = { accumulated = 0f },
        onDragCancel = { accumulated = 0f },
        onVerticalDrag = { _, dragAmount ->
            accumulated += dragAmount
            if (accumulated <= -thresholdPx) {
                AnimationPalettes.step(1)
                accumulated = 0f
            } else if (accumulated >= thresholdPx) {
                AnimationPalettes.step(-1)
                accumulated = 0f
            }
        }
    )
}

private fun Modifier.themeSwipeModifier(
    currentTheme: AppTheme,
    currentSettings: AppSettings,
    settingsManager: SettingsManager,
    horizontalThresholdPx: Float
) = pointerInput(currentTheme, currentSettings, horizontalThresholdPx) {
    var dragAmount = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragAmount = 0f },
        onHorizontalDrag = { change, dragAmountX ->
            dragAmount += dragAmountX
            change.consume()
        },
        onDragEnd = {
            if (abs(dragAmount) >= horizontalThresholdPx) {
                val themes = AppTheme.entries
                val currentIndex = themes.indexOf(currentTheme).coerceAtLeast(0)
                val nextIndex = if (dragAmount < 0f) {
                    (currentIndex + 1) % themes.size
                } else {
                    (currentIndex - 1 + themes.size) % themes.size
                }
                val nextTheme = themes[nextIndex]
                if (nextTheme != currentTheme) {
                    settingsManager.saveSettings(currentSettings.copy(selectedTheme = nextTheme))
                }
            }
            dragAmount = 0f
        },
        onDragCancel = { dragAmount = 0f }
    )
}

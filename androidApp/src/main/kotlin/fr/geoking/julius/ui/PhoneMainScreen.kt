package fr.geoking.julius.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import fr.geoking.julius.AppSettings
import fr.geoking.julius.nextSelectableAgent
import fr.geoking.julius.AppTheme
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.NetworkStatus
import fr.geoking.julius.ui.AgentSetupIssue
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
    networkStatus: NetworkStatus,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onMapClick: () -> Unit,
    onJulesClick: () -> Unit = {},
    setupIssue: AgentSetupIssue? = null,
    onOpenAgentSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentSettings = rememberUpdatedState(settings)
    val currentTheme = rememberUpdatedState(settings.selectedTheme)
    val verticalThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    val horizontalThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var showEffectBackground by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        showEffectBackground = true
    }

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
        if (showEffectBackground) {
            key(settings.selectedTheme) {
                ThemeBackground(
                    theme = settings.selectedTheme,
                    status = state.status,
                    palette = palette,
                    settings = settings
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF0F172A)))
        }
        VoiceMainContent(
            state = state,
            settings = settings,
            palette = palette,
            store = store,
            networkStatus = networkStatus,
            onSettingsClick = onSettingsClick,
            onHistoryClick = onHistoryClick,
            onMapClick = onMapClick,
            onJulesClick = onJulesClick,
            setupIssue = setupIssue,
            onOpenAgentSettings = onOpenAgentSettings,
            onAgentClick = {
                val nextAgent = nextSelectableAgent(settings.selectedAgent)
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

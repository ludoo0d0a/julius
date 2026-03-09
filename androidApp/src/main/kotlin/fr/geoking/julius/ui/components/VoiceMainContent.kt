package fr.geoking.julius.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.geoking.julius.AppSettings
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.ConversationState
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.phone.TrayLightEffectCanvas
import fr.geoking.julius.ui.components.JulesButton

/**
 * Main voice UI: status content, tray effect, waveform, mic button, settings.
 */
@Composable
fun VoiceMainContent(
    state: ConversationState,
    settings: AppSettings,
    palette: AnimationPalette,
    store: ConversationStore,
    onSettingsClick: () -> Unit,
    onMapClick: () -> Unit,
    onJulesClick: () -> Unit = {},
    onAgentClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayText by remember {
        derivedStateOf {
            when {
                state.currentTranscript.isNotBlank() -> state.currentTranscript
                state.status == VoiceEvent.Listening -> "Listening..."
                else -> state.messages.lastOrNull()?.text ?: "Hi, how can I help?"
            }
        }
    }

    Box(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
    ) {
        VoiceStatusContent(
            agentName = settings.selectedAgent.name,
            status = state.status,
            displayText = displayText,
            lastError = state.lastError,
            textAnimation = settings.textAnimation,
            onAgentClick = onAgentClick,
            modifier = Modifier.align(Alignment.Center)
        )
        TrayLightEffectCanvas(
            isActive = state.status == VoiceEvent.Listening,
            palette = palette,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MicroWaveformCanvas(
                isActive = state.status == VoiceEvent.Listening || state.status == VoiceEvent.Speaking,
                palette = palette,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            StatusChip(status = state.status)
            Spacer(modifier = Modifier.height(8.dp))
            MicroMicButton(
                status = state.status,
                accentColor = Color(palette.primary),
                onClick = {
                    when (state.status) {
                        VoiceEvent.Speaking -> store.stopSpeaking()
                        VoiceEvent.Listening -> store.stopListening()
                        else -> store.startListening()
                    }
                }
            )
        }
        SettingsButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 48.dp)
        )
        JulesButton(
            onClick = onJulesClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 72.dp, bottom = 48.dp)
        )
        MapButton(
            onClick = onMapClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 48.dp)
        )
    }
}

package fr.geoking.julius.ui

import androidx.compose.runtime.Composable
import fr.geoking.julius.AppSettings
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.conversation.ConversationState
import fr.geoking.julius.shared.network.NetworkStatus
import fr.geoking.julius.ui.AgentSetupIssue
import fr.geoking.julius.ui.anim.AnimationPalette

/**
 * Home screen focused on voice + LLM experience (no POI/fuel UI entry points).
 */
@Composable
fun DashboardVoiceScreen(
    state: ConversationState,
    settings: AppSettings,
    palette: AnimationPalette,
    settingsManager: SettingsManager,
    store: ConversationStore,
    networkStatus: NetworkStatus,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onJulesClick: () -> Unit = {},
    setupIssue: AgentSetupIssue? = null,
    onOpenAgentSettings: (() -> Unit)? = null,
) {
    PhoneMainScreen(
        state = state,
        settings = settings,
        palette = palette,
        settingsManager = settingsManager,
        store = store,
        networkStatus = networkStatus,
        onSettingsClick = onSettingsClick,
        onHistoryClick = onHistoryClick,
        onMapClick = null,
        onJulesClick = onJulesClick,
        setupIssue = setupIssue,
        onOpenAgentSettings = onOpenAgentSettings,
        modifier = androidx.compose.ui.Modifier
    )
}


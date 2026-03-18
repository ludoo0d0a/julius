package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.Header
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.AgentType
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.shared.Role
import fr.geoking.julius.shared.toHistoryScreenState
import fr.geoking.julius.shared.VoiceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MainScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: fr.geoking.julius.api.jules.JulesClient,
    private val getMapDeps: () -> MapDeps
) : Screen(carContext) {

    private var currentStatus: String = "Idle"
    private var currentText: String = "Tap mic to start"
    private var lastError: DetailedError? = null
    private var isListening: Boolean = false
    private var isSpeaking: Boolean = false
    private var lastProcessedMessageId: String? = null
    private var activeTabId: String = TAB_ASSISTANT

    private var cachedAgent: AgentType? = null
    private var dynamicIdleIcon: CarIcon? = null
    private var dynamicActiveIcon: CarIcon? = null

    /** When true, use MessageTemplate (fallback) instead of NavigationTemplate. */
    private var useFallback: Boolean = true
    /** True once we have received a surface (so we don't fall back unnecessarily). */
    private var surfaceReceived: Boolean = false
    private var fallbackCheckJob: Job? = null
    private var surfaceRenderer: AutoSurfaceRenderer? = null

    private val appManager: AppManager
        get() = carContext.getCarService(AppManager::class.java)

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surfaceReceived = true
            fallbackCheckJob?.cancel()
            fallbackCheckJob = null
            surfaceRenderer?.stop()
            val surface = surfaceContainer.surface ?: return
            val w = surfaceContainer.width.coerceAtLeast(1)
            val h = surfaceContainer.height.coerceAtLeast(1)
            surfaceRenderer = AutoSurfaceRenderer(surface, w, h).apply {
                isActive = isListening
                start()
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            surfaceRenderer?.stop()
            surfaceRenderer = null
            surfaceContainer.surface?.release()
            surfaceReceived = false
        }
    }

    init {
        lifecycleScope.launch {
            settingsManager.settings.collectLatest { settings ->
                if (settings.selectedAgent != cachedAgent) {
                    cachedAgent = settings.selectedAgent
                    refreshDynamicIcons()
                }
            }
        }

        lifecycleScope.launch {
            store.state.collectLatest { state ->
                val wasListening = isListening
                val wasSpeaking = isSpeaking

                isListening = state.status == VoiceEvent.Listening
                isSpeaking = state.status == VoiceEvent.Speaking
                surfaceRenderer?.isActive = isListening
                currentStatus = state.status.name
                lastError = state.lastError
                currentText = when {
                    state.lastError != null -> state.lastError!!.message
                    state.currentTranscript.isNotBlank() -> state.currentTranscript
                    else -> {
                        val userName = settingsManager.settings.value.googleUserName
                        val defaultGreeting = if (userName != null) "Hello $userName, how can I help?" else "Hi, how can I help you"
                        state.messages.lastOrNull()?.text ?: defaultGreeting
                    }
                }

                // Voice keyword triggers
                val lastMsg = state.messages.lastOrNull()
                if (state.status == VoiceEvent.Silence && lastMsg?.sender == Role.User && lastMsg.id != lastProcessedMessageId) {
                    lastProcessedMessageId = lastMsg.id
                    val lastUserMsg = lastMsg.text.lowercase()
                    val keywords = listOf("display map", "map", "carte", "gas stations", "stations service")
                    if (keywords.any { lastUserMsg.contains(it) }) {
                        val mapDeps = getMapDeps()
                        screenManager.push(
                            MapPoiScreen(
                                carContext = carContext,
                                poiProvider = mapDeps.poiProvider,
                                availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                                settingsManager = settingsManager,
                                routePlanner = mapDeps.routePlanner,
                                routingClient = mapDeps.routingClient,
                                tollCalculator = mapDeps.tollCalculator,
                                trafficProviderFactory = mapDeps.trafficProviderFactory,
                                geocodingClient = mapDeps.geocodingClient,
                                communityRepo = mapDeps.communityRepo,
                                favoritesRepo = mapDeps.favoritesRepo
                            )
                        )
                    }
                }

                // Only invalidate immediately if not listening or if status just changed
                if (state.status != VoiceEvent.Listening || isListening != wasListening || isSpeaking != wasSpeaking) {
                    invalidate()
                }
            }
        }

        // Separate collector for sampled transcript updates to avoid Android Auto refresh limits
        lifecycleScope.launch {
            store.state.sample(500).collectLatest { state ->
                if (state.status == VoiceEvent.Listening) {
                    invalidate()
                }
            }
        }
    }

    private fun refreshDynamicIcons() {
        val agent = cachedAgent ?: settingsManager.settings.value.selectedAgent
        dynamicIdleIcon = DynamicImageGenerator.generateIcon(agent, false)
        dynamicActiveIcon = DynamicImageGenerator.generateIcon(agent, true)
        invalidate()
    }

    private fun cycleToNextAgent() {
        val agents = AgentType.entries
        val current = settingsManager.settings.value.selectedAgent
        val currentIndex = agents.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + 1) % agents.size
        val nextAgent = agents[nextIndex]
        settingsManager.saveSettings(settingsManager.settings.value.copy(selectedAgent = nextAgent))
    }

    override fun onGetTemplate(): Template {
        return try {
            // NavigationTemplate CANNOT be wrapped in TabTemplate.
            // For navigation apps, we render NavigationTemplate directly when not in fallback mode.
            if (!useFallback) {
                surfaceCallback?.let { appManager.setSurfaceCallback(it) }
                scheduleFallbackIfNoSurface()
                return buildNavigationTemplate()
            }

            // Otherwise, we use the Tabbed interface with PaneTemplate
            val assistantTab = Tab.Builder()
                .setTitle("Assistant")
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                .setContentId(TAB_ASSISTANT)
                .build()

            val mapTab = Tab.Builder()
                .setTitle("Map")
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                .setContentId(TAB_MAP)
                .build()

            val historyTab = Tab.Builder()
                .setTitle("History")
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_history)).build())
                .setContentId(TAB_HISTORY)
                .build()

            val settingsTab = Tab.Builder()
                .setTitle("Settings")
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                .setContentId(TAB_SETTINGS)
                .build()

            val templateToDisplay = when (activeTabId) {
                TAB_ASSISTANT -> {
                    appManager.setSurfaceCallback(null)
                    buildPaneTemplate()
                }
                TAB_HISTORY -> buildHistoryTemplate()
                else -> buildPaneTemplate()
            }

            TabTemplate.Builder(object : TabTemplate.TabCallback {
                override fun onTabSelected(tabContentId: String) {
                    when (tabContentId) {
                        TAB_SETTINGS -> screenManager.push(AutoSettingsScreen(carContext, settingsManager, store, julesClient))
                        TAB_MAP -> {
                            val mapDeps = getMapDeps()
                            screenManager.push(
                                MapPoiScreen(
                                    carContext = carContext,
                                    poiProvider = mapDeps.poiProvider,
                                    availabilityProviderFactory = mapDeps.availabilityProviderFactory,
                                    settingsManager = settingsManager,
                                    routePlanner = mapDeps.routePlanner,
                                    routingClient = mapDeps.routingClient,
                                    tollCalculator = mapDeps.tollCalculator,
                                    trafficProviderFactory = mapDeps.trafficProviderFactory,
                                    geocodingClient = mapDeps.geocodingClient,
                                    communityRepo = mapDeps.communityRepo,
                                    favoritesRepo = mapDeps.favoritesRepo
                                )
                            )
                        }
                        else -> {
                            activeTabId = tabContentId
                            invalidate()
                        }
                    }
                }
            })
                .setHeaderAction(Action.APP_ICON)
                .addTab(assistantTab)
                .addTab(mapTab)
                .addTab(historyTab)
                .addTab(settingsTab)
                .setActiveTabContentId(activeTabId)
                .setTabContents(TabContents.Builder(templateToDisplay).build())
                .build()

        } catch (e: Exception) {
            Log.e(TAG, "onGetTemplate failed", e)
            buildErrorFallbackTemplate(e)
        }
    }

    private fun buildHistoryTemplate(): Template {
        val screenState = store.state.value.toHistoryScreenState()
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(screenState.emptyMessage)

        // Show last 6 items to comply with Android Auto list limits
        screenState.items.takeLast(6).reversed().forEach { item ->
            val senderIcon = if (item.isUser) R.drawable.ic_speaker else R.drawable.ic_home
            val senderLabel = if (item.isUser) Role.User.name else Role.Assistant.name
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(senderLabel)
                    .addText(item.text)
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, senderIcon)).build())
                    .setOnClickListener { store.speakAgain(item.text) }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle(screenState.title).build())
            .build()
    }

    private fun buildSettingsPlaceholderTemplate(): Template {
        return MessageTemplate.Builder("Redirecting to Settings...")
            .setLoading(true)
            .setHeader(Header.Builder().setTitle("Settings").build())
            .build()
    }

    private fun buildErrorFallbackTemplate(e: Exception): Template {
        val msg = e.message ?: e.toString()
        return MessageTemplate.Builder(msg.take(300))
            .setHeader(Header.Builder().setTitle("Julius Error").setStartHeaderAction(Action.APP_ICON).build())
            .build()
    }

    /** If we never receive a surface after showing NavigationTemplate, switch to MessageTemplate. */
    private fun scheduleFallbackIfNoSurface() {
        if (surfaceReceived) return
        fallbackCheckJob?.cancel()
        fallbackCheckJob = lifecycleScope.launch {
            delay(FALLBACK_DELAY_MS)
            if (!surfaceReceived) {
                useFallback = true
                invalidate()
            }
        }
    }

    private fun buildNavigationTemplate(): Template {
        val actionIconRes = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker
        val actionIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, actionIconRes)
        ).build()

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_swap_horiz)).build()
                    )
                    .setTitle(settingsManager.settings.value.selectedAgent.name)
                    .setOnClickListener { cycleToNextAgent() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_history)).build()
                    )
                    .setOnClickListener {
                        activeTabId = TAB_HISTORY
                        useFallback = true // Switch to tabbed view to show history
                        invalidate()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_settings)
                        ).build()
                    )
                    .setOnClickListener {
                        screenManager.push(AutoSettingsScreen(carContext, settingsManager, store, julesClient))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(actionIcon)
                    .setTitle(if (isListening || isSpeaking) "Stop" else "Speak")
                    .setOnClickListener {
                        when {
                            isSpeaking -> store.stopSpeaking()
                            isListening -> store.stopListening()
                            else -> store.startListening()
                        }
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    private fun buildPaneTemplate(): Template {
        val isProcessing = store.state.value.status == VoiceEvent.Processing
        val themeCarIcon = if (isListening || isSpeaking) {
            dynamicActiveIcon ?: CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.auto_theme_active)).build()
        } else {
            dynamicIdleIcon ?: CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.auto_theme_idle)).build()
        }

        val loaderIcon = DynamicImageGenerator.generateLoaderIcon(
            DynamicImageGenerator.paletteIndexForAgent(settingsManager.settings.value.selectedAgent)
        )

        val actionIconRes = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker
        val actionIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, actionIconRes)
        ).build()

        val paneBuilder = Pane.Builder()
            .setImage(if (isProcessing) loaderIcon else themeCarIcon)

        if (lastError != null) {
            val errorTitle = when (lastError!!.httpCode) {
                401 -> "Auth Error"
                403 -> "Permission Denied"
                429 -> "Rate Limit"
                in 500..599 -> "Server Error"
                else -> "Error"
            }
            val httpSuffix = lastError!!.httpCode?.let { " (HTTP $it)" } ?: ""
            val errorMessage = lastError!!.message + httpSuffix
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(errorMessage)
                    .build()
            )
        } else {
            val messages = store.state.value.messages
            val lastUserMsg = messages.lastOrNull { it.sender == Role.User }
            val lastAssistantMsg = messages.lastOrNull { it.sender == Role.Assistant }

            val statusRow = Row.Builder()
                .setTitle(if (isProcessing) "Processing" else currentStatus)
                .addText(currentText) // Current transcript or last overall message

            paneBuilder.addRow(statusRow.build())

            if (lastAssistantMsg != null) {
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("Assistant")
                        .addText(lastAssistantMsg.text.take(100) + if (lastAssistantMsg.text.length > 100) "..." else "")
                        .build()
                )
            }
        }

        paneBuilder.addAction(
            Action.Builder()
                .setIcon(actionIcon)
                .setTitle(if (isListening || isSpeaking) "Stop" else "Speak")
                .setOnClickListener {
                    when {
                        isSpeaking -> store.stopSpeaking()
                        isListening -> store.stopListening()
                        else -> store.startListening()
                    }
                }
                .build()
        )
        paneBuilder.addAction(
            Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_swap_horiz)).build())
                .setOnClickListener { cycleToNextAgent() }
                .build()
        )
        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(Header.Builder().setTitle("Assistant").build())
            .build()
    }

    companion object {
        private const val TAG = "MainScreen"
        private const val FALLBACK_DELAY_MS = 3000L
        private const val TAB_ASSISTANT = "assistant"
        private const val TAB_MAP = "map"
        private const val TAB_HISTORY = "history"
        private const val TAB_JULES = "jules"
        private const val TAB_SETTINGS = "settings"
    }
}

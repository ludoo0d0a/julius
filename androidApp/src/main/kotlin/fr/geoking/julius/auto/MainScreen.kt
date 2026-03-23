package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
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
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.AgentType
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.di.MapDeps
import fr.geoking.julius.shared.Role
import fr.geoking.julius.shared.toHistoryScreenState
import fr.geoking.julius.shared.VoiceEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Android Auto only: swap between two cached loader bitmaps while [VoiceEvent.Processing]. */
    private var processingLoaderVariant: Int = 0
    private var processingLoaderAlternateJob: Job? = null
    private var lastWasProcessing: Boolean = false

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

                val isProcessing = state.status == VoiceEvent.Processing
                if (isProcessing && !lastWasProcessing) {
                    processingLoaderVariant = 0
                    startProcessingLoaderAlternation()
                } else if (!isProcessing && lastWasProcessing) {
                    stopProcessingLoaderAlternation()
                }
                lastWasProcessing = isProcessing

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

    private fun startProcessingLoaderAlternation() {
        processingLoaderAlternateJob?.cancel()
        processingLoaderAlternateJob = lifecycleScope.launch {
            while (true) {
                delay(PROCESSING_LOADER_ALTERNATE_INTERVAL_MS)
                if (store.state.value.status != VoiceEvent.Processing) break
                processingLoaderVariant = processingLoaderVariant xor 1
                invalidate()
            }
        }
    }

    private fun stopProcessingLoaderAlternation() {
        processingLoaderAlternateJob?.cancel()
        processingLoaderAlternateJob = null
        processingLoaderVariant = 0
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
            // Navigation category: Tabbed UI providing access to Assistant, Map, History and Settings.
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
                    .setOnClickListener { store.speakAgain(item.text, isInterruptible = false) }
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

    private fun buildPaneTemplate(): Template {
        val isProcessing = store.state.value.status == VoiceEvent.Processing
        val themeCarIcon = if (isListening || isSpeaking) {
            dynamicActiveIcon ?: CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.auto_theme_active)).build()
        } else {
            dynamicIdleIcon ?: CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.auto_theme_idle)).build()
        }

        val paletteIndex = DynamicImageGenerator.paletteIndexForAgent(settingsManager.settings.value.selectedAgent)
        val loaderIcon = DynamicImageGenerator.generateLoaderIcon(paletteIndex, processingLoaderVariant)

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
        val agents = AgentType.entries
        val currentAgent = settingsManager.settings.value.selectedAgent
        val currentIndex = agents.indexOf(currentAgent).coerceAtLeast(0)
        val nextIndex = (currentIndex + 1) % agents.size
        val nextAgent = agents[nextIndex]

        paneBuilder.addAction(
            Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_swap_horiz)).build())
                .setTitle("${currentAgent.name} -> ${nextAgent.name}")
                .setOnClickListener { cycleToNextAgent() }
                .build()
        )
        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(Header.Builder().setTitle("Assistant").build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_jules)).build())
                            .setOnClickListener {
                                screenManager.push(AutoJulesScreen(carContext, store, settingsManager, julesClient))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    companion object {
        /** Low-frequency swap between two static loader variants (driver-distraction safe). */
        private const val PROCESSING_LOADER_ALTERNATE_INTERVAL_MS = 2_000L

        private const val TAG = "MainScreen"
        private const val TAB_ASSISTANT = "assistant"
        private const val TAB_MAP = "map"
        private const val TAB_HISTORY = "history"
        private const val TAB_JULES = "jules"
        private const val TAB_SETTINGS = "settings"
    }
}

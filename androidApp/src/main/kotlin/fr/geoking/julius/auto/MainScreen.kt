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
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabCallback
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.shared.Role
import fr.geoking.julius.shared.VoiceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private var currentStatus: String = "Idle"
    private var currentText: String = "Tap mic to start"
    private var lastError: DetailedError? = null
    private var isListening: Boolean = false
    private var isSpeaking: Boolean = false
    private var lastProcessedMessageId: String? = null
    private var activeTabId: String = TAB_ASSISTANT

    /** When true, use PaneTemplate (fallback) instead of NavigationTemplate. */
    private var useFallback: Boolean = false
    /** True once we have received a surface (so we don't fall back unnecessarily). */
    private var surfaceReceived: Boolean = false
    private var fallbackCheckJob: Job? = null
    private var surfaceRenderer: AutoSurfaceRenderer? = null

    private val appManager: AppManager
        get() = carContext.getCarService(AppManager::class.java)

    private val surfaceCallback = if (BuildConfig.CAR_USE_SURFACE) {
        object : SurfaceCallback {
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
    } else null

    init {
        lifecycleScope.launch {
            store.state.collectLatest { state ->
                isListening = state.status == VoiceEvent.Listening
                isSpeaking = state.status == VoiceEvent.Speaking
                surfaceRenderer?.isActive = isListening
                currentStatus = state.status.name
                lastError = state.lastError
                currentText = when {
                    state.lastError != null -> state.lastError!!.message
                    state.status == VoiceEvent.Listening -> state.currentTranscript.ifBlank { "Listening..." }
                    else -> state.messages.lastOrNull()?.text ?: "Tap mic to start"
                }

                // Voice keyword triggers
                val lastMsg = state.messages.lastOrNull()
                if (state.status == VoiceEvent.Silence && lastMsg?.sender == Role.User && lastMsg.id != lastProcessedMessageId) {
                    lastProcessedMessageId = lastMsg.id
                    val lastUserMsg = lastMsg.text.lowercase()
                    val keywords = listOf("display map", "map", "carte", "gas stations", "stations service")
                    if (keywords.any { lastUserMsg.contains(it) }) {
                        screenManager.push(MapPoiScreen(carContext))
                    }
                }

                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            // NavigationTemplate CANNOT be wrapped in TabTemplate.
            // If we are in Navigation mode (full build), we return NavigationTemplate directly.
            if (BuildConfig.CAR_USE_SURFACE && !useFallback) {
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
                    if (BuildConfig.CAR_USE_SURFACE) appManager.setSurfaceCallback(null)
                    buildPaneTemplate()
                }
                TAB_HISTORY -> buildHistoryTemplate()
                else -> buildPaneTemplate()
            }

            TabTemplate.Builder(object : TabCallback {
                override fun onTabSelected(tabContentId: String) {
                    if (tabContentId == TAB_SETTINGS) {
                        screenManager.push(AutoSettingsScreen(carContext, settingsManager))
                    } else {
                        activeTabId = tabContentId
                        invalidate()
                    }
                }
            })
                .setHeaderAction(Action.APP_ICON)
                .addTab(assistantTab)
                .addTab(historyTab)
                .addTab(settingsTab)
                .setActiveTabContentId(activeTabId)
                .setTemplate(templateToDisplay)
                .build()

        } catch (e: Exception) {
            Log.e(TAG, "onGetTemplate failed", e)
            buildErrorFallbackTemplate(e)
        }
    }

    private fun buildHistoryTemplate(): Template {
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("No conversation history")

        val messages = store.state.value.messages
        // Show last 6 messages to comply with Android Auto list limits
        messages.takeLast(6).reversed().forEach { msg ->
            val senderIcon = if (msg.sender == Role.User) R.drawable.ic_speaker else R.drawable.ic_home
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(msg.sender.name)
                    .addText(msg.text)
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, senderIcon)).build())
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Conversation History")
            .build()
    }

    private fun buildSettingsPlaceholderTemplate(): Template {
        return MessageTemplate.Builder("Redirecting to Settings...")
            .setLoading(true)
            .setTitle("Settings")
            .build()
    }

    private fun buildErrorFallbackTemplate(e: Exception): Template {
        val msg = e.message ?: e.toString()
        return MessageTemplate.Builder(msg.take(300))
            .setTitle("Julius Error")
            .setHeaderAction(Action.APP_ICON)
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
                            IconCompat.createWithResource(carContext, R.drawable.ic_map)
                        ).build()
                    )
                    .setOnClickListener {
                        screenManager.push(MapPoiScreen(carContext))
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
                        screenManager.push(AutoSettingsScreen(carContext, settingsManager))
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
        val themeImageResId = if (isListening) R.drawable.auto_theme_active else R.drawable.auto_theme_idle
        val themeCarIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, themeImageResId)
        ).build()

        val actionIconRes = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker
        val actionIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, actionIconRes)
        ).build()

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_map)
                        ).build()
                    )
                    .setOnClickListener {
                        screenManager.push(MapPoiScreen(carContext))
                    }
                    .build()
            )
            .build()

        val paneBuilder = Pane.Builder()
            .setImage(themeCarIcon)

        if (lastError != null) {
            val errorTitle = when (lastError!!.httpCode) {
                401 -> "Auth Error"
                403 -> "Permission Denied"
                429 -> "Rate Limit"
                in 500..599 -> "Server Error"
                else -> "Error"
            }
            val httpSuffix = lastError!!.httpCode?.let { " (HTTP $it)" } ?: ""
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("$errorTitle$httpSuffix")
                    .addText(lastError!!.message)
                    .build()
            )
        } else {
            val messages = store.state.value.messages
            val lastUserMsg = messages.lastOrNull { it.sender == Role.User }
            val lastAssistantMsg = messages.lastOrNull { it.sender == Role.Assistant }

            val statusRow = Row.Builder()
                .setTitle(currentStatus)
                .addText(currentText) // Current transcript or last overall message

            // Add a few more lines of context as requested ("The text that were heard")
            if (lastUserMsg != null && currentText != lastUserMsg.text) {
                statusRow.addText("Heard: ${lastUserMsg.text}")
            }

            paneBuilder.addRow(statusRow.build())

            if (lastAssistantMsg != null) {
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("Julius")
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
                .setTitle("Clear")
                .setOnClickListener {
                    store.clearConversation()
                }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setActionStrip(actionStrip)
            .setTitle("Julius Assistant")
            .build()
    }

    companion object {
        private const val TAG = "MainScreen"
        private const val FALLBACK_DELAY_MS = 3000L
        private const val TAB_ASSISTANT = "assistant"
        private const val TAB_HISTORY = "history"
        private const val TAB_SETTINGS = "settings"
    }
}

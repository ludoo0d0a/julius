package fr.geoking.julius.auto

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.BuildConfig
import fr.geoking.julius.R
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.VoiceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainScreen(
    carContext: CarContext,
    private val store: ConversationStore
) : Screen(carContext), SurfaceCallback {

    private var currentStatus: String = "Idle"
    private var currentText: String = "Tap mic to start"
    private var isListening: Boolean = false
    private var isSpeaking: Boolean = false

    /** When true, use PaneTemplate (fallback) instead of NavigationTemplate. */
    private var usePaneFallback: Boolean = false
    /** True once we have received a surface (so we don't fall back unnecessarily). */
    private var surfaceReceived: Boolean = false
    private var fallbackCheckJob: Job? = null
    private var surfaceRenderer: AutoSurfaceRenderer? = null

    private val appManager: AppManager
        get() = carContext.getCarService(AppManager::class.java)

    init {
        lifecycleScope.launch {
            store.state.collectLatest { state ->
                isListening = state.status == VoiceEvent.Listening
                isSpeaking = state.status == VoiceEvent.Speaking
                surfaceRenderer?.isActive = isListening
                currentStatus = state.status.name
                currentText = if (state.status == VoiceEvent.Listening) {
                    state.currentTranscript.ifBlank { "Listening..." }
                } else {
                    state.messages.lastOrNull()?.text ?: "Hello"
                }
                invalidate()
            }
        }
    }

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

    override fun onGetTemplate(): Template {
        // Play Store build: no ACCESS_SURFACE / NAVIGATION_TEMPLATES → use PaneTemplate only
        if (!BuildConfig.CAR_USE_SURFACE) {
            appManager.setSurfaceCallback(null)
            return buildPaneTemplate()
        }
        if (usePaneFallback) {
            appManager.setSurfaceCallback(null)
            return buildPaneTemplate()
        }

        appManager.setSurfaceCallback(this)
        scheduleFallbackIfNoSurface()
        return buildNavigationTemplate()
    }

    /** If we never receive a surface after showing NavigationTemplate, switch to PaneTemplate. */
    private fun scheduleFallbackIfNoSurface() {
        if (surfaceReceived) return
        fallbackCheckJob?.cancel()
        fallbackCheckJob = lifecycleScope.launch {
            delay(FALLBACK_DELAY_MS)
            if (!surfaceReceived) {
                usePaneFallback = true
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

        val pane = Pane.Builder()
            .setImage(themeCarIcon)
            .addRow(
                Row.Builder()
                    .setTitle(currentStatus)
                    .addText(currentText)
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

        return PaneTemplate.Builder(pane)
            .setTitle("Julius")
            .build()
    }

    companion object {
        private const val FALLBACK_DELAY_MS = 3000L
    }
}

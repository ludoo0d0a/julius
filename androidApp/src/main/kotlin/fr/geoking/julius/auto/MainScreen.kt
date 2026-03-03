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
import androidx.car.app.model.MessageTemplate
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
import fr.geoking.julius.shared.DetailedError
import fr.geoking.julius.shared.VoiceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainScreen(
    carContext: CarContext,
    private val store: ConversationStore
) : Screen(carContext) {

    private var currentStatus: String = "Idle"
    private var currentText: String = "Tap mic to start"
    private var lastError: DetailedError? = null
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
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            // Play Store build: no ACCESS_SURFACE / NAVIGATION_TEMPLATES → use MessageTemplate only
            if (!BuildConfig.CAR_USE_SURFACE) {
                // Completely bypass any setSurfaceCallback call in Play flavor to avoid ACCESS_SURFACE check
                buildMessageTemplate()
            } else if (usePaneFallback) {
                appManager.setSurfaceCallback(null)
                buildPaneTemplate()
            } else {
                // If we reach here, BuildConfig.CAR_USE_SURFACE is true
                surfaceCallback?.let { appManager.setSurfaceCallback(it) }
                scheduleFallbackIfNoSurface()
                buildNavigationTemplate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onGetTemplate failed", e)
            buildErrorFallbackTemplate(e)
        }
    }

    private fun buildErrorFallbackTemplate(e: Exception): Template {
        val msg = e.message ?: e.toString()
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Error")
                    .addText(msg.take(300))
                    .build()
            )
            .build()
        return PaneTemplate.Builder(pane).setTitle("Julius").build()
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

    private fun buildMessageTemplate(): Template {
        val actionIconRes = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker
        val actionIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, actionIconRes)
        ).build()

        val (title, text) = when {
            lastError != null -> {
                val errorTitle = when (lastError!!.httpCode) {
                    401 -> "Auth Error"
                    403 -> "Permission Denied"
                    429 -> "Rate Limit"
                    in 500..599 -> "Server Error"
                    else -> "Error"
                }
                val httpSuffix = lastError!!.httpCode?.let { " (HTTP $it)" } ?: ""
                "$errorTitle$httpSuffix" to lastError!!.message
            }
            else -> currentStatus to currentText
        }

        return MessageTemplate.Builder(text)
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
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

        val (rowTitle, rowText) = when {
            lastError != null -> {
                val errorTitle = when (lastError!!.httpCode) {
                    401 -> "Auth Error"
                    403 -> "Permission Denied"
                    429 -> "Rate Limit"
                    in 500..599 -> "Server Error"
                    else -> "Error"
                }
                val httpSuffix = lastError!!.httpCode?.let { " (HTTP $it)" } ?: ""
                "$errorTitle$httpSuffix" to lastError!!.message
            }
            else -> currentStatus to currentText
        }
        val pane = Pane.Builder()
            .setImage(themeCarIcon)
            .addRow(
                Row.Builder()
                    .setTitle(rowTitle)
                    .addText(rowText)
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
        private const val TAG = "MainScreen"
        private const val FALLBACK_DELAY_MS = 3000L
    }
}

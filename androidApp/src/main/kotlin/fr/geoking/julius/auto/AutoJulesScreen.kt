package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.shared.ConversationStore
import fr.geoking.julius.shared.VoiceEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutoJulesScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient
) : Screen(carContext) {

    private var isListening: Boolean = false
    private var isSpeaking: Boolean = false
    private var currentStatus: String = "Idle"
    private var currentTranscript: String = ""

    private var lastCapturedPrompt: String? = null
    private var lastCreatedSessionTitle: String? = null
    private var lastError: String? = null
    private var loading: Boolean = false

    init {
        lifecycleScope.launch {
            store.state.collectLatest { state ->
                isListening = state.status == VoiceEvent.Listening
                isSpeaking = state.status == VoiceEvent.Speaking
                currentStatus = state.status.name
                currentTranscript = state.currentTranscript

                // Consider the latest stable transcript as the "prompt" to create.
                if (state.status == VoiceEvent.Silence && state.currentTranscript.isNotBlank()) {
                    lastCapturedPrompt = state.currentTranscript.trim()
                    // We only disable auto-send while capturing a prompt on this screen.
                    store.autoSendFinalTranscripts = true
                }

                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            val settings = settingsManager.settings.value
            val apiKey = settings.julesKey
            val repoId = settings.lastJulesRepoId.takeIf { it.isNotBlank() }
            val repoName = settings.lastJulesRepoName.takeIf { it.isNotBlank() } ?: repoId

            if (apiKey.isBlank()) {
                return MessageTemplate.Builder("Set your Jules API key in Settings (phone), then come back here.")
                    .setHeader(Header.Builder().setTitle("Jules").setStartHeaderAction(Action.BACK).build())
                    .build()
            }

            if (repoId == null) {
                return MessageTemplate.Builder("Open Jules on the phone once and select a repository, then come back here.")
                    .setHeader(Header.Builder().setTitle("Jules").setStartHeaderAction(Action.BACK).build())
                    .build()
            }

            val actionIconRes = if (isSpeaking) R.drawable.ic_stop else R.drawable.ic_speaker
            val actionIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, actionIconRes)).build()

            val pane = Pane.Builder()
                .addRow(
                    Row.Builder()
                        .setTitle("Repository")
                        .addText(repoName ?: repoId)
                        .build()
                )
                .addRow(
                    Row.Builder()
                        .setTitle("Status")
                        .addText(if (loading) "Creating conversation…" else currentStatus)
                        .build()
                )

            val prompt = lastCapturedPrompt
            if (!prompt.isNullOrBlank()) {
                pane.addRow(
                    Row.Builder()
                        .setTitle("New prompt (from voice)")
                        .addText(prompt.take(200) + if (prompt.length > 200) "…" else "")
                        .build()
                )
            } else {
                pane.addRow(
                    Row.Builder()
                        .setTitle("New prompt (from voice)")
                        .addText("Tap Speak and describe what Jules should do.")
                        .build()
                )
            }

            if (!lastCreatedSessionTitle.isNullOrBlank()) {
                pane.addRow(
                    Row.Builder()
                        .setTitle("Last created")
                        .addText(lastCreatedSessionTitle!!)
                        .build()
                )
            }

            if (!lastError.isNullOrBlank()) {
                pane.addRow(
                    Row.Builder()
                        .setTitle("Error")
                        .addText(lastError!!.take(200))
                        .build()
                )
            }

            pane.addAction(
                Action.Builder()
                    .setIcon(actionIcon)
                    .setTitle(if (isListening || isSpeaking) "Stop" else "Speak")
                    .setOnClickListener {
                        when {
                            isSpeaking -> store.stopSpeaking()
                            isListening -> store.stopListening()
                            else -> {
                                lastError = null
                                store.autoSendFinalTranscripts = false
                                store.startListening()
                            }
                        }
                    }
                    .build()
            )

            pane.addAction(
                Action.Builder()
                    .setTitle("Create")
                    .setOnClickListener {
                        val p = lastCapturedPrompt?.takeIf { it.isNotBlank() } ?: return@setOnClickListener
                        if (loading) return@setOnClickListener
                        createSession(apiKey = apiKey, repoId = repoId, prompt = p)
                    }
                    .build()
            )

            PaneTemplate.Builder(pane.build())
                .setHeader(Header.Builder().setTitle("Jules").setStartHeaderAction(Action.BACK).build())
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "onGetTemplate failed", e)
            MessageTemplate.Builder((e.message ?: e.toString()).take(250))
                .setHeader(Header.Builder().setTitle("Jules").setStartHeaderAction(Action.BACK).build())
                .build()
        }
    }

    private fun createSession(apiKey: String, repoId: String, prompt: String) {
        loading = true
        lastError = null
        invalidate()
        lifecycleScope.launch {
            try {
                val session = julesClient.createSession(
                    apiKey = apiKey,
                    prompt = prompt,
                    source = repoId,
                    title = prompt.take(80)
                )
                lastCreatedSessionTitle = session.title.ifBlank { session.prompt.take(80) }.ifBlank { "Conversation created" }

                // Speak a short confirmation (without hitting the main assistant agent).
                store.voiceManager.speak("Created a new Jules conversation.")

                // Clear transcript so the user can dictate a new one.
                store.clearTranscript()
                lastCapturedPrompt = null
            } catch (e: Exception) {
                lastError = e.message ?: "Failed to create conversation"
                // Keep prompt so user can retry.
            } finally {
                loading = false
                invalidate()
            }
        }
    }

    companion object {
        private const val TAG = "AutoJulesScreen"
    }
}


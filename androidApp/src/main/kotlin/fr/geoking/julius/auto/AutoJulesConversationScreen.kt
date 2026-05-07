package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.R
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.voice.VoiceEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto screen to view a Jules conversation and send messages.
 * Uses ListTemplate (voice-only) to stay available while driving.
 */
class AutoJulesConversationScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository,
    private val session: JulesSessionEntity
) : Screen(carContext) {

    private var chatItems: List<JulesChatItem> = emptyList()
    private var loading: Boolean = true
    private var sending: Boolean = false
    private var lastError: String? = null
    private var lastSentFromStt: String? = null

    private var currentSessionState: JulesSessionEntity? = session

    init {
        // This screen uses STT as an input method, but messages should go to Jules (not the main conversational agent).
        store.autoSendFinalTranscripts = false

        startPolling()
        startSessionPolling()

        lifecycleScope.launch {
            store.state.collectLatest {
                invalidate()
            }
        }

        lifecycleScope.launch {
            store.voiceManager.transcribedText.collectLatest { text ->
                val trimmed = text.trim()
                if (trimmed.isBlank()) return@collectLatest
                if (sending) return@collectLatest
                if (trimmed == lastSentFromStt) return@collectLatest

                lastSentFromStt = trimmed
                sendMessage(trimmed)
            }
        }
    }

    private fun startPolling() {
        lifecycleScope.launch {
            while (true) {
                try {
                    julesRepository.getActivities(session.id).collectLatest { items ->
                        if (items.size != chatItems.size || items.lastOrNull() != chatItems.lastOrNull()) {
                            chatItems = items
                            loading = false
                            invalidate()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to poll activities", e)
                }
                delay(5000) // Poll activities every 5 seconds
            }
        }
    }

    private fun startSessionPolling() {
        val githubToken = settingsManager.settings.value.githubApiKey
        lifecycleScope.launch {
            while (true) {
                try {
                    julesRepository.pollSessionStatus(session.id, githubToken)
                    val updated = julesRepository.getSession(session.id)
                    if (updated != null && updated.sessionState != currentSessionState?.sessionState) {
                        currentSessionState = updated
                        invalidate()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to poll session status", e)
                }
                delay(30000) // Poll session status every 30 seconds
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(if (loading) "Loading messages…" else "No messages in this conversation.")

        val state = store.state.value
        val isActive = state.status == VoiceEvent.Listening || state.status == VoiceEvent.Speaking || state.status == VoiceEvent.Processing
        if (state.status == VoiceEvent.Listening) {
            val statusText = if (state.currentTranscript.isBlank()) carContext.getString(R.string.listening) else state.currentTranscript
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(statusText.take(200))
                    .build()
            )
        } else if (state.status == VoiceEvent.Processing) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.thinking))
                    .build()
            )
        } else if (state.status == VoiceEvent.Speaking) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.speaking))
                    .build()
            )
        }

        val sess = currentSessionState ?: session
        if (!sess.isFinished) {
            val isPaused = sess.sessionState == "PAUSED"
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(if (isPaused) carContext.getString(R.string.resume) else carContext.getString(R.string.pause))
                    .addText(if (isPaused) "Session is paused" else "Session is active")
                    .setImage(
                        CarIcon.Builder(
                            androidx.core.graphics.drawable.IconCompat.createWithResource(
                                carContext,
                                if (isPaused) fr.geoking.julius.R.drawable.ic_home else fr.geoking.julius.R.drawable.ic_stop
                            )
                        ).build()
                    )
                    .setOnClickListener {
                        lifecycleScope.launch {
                            try {
                                if (isPaused) julesRepository.resumeSession(sess.id)
                                else julesRepository.pauseSession(sess.id)
                                val updated = julesRepository.getSession(sess.id)
                                if (updated != null) {
                                    currentSessionState = updated
                                    invalidate()
                                }
                            } catch (e: Exception) {
                                lastError = "Action failed: ${e.message}"
                                invalidate()
                            }
                        }
                    }
                    .build()
            )
        }

        if (sending) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Sending message…")
                    .build()
            )
        }

        lastError?.let {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Error")
                    .addText(it.take(200))
                    .build()
            )
        }

        // Show last 6 messages to stay within AA limits (6 items per list in some templates)
        chatItems.takeLast(6).reversed().forEach { item ->
            val (title, text, icon) = when (item) {
                is JulesChatItem.UserMessage -> Triple("You", item.text, fr.geoking.julius.R.drawable.ic_speaker)
                is JulesChatItem.AgentMessage -> Triple("Jules", item.text, fr.geoking.julius.R.drawable.ic_home)
            }
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(title)
                    .addText(text)
                    .setImage(CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithResource(carContext, icon)).build())
                    .setOnClickListener { store.voiceManager.speak(text) }
                    .build()
            )
        }

        val micAction = if (isActive) {
            Action.Builder()
                .setTitle(carContext.getString(R.string.stop))
                .setOnClickListener {
                    store.stopAllActions()
                    store.clearTranscript()
                }
                .build()
        } else {
            Action.Builder()
                .setTitle(carContext.getString(R.string.start_listening))
                .setOnClickListener {
                    store.clearTranscript()
                    store.startListening(continuous = false)
                }
                .build()
        }

        val sessTitle = (currentSessionState ?: session).title.ifBlank { "Jules" }
        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(sessTitle.take(60))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(micAction)
                    .build()
            )
            .build()
    }

    private fun sendMessage(prompt: String) {
        sending = true
        lastError = null
        invalidate()
        lifecycleScope.launch {
            try {
                julesRepository.sendMessage(session.id, prompt)
                store.clearTranscript()
                // Refresh immediately
                julesRepository.getActivities(session.id).collectLatest { items ->
                    chatItems = items
                    sending = false
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                lastError = e.message ?: "Failed to send"
                sending = false
                invalidate()
            }
        }
    }

    companion object {
        private const val TAG = "AutoJulesConversationScreen"
    }
}

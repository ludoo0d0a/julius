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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto screen to view a Jules conversation and send messages.
 * Uses SearchTemplate to provide a text/voice input zone and display chat history.
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
    private var lastCapturedPrompt: String? = null
    private var loading: Boolean = true
    private var sending: Boolean = false
    private var lastError: String? = null

    private var currentSessionState: JulesSessionEntity? = session

    init {
        startPolling()
        startSessionPolling()
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

        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                lastCapturedPrompt = searchText
            }

            override fun onSearchSubmitted(searchText: String) {
                lastCapturedPrompt = searchText
                if (searchText.isNotBlank()) {
                    sendMessage(searchText)
                }
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Send message to Jules…")
            .setInitialSearchText(lastCapturedPrompt ?: "")
            .setItemList(listBuilder.build())
            .build()
    }

    private fun sendMessage(prompt: String) {
        sending = true
        lastError = null
        invalidate()
        lifecycleScope.launch {
            try {
                julesRepository.sendMessage(session.id, prompt)
                lastCapturedPrompt = null
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

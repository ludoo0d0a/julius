package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto screen to create a new Jules session.
 * Uses ListTemplate (voice-only) to stay available while driving.
 */
class AutoJulesNewSessionScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository,
    private val sourceId: String,
    private val sourceDisplayName: String
) : Screen(carContext) {

    private var loading: Boolean = false
    private var error: String? = null
    private var lastSentFromStt: String? = null

    init {
        // This screen uses STT as an input method, but prompts should create a new Jules session.
        store.autoSendFinalTranscripts = false

        lifecycleScope.launch {
            store.voiceManager.transcribedText.collectLatest { text ->
                val trimmed = text.trim()
                if (trimmed.isBlank()) return@collectLatest
                if (loading) return@collectLatest
                if (trimmed == lastSentFromStt) return@collectLatest

                lastSentFromStt = trimmed
                createSession(trimmed)
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (loading) {
            return MessageTemplate.Builder("Creating conversation…")
                .setLoading(true)
                .setHeader(Header.Builder().setTitle("New Conversation").setStartHeaderAction(Action.BACK).build())
                .build()
        }

        val list = ItemList.Builder()
        error?.let {
            list.addItem(Row.Builder().setTitle("Error").addText(it.take(200)).build())
        }
        list.addItem(Row.Builder().setTitle("Repository").addText(sourceDisplayName).build())
        list.addItem(
            Row.Builder()
                .setTitle("Describe the task")
                .addText("Tap “Start listening” and speak your request.")
                .build()
        )

        val listeningAction = Action.Builder()
            .setTitle(carContext.getString(fr.geoking.julius.R.string.start_listening))
            .setOnClickListener {
                error = null
                store.clearTranscript()
                store.startListening()
                invalidate()
            }
            .build()

        return ListTemplate.Builder()
            .setSingleList(list.build())
            .setHeader(
                Header.Builder()
                    .setTitle("New Conversation")
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(listeningAction)
                    .build()
            )
            .build()
    }

    private fun createSession(prompt: String) {
        val apiKeys = settingsManager.settings.value.julesKeys
        loading = true
        error = null
        invalidate()
        lifecycleScope.launch {
            try {
                val sessionId = julesRepository.createSession(
                    apiKeys = apiKeys,
                    prompt = prompt,
                    source = sourceId,
                    title = prompt.take(80)
                )
                val entity = julesRepository.getSession(sessionId) ?: throw Exception("Failed to load created session")
                // Navigate to conversation view
                screenManager.pop() // Remove "New Session" screen
                screenManager.push(AutoJulesConversationScreen(carContext, store, settingsManager, julesClient, julesRepository, entity))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create session", e)
                error = e.message ?: "Failed to create conversation"
                loading = false
                invalidate()
            }
        }
    }

    companion object {
        private const val TAG = "AutoJulesNewSessionScreen"
    }
}

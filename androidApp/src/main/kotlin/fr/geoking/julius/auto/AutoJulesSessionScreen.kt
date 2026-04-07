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
 * Android Auto screen to list sessions for a selected repository.
 */
class AutoJulesSessionScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository,
    private val sourceId: String,
    private val sourceDisplayName: String
) : Screen(carContext) {

    private var sessions: List<JulesSessionEntity> = emptyList()
    private var loading: Boolean = true
    private var error: String? = null

    init {
        loadSessions()
    }

    private fun loadSessions() {
        val settings = settingsManager.settings.value
        val apiKey = settings.julesKey
        val githubToken = settings.githubApiKey

        lifecycleScope.launch {
            try {
                julesRepository.getSessions(apiKey, sourceId, githubToken).collectLatest { list ->
                    sessions = list
                    loading = false
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load sessions", e)
                error = e.message ?: "Failed to load sessions"
                loading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (loading && sessions.isEmpty()) {
            return MessageTemplate.Builder("Loading conversations…")
                .setLoading(true)
                .setHeader(Header.Builder().setTitle("Jules - $sourceDisplayName").setStartHeaderAction(Action.BACK).build())
                .build()
        }

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("No conversations yet. Tap 'New conversation' to start.")

        // "New conversation" row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("New conversation")
                .addText("Start a new Jules coding session")
                .setImage(CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithResource(carContext, fr.geoking.julius.R.drawable.ic_home)).build())
                .setOnClickListener {
                    screenManager.push(AutoJulesNewSessionScreen(carContext, store, settingsManager, julesClient, julesRepository, sourceId, sourceDisplayName))
                }
                .build()
        )

        sessions.filter { !it.isArchived }.take(10).forEach { session ->
            val status = when {
                session.prState == "merged" -> "Merged"
                session.prState == "closed" -> "Closed"
                session.prState == "open" -> "Open PR"
                session.sessionState == "COMPLETED" -> "Completed"
                session.sessionState == "FAILED" -> "Failed"
                session.sessionState == "AWAITING_PLAN_APPROVAL" -> "Waiting for approval"
                session.sessionState == "AWAITING_USER_FEEDBACK" -> "Waiting for you"
                session.sessionState == "PLANNING" -> "Planning…"
                session.sessionState == "QUEUED" -> "Queued…"
                session.sessionState == "PAUSED" -> "Paused"
                else -> if (!session.prUrl.isNullOrBlank()) "Output available" else "In progress"
            }

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(session.title.ifBlank { session.prompt.take(60) }.ifBlank { "Conversation" })
                    .addText(status)
                    .setOnClickListener {
                        screenManager.push(AutoJulesConversationScreen(carContext, store, settingsManager, julesClient, julesRepository, session))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Jules - $sourceDisplayName").setStartHeaderAction(Action.BACK).build())
            .build()
    }

    companion object {
        private const val TAG = "AutoJulesSessionScreen"
    }
}

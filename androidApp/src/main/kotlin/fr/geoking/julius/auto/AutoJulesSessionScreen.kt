package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.JulesSessionEntity
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
    private val sourceDisplayName: String,
    private val featureId: String? = null,
    private val featureTitle: String? = null
) : Screen(carContext), KoinComponent {

    private val featureRepository: FeatureRepository by inject()
    private var sessions: List<JulesSessionEntity> = emptyList()
    private var loading: Boolean = true
    private var error: String? = null

    init {
        loadSessions()
    }

    private fun loadSessions() {
        val settings = settingsManager.settings.value
        val apiKeys = settings.julesKeys
        val githubToken = settings.githubApiKey

        lifecycleScope.launch {
            try {
                julesRepository.getSessions(this, apiKeys, sourceId).collectLatest { list ->
                    sessions = list.filter {
                        when {
                            featureId == null -> true
                            featureId.startsWith("session_") -> it.id == featureId.removePrefix("session_")
                            else -> it.featureId == featureId
                        }
                    }
                    loading = false
                    invalidate()
                    featureRepository.autoPromoteOrphans(this, sourceId, list)
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
        val headerTitle = featureTitle ?: sourceDisplayName

        if (loading && sessions.isEmpty()) {
            return MessageTemplate.Builder("Chargement des conversations…")
                .setLoading(true)
                .setHeader(Header.Builder().setTitle("Jules - $headerTitle").setStartHeaderAction(Action.BACK).build())
                .build()
        }

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("Aucune conversation ici.")

        // "New conversation" row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Nouvelle conversation")
                .addText("Démarrer une nouvelle session Jules")
                .setImage(CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithResource(carContext, fr.geoking.julius.R.drawable.ic_home)).build())
                .setOnClickListener {
                    screenManager.push(
                        AutoJulesNewSessionScreen(
                            carContext,
                            store,
                            settingsManager,
                            julesClient,
                            julesRepository,
                            sourceId,
                            sourceDisplayName,
                            featureId
                        )
                    )
                }
                .build()
        )

        sessions.filter { !it.isArchived }.take(10).forEach { session ->
            val backend = if (session.id.startsWith("sesn_")) "CLAUDE_CODE" else "JULES"
            val status = when (session.sessionState) {
                "COMPLETED" -> "Terminé"
                "FAILED" -> "Échec"
                "AWAITING_PLAN_APPROVAL" -> "Approbation"
                "AWAITING_USER_FEEDBACK" -> "Feedback requis"
                "PLANNING" -> "Planning…"
                "QUEUED" -> "En file…"
                "PAUSED" -> "En pause"
                "IN_PROGRESS" -> "En cours…"
                else -> when {
                    session.prState == "merged" -> "Mergé"
                    session.prState == "closed" -> "Fermée"
                    session.prState == "open" -> "PR ouverte"
                    !session.prUrl.isNullOrBlank() -> "Output available"
                    else -> "Initialisation…"
                }
            }

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(session.title.ifBlank { session.prompt.take(60) }.ifBlank { "Conversation" })
                    .addText("$backend · ${session.sourceName} · $status")
                    .setOnClickListener {
                        screenManager.push(
                            AutoJulesConversationScreen(
                                carContext,
                                store,
                                settingsManager,
                                julesClient,
                                julesRepository,
                                session
                            )
                        )
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Jules - $headerTitle").setStartHeaderAction(Action.BACK).build())
            .build()
    }

    companion object {
        private const val TAG = "AutoJulesSessionScreen"
    }
}

package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.FeatureEntity
import fr.geoking.julius.queue.CodingAgentQueueEngine
import fr.geoking.julius.repository.FeatureRepository
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android Auto screen to list features for a selected project.
 */
class AutoJulesFeatureScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository,
    private val sourceId: String,
    private val sourceDisplayName: String
) : Screen(carContext), KoinComponent {

    private val featureRepository: FeatureRepository by inject()
    private val queueEngine: CodingAgentQueueEngine by inject()
    private var features: List<FeatureEntity> = emptyList()
    private var loading: Boolean = true
    private var queueSummary: String = ""

    init {
        loadFeatures()
        lifecycleScope.launch {
            queueEngine.status.collectLatest { status ->
                queueSummary = when {
                    status.paused -> "Queue paused"
                    else -> "Queue ${status.activeCount}/${status.parallelLimit} · ${status.pendingCount} pending"
                }
                invalidate()
            }
        }
    }

    private fun loadFeatures() {
        lifecycleScope.launch {
            val apiKeys = settingsManager.settings.value.julesKeys

            // 1. Initial quick load from cache
            val cached = featureRepository.getFeaturesCached(sourceId)
            if (cached.isNotEmpty()) {
                features = cached.sortedBy { it.position }
                loading = false
                invalidate()
            }

            // 2. Background refresh if needed
            val refreshJob = if (apiKeys.isNotEmpty() && featureRepository.shouldRefreshFeatures(sourceId)) {
                launch {
                    try {
                        featureRepository.refreshFeatures(sourceId, apiKeys, settingsManager.settings.value.githubApiKey)
                    } catch (e: Exception) {
                        android.util.Log.e("AutoJulesFeatureScreen", "Failed to refresh features", e)
                    }
                }
            } else null

            // 3. Observe the flow
            try {
                featureRepository.getFeaturesFlow(sourceId).collectLatest { list ->
                    features = list.sortedBy { it.position }
                    loading = false
                    invalidate()
                }
            } finally {
                refreshJob?.cancel()
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (loading) {
            return MessageTemplate.Builder("Chargement des features…")
                .setLoading(true)
                .setHeader(Header.Builder().setTitle("Jules - $sourceDisplayName").setStartHeaderAction(Action.BACK).build())
                .build()
        }

        val listBuilder = ItemList.Builder()

        if (queueSummary.isNotBlank()) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(queueSummary)
                    .build(),
            )
        }

        features.forEach { feature ->
            val statusLabel = when (feature.status) {
                "IN_PROGRESS" -> "En cours"
                "COMPLETED" -> "Terminée"
                "FAILED" -> "À faire (Échec)"
                "QUEUED" -> "Prête"
                else -> "Idée"
            }

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(feature.title)
                    .addText(statusLabel)
                    .setOnClickListener {
                        screenManager.push(
                            AutoJulesSessionScreen(
                                carContext,
                                store,
                                settingsManager,
                                julesClient,
                                julesRepository,
                                sourceId = sourceId,
                                sourceDisplayName = sourceDisplayName,
                                featureId = feature.id,
                                featureTitle = feature.title
                            )
                        )
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Jules - $sourceDisplayName").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}

package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.persistence.FeatureEntity
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
    private var features: List<FeatureEntity> = emptyList()
    private var loading: Boolean = true

    init {
        loadFeatures()
    }

    private fun loadFeatures() {
        lifecycleScope.launch {
            featureRepository.getAllFeatures().collectLatest { all ->
                features = all.filter { it.sourceName == sourceId }.sortedBy { it.position }
                loading = false
                invalidate()
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

        // "Unlinked conversations" row (All others)
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Conversations non liées")
                .addText("Voir les conversations sans feature")
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
                            featureId = null,
                            featureTitle = "Hors feature"
                        )
                    )
                }
                .build()
        )

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

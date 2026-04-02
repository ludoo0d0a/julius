package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.repository.JulesRepository
import fr.geoking.julius.shared.conversation.ConversationStore
import kotlinx.coroutines.launch

/**
 * Android Auto screen to list available Jules repositories (sources).
 */
class AutoJulesSourceScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient,
    private val julesRepository: JulesRepository
) : Screen(carContext) {

    private var sources: List<JulesClient.JulesSource> = emptyList()
    private var loading: Boolean = true
    private var error: String? = null

    init {
        loadSources()
    }

    private fun loadSources() {
        val apiKey = settingsManager.settings.value.julesKey
        if (apiKey.isBlank()) {
            error = "Jules API key is missing. Set it on your phone."
            loading = false
            invalidate()
            return
        }

        lifecycleScope.launch {
            try {
                val resp = julesClient.listSources(apiKey)
                sources = resp.sources
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load sources", e)
                error = e.message ?: "Failed to load repositories"
            } finally {
                loading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (loading) {
            return MessageTemplate.Builder("Loading repositories…")
                .setLoading(true)
                .setHeader(Header.Builder().setTitle("Jules - Repositories").setStartHeaderAction(Action.BACK).build())
                .build()
        }

        val error = error
        if (error != null) {
            return MessageTemplate.Builder(error.take(250))
                .setHeader(Header.Builder().setTitle("Jules - Error").setStartHeaderAction(Action.BACK).build())
                .addAction(Action.Builder().setTitle("Retry").setOnClickListener {
                    this.loading = true
                    this.error = null
                    invalidate()
                    loadSources()
                }.build())
                .build()
        }

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("No repositories found. Connect one at jules.google.com.")

        sources.forEach { src ->
            val displayName = src.githubRepo?.let { "${it.owner}/${it.repo}" } ?: src.name
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(displayName)
                    .setOnClickListener {
                        val currentSettings = settingsManager.settings.value
                        settingsManager.saveSettings(currentSettings.copy(
                            lastJulesRepoId = src.name,
                            lastJulesRepoName = displayName
                        ))
                        screenManager.push(AutoJulesSessionScreen(carContext, store, settingsManager, julesClient, julesRepository, src.name, displayName))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Jules - Repositories").setStartHeaderAction(Action.BACK).build())
            .build()
    }

    companion object {
        private const val TAG = "AutoJulesSourceScreen"
    }
}

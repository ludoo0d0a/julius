package fr.geoking.julius.auto

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.api.jules.JulesClient
import fr.geoking.julius.shared.ConversationStore
import kotlinx.coroutines.launch

/**
 * Android Auto screen for Jules, using SearchTemplate to provide a text zone
 * that supports both typing and voice input.
 */
class AutoJulesScreen(
    carContext: CarContext,
    private val store: ConversationStore,
    private val settingsManager: SettingsManager,
    private val julesClient: JulesClient
) : Screen(carContext) {

    private var lastCapturedPrompt: String? = null
    private var lastCreatedSessionTitle: String? = null
    private var lastError: String? = null
    private var loading: Boolean = false

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

            if (loading) {
                return MessageTemplate.Builder("Creating conversation…")
                    .setLoading(true)
                    .setHeader(Header.Builder().setTitle("Jules").setStartHeaderAction(Action.BACK).build())
                    .build()
            }

            val list = ItemList.Builder()

            list.addItem(
                Row.Builder()
                    .setTitle("Repository")
                    .addText(repoName ?: repoId)
                    .build()
            )

            if (!lastCreatedSessionTitle.isNullOrBlank()) {
                list.addItem(
                    Row.Builder()
                        .setTitle("Last created")
                        .addText(lastCreatedSessionTitle!!)
                        .build()
                )
            }

            if (!lastError.isNullOrBlank()) {
                list.addItem(
                    Row.Builder()
                        .setTitle("Error")
                        .addText(lastError!!.take(200))
                        .build()
                )
            }

            SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    lastCapturedPrompt = searchText
                }

                override fun onSearchSubmitted(searchText: String) {
                    lastCapturedPrompt = searchText
                    if (searchText.isNotBlank()) {
                        createSession(apiKey, repoId, searchText)
                    }
                }
            })
                .setHeaderAction(Action.BACK)
                .setSearchHint("What should Jules do?")
                .setInitialSearchText(lastCapturedPrompt ?: "")
                .setItemList(list.build())
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

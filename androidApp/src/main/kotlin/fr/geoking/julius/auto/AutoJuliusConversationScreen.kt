package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.R
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.conversation.Role
import fr.geoking.julius.shared.voice.VoiceEvent
import kotlinx.coroutines.launch

/**
 * Main Julius conversation on Android Auto: uses [SearchTemplate] so the host provides
 * microphone + keyboard input (the in-app [android.speech.SpeechRecognizer] path is unreliable in AA).
 */
class AutoJuliusConversationScreen(
    carContext: CarContext,
    private val store: ConversationStore,
) : Screen(carContext) {

    private var lastSearchDraft: String? = null

    init {
        lifecycleScope.launch {
            store.state.collect {
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, TAG) {
        val state = store.state.value
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("No messages yet. Use the search bar to ask Julius.")

        when (state.status) {
            VoiceEvent.Processing ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Julius")
                        .addText("Thinking…")
                        .build()
                )
            VoiceEvent.Speaking ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Julius")
                        .addText("Speaking…")
                        .build()
                )
            VoiceEvent.Listening, VoiceEvent.PassiveListening ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Listening")
                        .addText("Waiting for speech…")
                        .build()
                )
            VoiceEvent.Silence -> Unit
        }

        state.currentTranscript.trim().takeIf { it.isNotEmpty() }?.let { draft ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("Draft")
                    .addText(draft.take(200))
                    .build()
            )
        }

        state.messages.takeLast(6).reversed().forEach { msg ->
            val (title, icon) = when (msg.sender) {
                Role.User -> "You" to R.drawable.ic_speaker
                Role.Assistant -> "Julius" to R.drawable.ic_home
            }
            val row = Row.Builder()
                .setTitle(title)
                .addText(msg.text)
                .setImage(
                    CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build()
                )
            if (msg.sender == Role.Assistant) {
                row.setOnClickListener { store.speakAgain(msg.text) }
            }
            listBuilder.addItem(row.build())
        }

        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                lastSearchDraft = searchText
            }

            override fun onSearchSubmitted(searchText: String) {
                lastSearchDraft = searchText
                val trimmed = searchText.trim()
                if (trimmed.isNotBlank()) {
                    lastSearchDraft = ""
                    store.onUserFinishedSpeaking(trimmed)
                    invalidate()
                }
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Speak or type a message, then send")
            .setInitialSearchText(lastSearchDraft ?: "")
            .setItemList(listBuilder.build())
            .build()
    }

    companion object {
        private const val TAG = "AutoJuliusConversationScreen"
    }
}

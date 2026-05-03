package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.R
import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.conversation.Role
import fr.geoking.julius.shared.voice.VoiceEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Auto screen for real-time voice conversation.
 * Displays chat messages and current transcript.
 */
class AutoConversationScreen(
    carContext: CarContext,
    private val store: ConversationStore
) : Screen(carContext) {

    init {
        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            store.state.collectLatest {
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, TAG) {
        val state = store.state.value
        val listBuilder = ItemList.Builder()

        // Show "Listening..." or partial transcript at the top
        if (state.status == VoiceEvent.Listening || state.status == VoiceEvent.Processing || state.status == VoiceEvent.Speaking) {
            val statusText = when (state.status) {
                VoiceEvent.Listening -> if (state.currentTranscript.isBlank()) carContext.getString(R.string.listening) else state.currentTranscript
                VoiceEvent.Processing -> carContext.getString(R.string.thinking)
                VoiceEvent.Speaking -> carContext.getString(R.string.speaking)
                else -> ""
            }

            if (statusText.isNotBlank()) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(statusText)
                        .setImage(
                            CarIcon.Builder(
                                androidx.core.graphics.drawable.IconCompat.createWithResource(
                                    carContext,
                                    if (state.status == VoiceEvent.Listening) R.drawable.ic_speaker else R.drawable.ic_home
                                )
                            ).build()
                        )
                        .build()
                )
            }
        }

        // Show chat history
        state.messages.takeLast(5).reversed().forEach { msg ->
            val isUser = msg.sender == Role.User
            val sender = if (isUser) carContext.getString(R.string.you) else carContext.getString(R.string.julius)
            val icon = if (isUser) R.drawable.ic_speaker else R.drawable.ic_home

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(sender)
                    .addText(msg.text)
                    .setImage(
                        CarIcon.Builder(
                            androidx.core.graphics.drawable.IconCompat.createWithResource(carContext, icon)
                        ).build()
                    )
                    .setOnClickListener {
                        store.speakAgain(msg.text)
                    }
                    .build()
            )
        }

        val isActive = state.status == VoiceEvent.Listening || state.status == VoiceEvent.Speaking || state.status == VoiceEvent.Processing

        val headerAction = if (isActive) {
            Action.Builder()
                .setTitle(carContext.getString(R.string.stop))
                .setOnClickListener { store.stopAllActions() }
                .build()
        } else {
            Action.Builder()
                .setTitle(carContext.getString(R.string.start_listening))
                .setOnClickListener { store.startListening(continuous = true) }
                .build()
        }

        ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.conversation))
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(headerAction)
                    .build()
            )
            .build()
    }

    companion object {
        private const val TAG = "AutoConversationScreen"
    }
}

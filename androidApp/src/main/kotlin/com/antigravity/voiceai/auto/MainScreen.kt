package com.antigravity.voiceai.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.antigravity.voiceai.shared.ConversationStore
import com.antigravity.voiceai.shared.VoiceEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainScreen(
    carContext: CarContext,
    private val store: ConversationStore
) : Screen(carContext) {

    private var currentStatus: String = "Idle"
    private var currentText: String = "Tap mic to start"
    private var isListening: Boolean = false

    init {
        // Observe State
        // In Car App Library, we attach to the Screen's lifecycle
        lifecycleScope.launch {
            store.state.collectLatest { state ->
                isListening = state.status == VoiceEvent.Listening
                currentStatus = state.status.name
                
                currentText = if (state.status == VoiceEvent.Listening) {
                    state.currentTranscript.ifBlank { "Listening..." }
                } else {
                    state.messages.lastOrNull()?.text ?: "Hello"
                }
                
                invalidate() // Refresh UI
            }
        }
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        // Status Row
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(currentStatus)
                .addText(currentText)
                .build()
        )

        // Action Button
        val micAction = Action.Builder()
            .setIcon(CarIcon.APP_ICON) // Placeholder for Mic Icon
            .setTitle(if (isListening) "Stop" else "Speak")
            .setOnClickListener {
                if (isListening) store.stopListening() else store.startListening()
            }
            .build()
            
        paneBuilder.addAction(micAction)

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Voice AI")
            .build()
    }
}

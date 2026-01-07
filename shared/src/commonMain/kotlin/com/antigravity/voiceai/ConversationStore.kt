package com.antigravity.voiceai.shared

import com.antigravity.voiceai.agents.ConversationalAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val status: VoiceEvent = VoiceEvent.Silence,
    val currentTranscript: String = "",
    val lastError: String? = null
)

data class ChatMessage(
    val id: String,
    val sender: Role,
    val text: String
)

enum class Role {
    User, Assistant
}

class ConversationStore(
    private val scope: CoroutineScope,
    private val agent: ConversationalAgent, // Swapped ChatService for Agent
    private val voiceManager: VoiceManager
) {
    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()
    
    // Configurable prompt or context
    var systemPrompt: String = "You are a helpful driving assistant. Keep answers short."

    init {
        voiceManager.events.onEach { event ->
            _state.value = _state.value.copy(status = event)
        }.launchIn(scope)

        voiceManager.transcribedText.onEach { text ->
             // If we get a final transcription (simplification for now), we send it
             if (text.isNotBlank() && _state.value.status != VoiceEvent.Speaking) {
                 _state.value = _state.value.copy(currentTranscript = text)
                 // Auto-send when transcription stabilizes
                 onUserFinishedSpeaking(text)
             }
        }.launchIn(scope)
    }

    // Made public to be called manually or from VoiceManager logic
    fun onUserFinishedSpeaking(text: String) {
        if (text.isBlank()) return
        
        scope.launch {
            try {
                // 1. Add User Message
                val userMsg = ChatMessage("u_${System.currentTimeMillis()}", Role.User, text)
                updateMessages(userMsg)

                // 2. Call AI
                _state.value = _state.value.copy(status = VoiceEvent.Processing, lastError = null)

                val response = agent.process(text) // Returns text + audio?

                // 3. Add AI Message
                val aiMsg = ChatMessage("a_${System.currentTimeMillis()}", Role.Assistant, response.text)
                updateMessages(aiMsg)

                // 4. Speak
                if (response.audio != null) {
                    voiceManager.playAudio(response.audio)
                } else {
                    voiceManager.speak(response.text)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(lastError = e.message, status = VoiceEvent.Silence)
            }
        }
    }
    
    private fun updateMessages(msg: ChatMessage) {
        val current = _state.value.messages
        _state.value = _state.value.copy(messages = current + msg)
    }
    
    fun startListening() {
        voiceManager.startListening()
    }
    
    fun stopListening() {
        voiceManager.stopListening()
    }
}

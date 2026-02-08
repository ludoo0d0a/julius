package fr.geoking.julius.shared

import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.shared.ActionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DetailedError(
    val httpCode: Int?,
    val message: String,
    val timestamp: Long
)

data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val status: VoiceEvent = VoiceEvent.Silence,
    val currentTranscript: String = "",
    val lastError: DetailedError? = null,
    val errorLog: List<DetailedError> = emptyList()
)

data class ChatMessage(
    val id: String,
    val sender: Role,
    val text: String
)

enum class Role {
    User, Assistant
}

open class ConversationStore(
    private val scope: CoroutineScope,
    private val agent: ConversationalAgent, // Swapped ChatService for Agent
    private val voiceManager: VoiceManager,
    private val actionExecutor: ActionExecutor? = null
) {
    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private val maxContextMessages = 12
    
    // Configurable prompt or context
    var systemPrompt: String = "You are a helpful driving assistant. Keep answers short."
    private var preferredSpeechLanguageTag: String? = null

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
        SpeechLanguageResolver.extractPreferredLanguageTag(text)?.let { preferredTag ->
            preferredSpeechLanguageTag = preferredTag
        }
        
        scope.launch {
            try {
                // 1. Add User Message
                val userMsg = ChatMessage("u_${getCurrentTimeMillis()}", Role.User, text)
                updateMessages(userMsg)
                
                // 2. Call AI
                _state.value = _state.value.copy(status = VoiceEvent.Processing, lastError = null)
                
                val contextPrompt = buildContextPrompt(_state.value.messages)
                val response = agent.process(contextPrompt) // Returns text + audio + action
                
                // 3. Execute action if present (from agent or parsed from response)
                var actionResultMessage = ""
                val actionToExecute = response.action ?: ActionParser.parseActionFromResponse(response.text)
                
                if (actionToExecute != null && actionExecutor != null) {
                    try {
                        val actionResult = actionExecutor.executeAction(actionToExecute)
                        actionResultMessage = if (actionResult.success) {
                            "\n[Action executed: ${actionResult.message}]"
                        } else {
                            "\n[Action failed: ${actionResult.message}]"
                        }
                    } catch (e: Exception) {
                        actionResultMessage = "\n[Action error: ${e.message}]"
                    }
                }
                
                // 4. Add AI Message (with action result if any)
                val responseText = response.text + actionResultMessage
                val aiMsg = ChatMessage("a_${getCurrentTimeMillis()}", Role.Assistant, responseText)
                updateMessages(aiMsg)
                
                // 5. Speak (text without action result message for cleaner audio)
                if (response.audio != null) {
                    voiceManager.playAudio(response.audio)
                } else {
                    val speechLanguageTag = preferredSpeechLanguageTag
                        ?: SpeechLanguageResolver.detectLanguageTag(response.text)
                    voiceManager.speak(response.text, speechLanguageTag)
                }
            } catch (e: NetworkException) {
                val error = DetailedError(e.httpCode, e.message ?: "Unknown error", getCurrentTimeMillis())
                val newErrorLog = (_state.value.errorLog + error).takeLast(10)
                _state.value = _state.value.copy(
                    lastError = error,
                    errorLog = newErrorLog,
                    status = VoiceEvent.Silence
                )
            } catch (e: Exception) {
                val error = DetailedError(null, e.message ?: "Unknown error", getCurrentTimeMillis())
                val newErrorLog = (_state.value.errorLog + error).takeLast(10)
                _state.value = _state.value.copy(
                    lastError = error,
                    errorLog = newErrorLog,
                    status = VoiceEvent.Silence
                )
            }
        }
    }
    
    private fun updateMessages(msg: ChatMessage) {
        val current = _state.value.messages
        _state.value = _state.value.copy(messages = current + msg)
    }

    private fun buildContextPrompt(messages: List<ChatMessage>): String {
        val trimmedMessages = messages.takeLast(maxContextMessages)
        val history = trimmedMessages.joinToString("\n") { msg ->
            val speaker = if (msg.sender == Role.User) "User" else "Assistant"
            "$speaker: ${msg.text.trim()}"
        }
        return buildString {
            val prompt = systemPrompt.trim()
            if (prompt.isNotBlank()) {
                append("System: ")
                append(prompt)
                append("\n\n")
            }
            if (history.isNotBlank()) {
                append("Conversation so far:\n")
                append(history)
                append("\n\n")
            }
            append("Please respond to the last user message.")
        }
    }
    
    fun startListening() {
        voiceManager.startListening()
    }
    
    fun stopListening() {
        voiceManager.stopListening()
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }
}

package fr.geoking.julius.shared.conversation

import androidx.compose.runtime.Stable
import fr.geoking.julius.agents.AgentResponse
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.agents.ToolCall
import fr.geoking.julius.shared.action.ActionExecutor
import fr.geoking.julius.shared.action.ActionParser
import fr.geoking.julius.shared.action.ActionType
import fr.geoking.julius.shared.action.AssistantCapabilities
import fr.geoking.julius.shared.action.DeviceAction
import fr.geoking.julius.shared.logging.log
import fr.geoking.julius.shared.platform.getCurrentTimeMillis
import fr.geoking.julius.shared.network.NetworkException
import fr.geoking.julius.shared.voice.LocalTranscriber
import fr.geoking.julius.shared.voice.NoLocalTranscriber
import fr.geoking.julius.shared.voice.SpeechLanguageResolver
import fr.geoking.julius.shared.voice.SttEnginePreference
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailedError(
    val httpCode: Int?,
    val message: String,
    val timestamp: Long
)

@Stable
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
    val text: String,
    val timestamp: Long
)

enum class Role {
    User, Assistant
}

@Stable
open class ConversationStore(
    private val scope: CoroutineScope,
    private val agent: ConversationalAgent, // Swapped ChatService for Agent
    val voiceManager: VoiceManager,
    private val actionExecutor: ActionExecutor? = null,
    private val initialSpeechLanguageTag: String? = null,
    private val localTranscriber: LocalTranscriber = NoLocalTranscriber,
    private val sttPreference: () -> SttEnginePreference = { SttEnginePreference.NativeOnly },
    private val persistence: MessagePersistence? = null
) {
    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    private var activeProcessingJob: Job? = null

    private val _userName = MutableStateFlow<String?>(null)
    var userName: String?
        get() = _userName.value
        set(value) { _userName.value = value }

    // Configurable prompt or context
    var systemPrompt: String = "You are a helpful driving assistant. Keep answers short."
    private val _preferredSpeechLanguageTag = MutableStateFlow(initialSpeechLanguageTag)
    val preferredSpeechLanguageTag: StateFlow<String?> = _preferredSpeechLanguageTag.asStateFlow()

    /** Text to show in the voice UI: current transcript, last assistant message, or default greeting. */
    val displayText: StateFlow<String> = combine(_state, _userName, _preferredSpeechLanguageTag) { s, name, lang ->
        when {
            s.currentTranscript.isNotBlank() -> s.currentTranscript
            else -> s.messages.lastOrNull()?.text ?: getGreeting(name, lang)
        }
    }.stateIn(scope, SharingStarted.Eagerly, "Hi, how can I help you")

    private fun getGreeting(name: String?, lang: String?): String {
        val baseLang = lang?.take(2)?.lowercase()
        return when (baseLang) {
            "fr" -> if (name != null) "Bonjour $name, comment puis-je vous aider ?" else "Bonjour, comment puis-je vous aider ?"
            "es" -> if (name != null) "Hola $name, ¿cómo puedo ayudarte?" else "Hola, ¿cómo puedo ayudarte?"
            "de" -> if (name != null) "Hallo $name, wie kann ich dir helfen?" else "Hallo, wie kann ich dir helfen?"
            "it" -> if (name != null) "Ciao $name, come posso aiutarti?" else "Ciao, come posso aiutarti?"
            "pt" -> if (name != null) "Olá $name, como posso ajudar?" else "Olá, como posso ajudar?"
            else -> if (name != null) "Hello $name, how can I help?" else "Hi, how can I help you"
        }
    }

    private val maxContextMessages = 12

    /**
     * When false, incoming final transcriptions will NOT be auto-sent to the conversational agent.
     * This is useful for flows that reuse STT (e.g. Android Auto "Jules" prompt creation).
     */
    var autoSendFinalTranscripts: Boolean = true

    init {
        scope.launch {
            val p = persistence ?: return@launch
            try {
                val lastMessages = withContext(Dispatchers.Default) {
                    val oneWeekAgo = getCurrentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                    p.cleanupOldMessages(oneWeekAgo)
                    p.loadMessages(limit = 20)
                }
                _state.value = _state.value.copy(messages = lastMessages)
            } catch (e: Exception) {
                log.e(e) { "Failed to load persisted messages" }
            }
        }

        voiceManager.setTranscriber { audioData ->
            when (sttPreference()) {
                SttEnginePreference.NativeOnly ->
                    if (agent.isSttSupported) agent.transcribe(audioData) else null
                SttEnginePreference.LocalOnly ->
                    localTranscriber.transcribe(audioData)
                SttEnginePreference.LocalFirst ->
                    localTranscriber.transcribe(audioData)
                        ?: if (agent.isSttSupported) agent.transcribe(audioData) else null
            }
        }

        voiceManager.events.onEach { event ->
            // Clear transcript when entering Listening so partial results show with fresh letter-by-letter animation
            val nextTranscript = if (event == VoiceEvent.Listening) "" else _state.value.currentTranscript
            _state.value = _state.value.copy(status = event, currentTranscript = nextTranscript)
        }.launchIn(scope)

        voiceManager.partialText.onEach { text ->
            // Update live transcript while listening or processing (if it arrives late)
            if (text.isNotBlank()) {
                _state.value = _state.value.copy(currentTranscript = text)
            }
        }.launchIn(scope)

        voiceManager.transcribedText.onEach { text ->
             // If we get a final transcription, we send it
             if (text.isNotBlank()) {
                 _state.value = _state.value.copy(currentTranscript = text)
                 if (isStopCommand(text)) {
                     stopAllActions()
                     return@onEach
                 }
                 // Auto-send when transcription stabilizes (unless disabled by a feature flow)
                 if (autoSendFinalTranscripts) {
                     onUserFinishedSpeaking(text)
                 }
             }
        }.launchIn(scope)
    }

    private fun isStopCommand(text: String): Boolean {
        val normalized = text
            .lowercase()
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
        return Regex("\\bstop\\b").containsMatchIn(normalized)
    }

    fun stopAllActions() {
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        _state.value = _state.value.copy(
            status = VoiceEvent.Silence,
            currentTranscript = ""
        )
    }

    // Made public to be called manually or from VoiceManager logic
    fun onUserFinishedSpeaking(text: String) {
        if (text.isBlank()) return

        // 1. Explicit language switch (e.g. "speak in French")
        SpeechLanguageResolver.extractPreferredLanguageTag(text)?.let { preferredTag ->
            _preferredSpeechLanguageTag.value = preferredTag
        } ?: run {
            // 2. Dynamic detection based on current input
            SpeechLanguageResolver.detectLanguageTag(text)?.let { detectedTag ->
                _preferredSpeechLanguageTag.value = detectedTag
            }
        }
        
        activeProcessingJob?.cancel()
        activeProcessingJob = scope.launch {
            try {
                // 1. Add User Message
                val userMsg = ChatMessage("u_${getCurrentTimeMillis()}", Role.User, text, getCurrentTimeMillis())
                updateMessages(userMsg)
                
                // 2. Call AI (offload to Default dispatcher to avoid blocking main thread)
                _state.value = _state.value.copy(status = VoiceEvent.Processing, lastError = null)

                val response = withContext(Dispatchers.Default) {
                    var contextPrompt = buildContextPrompt(_state.value.messages)
                    var resp = agent.process(contextPrompt)
                    // 3. Tool Calling Loop (if agent supports it and returned tool calls)
                    var toolIteration = 0
                    while (resp.toolCalls != null && toolIteration < 5) {
                        toolIteration++
                        val toolResults = resp.toolCalls.map { toolCall ->
                            val result = withContext(Dispatchers.Main.immediate) {
                                actionExecutor?.executeAction(toolCall.action)
                            }
                            val status = if (result?.success == true) "SUCCESS" else "FAILED"
                            val message = result?.message ?: "Action executor not available"
                            "Tool ${toolCall.action.type} result: $status - $message"
                        }.joinToString("\n")
                        contextPrompt += "\n\nTool Results:\n$toolResults\n\nPlease provide your final response based on these results."
                        resp = agent.process(contextPrompt)
                    }
                    resp
                }

                // 4. Execute single action if present (from agent or parsed from response)
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
                
                // 5. Add AI Message (with action result if any)
                val responseText = response.text + actionResultMessage
                val aiMsg = ChatMessage("a_${getCurrentTimeMillis()}", Role.Assistant, responseText, getCurrentTimeMillis())
                updateMessages(aiMsg)
                _state.value = _state.value.copy(currentTranscript = "")

                // 6. Speak (text without action result message for cleaner audio)
                if (response.audio != null) {
                    voiceManager.playAudio(response.audio)
                } else {
                    val speechLanguageTag = _preferredSpeechLanguageTag.value
                        ?: SpeechLanguageResolver.detectLanguageTag(response.text)
                    voiceManager.speak(response.text, speechLanguageTag)
                }
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(status = VoiceEvent.Silence)
            } catch (e: NetworkException) {
                val msg = e.message ?: "Unknown network error"
                log.e(e) { "Network error: $msg (httpCode=${e.httpCode})" }
                val error = DetailedError(e.httpCode, msg, getCurrentTimeMillis())
                val newErrorLog = (_state.value.errorLog + error).takeLast(10)
                _state.value = _state.value.copy(
                    lastError = error,
                    errorLog = newErrorLog,
                    status = VoiceEvent.Silence
                )
            } catch (e: Exception) {
                val msg = buildDetailedErrorMessage(e)
                log.e(e) { "Agent/voice error: $msg" }
                val error = DetailedError(null, msg, getCurrentTimeMillis())
                val newErrorLog = (_state.value.errorLog + error).takeLast(10)
                _state.value = _state.value.copy(
                    lastError = error,
                    errorLog = newErrorLog,
                    status = VoiceEvent.Silence
                )
            }
        }
    }
    
    private fun buildDetailedErrorMessage(e: Exception): String {
        val base = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
        val type = e.toString().substringBefore(":").trim().ifBlank { "Exception" }
        return if (type != "Exception" && !base.contains(type)) "$type: $base" else base
    }

    private fun updateMessages(msg: ChatMessage) {
        val current = _state.value.messages
        _state.value = _state.value.copy(messages = current + msg)
        scope.launch {
            val p = persistence ?: return@launch
            try {
                withContext(Dispatchers.Default) {
                    p.saveMessage(msg)
                }
            } catch (e: Exception) {
                log.e(e) { "Failed to persist message (id=${msg.id})" }
            }
        }
    }

    private fun buildContextPrompt(messages: List<ChatMessage>): String {
        val trimmedMessages = messages.takeLast(maxContextMessages)
        val lastUserText = trimmedMessages.lastOrNull { it.sender == Role.User }?.text?.trim().orEmpty()
        val capabilitiesInjection = if (AssistantCapabilities.isCapabilitiesHelpQuery(lastUserText)) {
            AssistantCapabilities.llmInstructionForCapabilitiesOverview(_preferredSpeechLanguageTag.value)
        } else {
            null
        }
        val history = trimmedMessages.joinToString("\n") { msg ->
            val speaker = if (msg.sender == Role.User) "User" else "Assistant"
            "$speaker: ${msg.text.trim()}"
        }
        return buildString {
            val langName = SpeechLanguageResolver.getLanguageName(_preferredSpeechLanguageTag.value)
            val langInstruction = if (langName != null) " Respond in $langName." else ""
            val nameInfo = if (_userName.value != null) " The user's name is $userName." else ""

            val prompt = buildString {
                append("$systemPrompt$nameInfo$langInstruction".trim())
                if (capabilitiesInjection != null) {
                    if (isNotEmpty()) append("\n\n")
                    append(capabilitiesInjection)
                }
            }
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

    fun clearTranscript() {
        _state.value = _state.value.copy(currentTranscript = "")
    }

    /** Speaks an existing message again without sending it to the agent. */
    fun speakAgain(text: String, isInterruptible: Boolean = true) {
        if (text.isBlank()) return
        val speechLanguageTag = _preferredSpeechLanguageTag.value
            ?: SpeechLanguageResolver.detectLanguageTag(text)
        voiceManager.speak(text, speechLanguageTag, isInterruptible)
    }

    fun clearConversation() {
        _state.value = _state.value.copy(
            messages = emptyList(),
            currentTranscript = "",
            lastError = null
        )
        scope.launch {
            val p = persistence ?: return@launch
            try {
                withContext(Dispatchers.Default) {
                    p.clearMessages()
                }
            } catch (e: Exception) {
                log.e(e) { "Failed to clear persisted messages" }
            }
        }
    }

    /** Records an error into [ConversationState.errorLog] and [lastError] for later debugging (e.g. map provider failures). */
    fun recordError(httpCode: Int?, message: String) {
        val error = DetailedError(httpCode, message, getCurrentTimeMillis())
        val newErrorLog = (_state.value.errorLog + error).takeLast(10)
        _state.value = _state.value.copy(
            lastError = error,
            errorLog = newErrorLog
        )
    }

    /** Triggers a descriptive connection status report as an assistant message. */
    fun reportNetworkStatus() {
        scope.launch {
            val result = actionExecutor?.executeAction(DeviceAction(ActionType.GET_NETWORK_STATUS))
            if (result != null && result.success) {
                val aiMsg = ChatMessage("a_net_${getCurrentTimeMillis()}", Role.Assistant, result.message, getCurrentTimeMillis())
                updateMessages(aiMsg)
                speakAgain(result.message)
            }
        }
    }
}

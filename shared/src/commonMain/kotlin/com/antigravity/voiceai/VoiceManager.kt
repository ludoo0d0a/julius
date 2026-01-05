package com.antigravity.voiceai.shared

import kotlinx.coroutines.flow.Flow

enum class VoiceEvent {
    Listening,
    Processing,
    Silence,
    Speaking
}

interface VoiceManager {
    val events: Flow<VoiceEvent>
    val transcribedText: Flow<String>

    fun startListening()
    fun stopListening()
    fun speak(text: String)
    fun playAudio(bytes: ByteArray)
    fun stopSpeaking()
}

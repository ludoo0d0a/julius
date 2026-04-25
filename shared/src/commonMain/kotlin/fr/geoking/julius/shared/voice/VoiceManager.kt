package fr.geoking.julius.shared.voice

import kotlinx.coroutines.flow.Flow

enum class VoiceEvent {
    Listening,
    PassiveListening,
    Processing,
    Silence,
    Speaking
}

interface VoiceManager {
    val events: Flow<VoiceEvent>
    val transcribedText: Flow<String>
    val partialText: Flow<String>

    fun startListening(continuous: Boolean = false)
    fun stopListening()
    fun setTranscriber(transcriber: suspend (ByteArray) -> String?)
    fun speak(text: String, languageTag: String? = null, isInterruptible: Boolean = true)
    fun playAudio(bytes: ByteArray)
    fun stopSpeaking()
}

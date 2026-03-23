package fr.geoking.julius.shared

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
    val partialText: Flow<String>

    fun startListening()
    fun stopListening()
    fun setTranscriber(transcriber: suspend (ByteArray) -> String?)
    fun speak(text: String, languageTag: String? = null, isInterruptible: Boolean = true)
    fun playAudio(bytes: ByteArray)
    fun stopSpeaking()
}

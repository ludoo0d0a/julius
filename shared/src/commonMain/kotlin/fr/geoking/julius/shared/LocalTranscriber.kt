package fr.geoking.julius.shared

/**
 * Local (on-device) speech-to-text for the car mic path.
 * Used when [SttEnginePreference] is LocalOnly or LocalFirst.
 */
interface LocalTranscriber {
    /**
     * Transcribes raw audio to text.
     * @param audioData PCM audio (e.g. 16 kHz 16-bit mono); format is implementation-defined.
     * @return Transcribed text, or null if not available / failed.
     */
    suspend fun transcribe(audioData: ByteArray): String?
}

/**
 * No-op local transcriber; always returns null.
 * Used when no local STT (e.g. Vosk) is available.
 */
object NoLocalTranscriber : LocalTranscriber {
    override suspend fun transcribe(audioData: ByteArray): String? = null
}

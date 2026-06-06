package fr.geoking.julius.shared.voice

/**
 * Result of a transcription attempt.
 * @param text The transcribed text.
 * @param isFinal True if the transcriber has detected the end of speech.
 */
data class TranscriptionResult(val text: String, val isFinal: Boolean)

/**
 * Local (on-device) speech-to-text for the car mic path.
 * Used when [SttEnginePreference] is LocalOnly or LocalFirst.
 */
interface LocalTranscriber {
    /**
     * Transcribes raw audio to text.
     * @param audioData PCM audio (e.g. 16 kHz 16-bit mono); format is implementation-defined.
     * @return Transcribed text and finality status, or null if not available / failed.
     */
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult?

    /**
     * Resets the transcriber state (e.g. for starting a new streaming session).
     */
    fun reset() {}

    /**
     * Flushes any pending recognition result after streaming stops (e.g. last partial phrase).
     */
    fun flushPendingFinal(): String? = null

    /**
     * Whether the local transcriber is available (e.g. model loaded).
     */
    fun isAvailable(): Boolean = false

    /**
     * Incremental streaming transcription for live partial/final results.
     * Default falls back to [transcribe] (batch mode).
     */
    suspend fun transcribeStreamingChunk(audioData: ByteArray): TranscriptionResult? =
        transcribe(audioData)
}

/**
 * No-op local transcriber; always returns null.
 * Used when no local STT (e.g. Vosk) is available.
 */
object NoLocalTranscriber : LocalTranscriber {
    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult? = null
    override fun reset() {}
    override fun isAvailable(): Boolean = false
}

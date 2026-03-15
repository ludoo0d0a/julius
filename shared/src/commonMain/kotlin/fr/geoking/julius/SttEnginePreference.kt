package fr.geoking.julius.shared

/**
 * Controls how speech-to-text is chosen for the car mic path (transcriber callback).
 * Phone path always uses Android SpeechRecognizer.
 */
enum class SttEnginePreference {
    /** Use only local (Vosk) STT; no fallback to agent. */
    LocalOnly,

    /** Try local (Vosk) first; if null/blank, use agent.transcribe when supported. */
    LocalFirst,

    /** Do not use Vosk; use only agent STT when supported. */
    NativeOnly
}

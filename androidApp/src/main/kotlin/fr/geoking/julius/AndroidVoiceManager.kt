package fr.geoking.julius

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.shared.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class AndroidVoiceManager(
    private val context: Context
) : VoiceManager, RecognitionListener, TextToSpeech.OnInitListener {

    private val _events = MutableStateFlow(VoiceEvent.Silence)
    override val events: StateFlow<VoiceEvent> = _events.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    override val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(context.mainLooper)
    private var ttsReady: Boolean = false
    private var currentLanguageTag: String? = null
    private var pendingLanguageTag: String? = null
    private var isRecognizerActive: Boolean = false
    private var isBargeInActive: Boolean = false
    private var bargeInRestartScheduled: Boolean = false
    
    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Keep event in Speaking while TTS is active
                if (_events.value != VoiceEvent.Speaking) {
                    _events.value = VoiceEvent.Speaking
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (_events.value == VoiceEvent.Speaking) {
                        _events.value = VoiceEvent.Silence
                    }
                    stopBargeInListening()
                }
            }

            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }
        })
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnCompletionListener { 
            // Only reset to Silence if we were speaking, not if we're listening
            if (_events.value == VoiceEvent.Speaking) {
                _events.value = VoiceEvent.Silence
            }
            stopBargeInListening()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            setTtsLanguage(pendingLanguageTag)
            pendingLanguageTag = null
        }
    }

    override fun startListening() {
        startListeningInternal(stopOutputs = true, bargeIn = false)
    }

    override fun stopListening() {
        mainHandler.post {
            speechRecognizer?.stopListening()
            isRecognizerActive = false
            isBargeInActive = false
            if (_events.value == VoiceEvent.Listening) {
                _events.value = VoiceEvent.Silence
            }
        }
    }
    
    override fun speak(text: String, languageTag: String?) {
        _events.value = VoiceEvent.Speaking
        updateTtsLanguage(languageTag)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_ai_utterance")
        startBargeInListening()
    }

    override fun playAudio(bytes: ByteArray) {
        _events.value = VoiceEvent.Speaking
        try {
            // Write to temp file
            val tempFile = File.createTempFile("voice_ai_temp", ".mp3", context.cacheDir)
            val fos = FileOutputStream(tempFile)
            fos.write(bytes)
            fos.close()
            
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(tempFile.absolutePath)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            startBargeInListening()
        } catch (e: Exception) {
            e.printStackTrace()
            _events.value = VoiceEvent.Silence // Reset on error
        }
    }

    override fun stopSpeaking() {
        tts?.stop()
        mediaPlayer?.stop()
        stopBargeInListening()
        _events.value = VoiceEvent.Silence
    }

    private fun updateTtsLanguage(languageTag: String?) {
        if (languageTag.isNullOrBlank()) return
        if (!ttsReady) {
            pendingLanguageTag = languageTag
            return
        }
        if (languageTag == currentLanguageTag) return
        setTtsLanguage(languageTag)
    }

    private fun setTtsLanguage(languageTag: String?) {
        val desiredLocale = resolveLocale(languageTag)
        if (applyLocale(desiredLocale)) return

        if (!languageTag.isNullOrBlank()) {
            val defaultLocale = Locale.getDefault()
            if (defaultLocale != desiredLocale && applyLocale(defaultLocale)) return
        }

        applyLocale(Locale.US)
    }

    private fun resolveLocale(languageTag: String?): Locale {
        if (languageTag.isNullOrBlank()) return Locale.getDefault()
        val locale = Locale.forLanguageTag(languageTag)
        return if (locale == Locale.ROOT) Locale.getDefault() else locale
    }

    private fun applyLocale(locale: Locale): Boolean {
        val ttsInstance = tts ?: return false
        val availability = ttsInstance.isLanguageAvailable(locale)
        return if (availability >= TextToSpeech.LANG_AVAILABLE) {
            ttsInstance.language = locale
            currentLanguageTag = locale.toLanguageTag()
            true
        } else {
            false
        }
    }

    private fun startListeningInternal(stopOutputs: Boolean, bargeIn: Boolean) {
        val intent = buildRecognizerIntent()
        mainHandler.post {
            val recognizer = getOrCreateRecognizer() ?: return@post
            if (stopOutputs) {
                mediaPlayer?.stop()
                tts?.stop()
            }
            if (isRecognizerActive) {
                if (isBargeInActive == bargeIn) {
                    if (!bargeIn) {
                        _events.value = VoiceEvent.Listening
                    }
                    return@post
                }
                recognizer.cancel()
            }
            isRecognizerActive = true
            isBargeInActive = bargeIn
            bargeInRestartScheduled = false
            recognizer.startListening(intent)
            if (!bargeIn) {
                _events.value = VoiceEvent.Listening
            }
        }
    }

    private fun startBargeInListening() {
        startListeningInternal(stopOutputs = false, bargeIn = true)
    }

    private fun stopBargeInListening() {
        if (!isBargeInActive) return
        isBargeInActive = false
        if (isRecognizerActive) {
            speechRecognizer?.cancel()
            isRecognizerActive = false
        }
    }

    private fun scheduleBargeInRestart() {
        if (bargeInRestartScheduled) return
        bargeInRestartScheduled = true
        mainHandler.postDelayed({
            bargeInRestartScheduled = false
            if (_events.value == VoiceEvent.Speaking) {
                startBargeInListening()
            } else {
                isBargeInActive = false
            }
        }, BARGE_IN_RESTART_DELAY_MS)
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        }
        return speechRecognizer
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // RecognitionListener
    override fun onReadyForSpeech(params: Bundle?) {
        // Keep Speaking state when listening for barge-in
        if (!(isBargeInActive && _events.value == VoiceEvent.Speaking)) {
            _events.value = VoiceEvent.Listening
        }
    }
    override fun onBeginningOfSpeech() {
        if (isBargeInActive && _events.value == VoiceEvent.Speaking) {
            tts?.stop()
            mediaPlayer?.stop()
            isBargeInActive = false
        }
        _events.value = VoiceEvent.Listening 
    }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        if (!(isBargeInActive && _events.value == VoiceEvent.Speaking)) {
            _events.value = VoiceEvent.Processing
        }
    }
    override fun onError(error: Int) {
        isRecognizerActive = false
        if (isBargeInActive && _events.value == VoiceEvent.Speaking) {
            scheduleBargeInRestart()
            return
        }
        isBargeInActive = false
        _events.value = VoiceEvent.Silence
    }
    override fun onResults(results: Bundle?) {
        isRecognizerActive = false
        if (isBargeInActive && _events.value == VoiceEvent.Speaking) {
            scheduleBargeInRestart()
            return
        }
        isBargeInActive = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        _transcribedText.value = text 
        // Note: ConversationStore observes this flow and triggers processing
    }
    override fun onPartialResults(partialResults: Bundle?) {
        if (isBargeInActive && _events.value == VoiceEvent.Speaking) return
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        // Optional: emit partials
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        private const val BARGE_IN_RESTART_DELAY_MS = 250L
    }
}

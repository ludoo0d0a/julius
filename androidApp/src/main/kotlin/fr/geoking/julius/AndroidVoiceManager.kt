package fr.geoking.julius

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
    private var ttsReady: Boolean = false
    private var currentLanguageTag: String? = null
    private var pendingLanguageTag: String? = null
    
    init {
        tts = TextToSpeech(context, this)
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setOnCompletionListener { 
            // Only reset to Silence if we were speaking, not if we're listening
            if (_events.value == VoiceEvent.Speaking) {
                _events.value = VoiceEvent.Silence
            }
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
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        android.os.Handler(context.mainLooper).post {
            mediaPlayer?.stop()
            tts?.stop()
            speechRecognizer?.startListening(intent)
            _events.value = VoiceEvent.Listening
        }
    }

    override fun stopListening() {
        android.os.Handler(context.mainLooper).post {
            speechRecognizer?.stopListening()
        }
    }
    
    override fun speak(text: String, languageTag: String?) {
        _events.value = VoiceEvent.Speaking
        updateTtsLanguage(languageTag)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_ai_utterance")
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
        } catch (e: Exception) {
            e.printStackTrace()
            _events.value = VoiceEvent.Silence // Reset on error
        }
    }

    override fun stopSpeaking() {
        tts?.stop()
        mediaPlayer?.stop()
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

    // RecognitionListener
    override fun onReadyForSpeech(params: Bundle?) {
        // Ensure icon stays red as soon as recognizer is ready to listen
        _events.value = VoiceEvent.Listening
    }
    override fun onBeginningOfSpeech() {
        _events.value = VoiceEvent.Listening 
    }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        _events.value = VoiceEvent.Processing
    }
    override fun onError(error: Int) {
        _events.value = VoiceEvent.Silence
    }
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        _transcribedText.value = text 
        // Note: ConversationStore observes this flow and triggers processing
    }
    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        // Optional: emit partials
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

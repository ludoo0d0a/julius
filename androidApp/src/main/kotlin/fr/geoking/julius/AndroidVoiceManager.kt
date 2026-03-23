package fr.geoking.julius

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.shared.VoiceManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.OptIn
import java.util.Locale

@OptIn(DelicateCoroutinesApi::class)
@UnstableApi
class AndroidVoiceManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) : VoiceManager, RecognitionListener, TextToSpeech.OnInitListener {

    private var carContext: CarContext? = null
    private var isRecording = false
    private var wakeWordDetectionJob: kotlinx.coroutines.Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    fun setCarContext(carContext: CarContext) {
        this.carContext = carContext
    }

    companion object {
        private const val TAG = "Julius/Voice"
        private const val BARGE_IN_RESTART_DELAY_MS = 250L
        private const val RMS_LOG_INTERVAL = 20
        private const val BUFFER_LOG_INTERVAL = 50
    }

    private val _events = MutableStateFlow(VoiceEvent.Silence)
    override val events: StateFlow<VoiceEvent> = _events.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    override val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(context.mainLooper)
    private var ttsReady: Boolean = false
    private var currentLanguageTag: String? = null
    private var pendingLanguageTag: String? = null
    private var isRecognizerActive: Boolean = false
    private var isBargeInActive: Boolean = false
    private var isHeyJuliusKeywordBargeIn: Boolean = false
    private var heyJuliusActivationInProgress: Boolean = false
    private var bargeInRestartScheduled: Boolean = false
    private var rmsCallCount: Int = 0
    private var bufferCallCount: Int = 0
    private var transcriber: (suspend (ByteArray) -> String?)? = null

    private val player = object : SimpleBasePlayer(context.mainLooper) {
        fun notifyStateChanged() {
            invalidateState()
        }

        override fun getState(): State {
            val playbackState = when (_events.value) {
                VoiceEvent.Listening, VoiceEvent.Speaking, VoiceEvent.Processing -> Player.STATE_READY
                VoiceEvent.Silence -> Player.STATE_IDLE
            }
            val playWhenReady = _events.value != VoiceEvent.Silence

            return State.Builder()
                .setAvailableCommands(
                    Player.Commands.Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_STOP)
                        .build()
                )
                .setPlaybackState(playbackState)
                .setPlayWhenReady(playWhenReady, Player.PLAYBACK_SUPPRESSION_REASON_NONE)
                .setPlaylist(
                    listOf(
                        MediaItemData.Builder("julius_voice")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Julius")
                                    .setArtist(when (_events.value) {
                                        VoiceEvent.Listening -> "Listening..."
                                        VoiceEvent.Speaking -> "Speaking..."
                                        VoiceEvent.Processing -> "Thinking..."
                                        VoiceEvent.Silence -> "Idle"
                                    })
                                    .setArtworkUri(Uri.parse("android.resource://${context.packageName}/drawable/ic_launcher_foreground"))
                                    .setIsPlayable(true)
                                    .build()
                            )
                            .build()
                    )
                )
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) {
                startListening()
            } else {
                stopSpeaking()
                stopListening()
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            stopSpeaking()
            stopListening()
            return Futures.immediateVoidFuture()
        }
    }

    var mediaLibrarySession: MediaLibraryService.MediaLibrarySession? = null
        private set

    fun releaseMediaSession() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
    }

    fun initMediaSession(service: MediaLibraryService) {
        if (mediaLibrarySession != null) return

        mediaLibrarySession = MediaLibraryService.MediaLibrarySession.Builder(
            service,
            player,
            object : MediaLibraryService.MediaLibrarySession.Callback {
                override fun onGetLibraryRoot(
                    session: MediaLibraryService.MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: MediaLibraryService.LibraryParams?
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val rootItem = MediaItem.Builder()
                        .setMediaId("root")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("Julius")
                                .setIsPlayable(false)
                                .setIsBrowsable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .build()
                        )
                        .build()
                    return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
                }

                override fun onGetItem(
                    session: MediaLibraryService.MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val item = MediaItem.Builder()
                        .setMediaId("start_julius")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("Start Julius")
                                .setIsPlayable(true)
                                .setIsBrowsable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .build()
                        )
                        .build()
                    return Futures.immediateFuture(LibraryResult.ofItem(item, null))
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<List<MediaItem>> {
                    startListening()
                    return Futures.immediateFuture(mediaItems)
                }
            }
        ).build()
    }

    init {
        observeSettings()
        // Defer TTS and MediaPlayer creation off the main thread to avoid ANR.
        // TextToSpeech constructor can block for several seconds on first use.
        GlobalScope.launch(Dispatchers.IO) {
            val newTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    mainHandler.post {
                        ttsReady = true
                        setTtsLanguage(pendingLanguageTag)
                        pendingLanguageTag = null
                    }
                }
            }
            val newMediaPlayer = MediaPlayer()
            withContext(Dispatchers.Main) {
                tts = newTts
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (_events.value != VoiceEvent.Speaking) {
                            _events.value = VoiceEvent.Speaking
                            player.notifyStateChanged()
                        }
                        startBargeInWhileSpeakingIfNeeded()
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (_events.value == VoiceEvent.Speaking) {
                                _events.value = VoiceEvent.Silence
                                player.notifyStateChanged()
                                abandonAudioFocus()
                            }
                            stopBargeInListening()
                        }
                    }

                    @Deprecated("Overrides deprecated UtteranceProgressListener.onError", ReplaceWith("onDone(utteranceId)"))
                    override fun onError(utteranceId: String?) {
                        onDone(utteranceId)
                    }
                })
                mediaPlayer = newMediaPlayer
                mediaPlayer?.setOnCompletionListener {
                    if (_events.value == VoiceEvent.Speaking) {
                        _events.value = VoiceEvent.Silence
                        player.notifyStateChanged()
                        abandonAudioFocus()
                    }
                    stopBargeInListening()
                }
            }
        }
    }

    @Deprecated("Implements deprecated TextToSpeech.OnInitListener", ReplaceWith("use TextToSpeech constructor callback"))
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            setTtsLanguage(pendingLanguageTag)
            pendingLanguageTag = null
        }
    }

    override fun startListening() {
        Log.d(TAG, "mic on: startListening()")
        requestAudioFocus()
        val currentCarContext = carContext
        // Use the car's microphone whenever a car context is available and the setting is enabled.
        // We no longer gate this on a specific Gradle flavor so that non-flavored builds work.
        if (currentCarContext != null && settingsManager.settings.value.useCarMic) {
            startCarListening(currentCarContext)
        } else {
            startListeningInternal(stopOutputs = true, bargeIn = false)
        }
    }

    private fun startCarListening(carContext: CarContext) {
        if (isRecording) return
        isRecording = true
        _events.value = VoiceEvent.Listening
        player.notifyStateChanged()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val carAudioRecord = CarAudioRecord.create(carContext)
                carAudioRecord.startRecording()
                val baos = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)

                Log.d(TAG, "Car recording started")

                while (isRecording) {
                    val read = carAudioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        baos.write(buffer, 0, read)
                        // In a real app, we would do VAD here to auto-stop
                    } else if (read < 0) {
                        break
                    }
                }

                carAudioRecord.stopRecording()
                val audioData = baos.toByteArray()
                Log.d(TAG, "Car recording stopped, size: ${audioData.size}")

                if (audioData.isNotEmpty()) {
                    processCarAudio(audioData)
                } else {
                    mainHandler.post {
                        _events.value = VoiceEvent.Silence
                        player.notifyStateChanged()
                        abandonAudioFocus()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Car recording failed", e)
                isRecording = false
                mainHandler.post {
                    _events.value = VoiceEvent.Silence
                    player.notifyStateChanged()
                }
            }
        }
    }

    private suspend fun processCarAudio(audioData: ByteArray) {
        mainHandler.post {
            _events.value = VoiceEvent.Processing
            player.notifyStateChanged()
        }

        val text = transcriber?.invoke(audioData) ?: ""
        Log.d(TAG, "Car transcription: \"$text\"")

        mainHandler.post {
            if (text.isNotBlank()) {
                _transcribedText.value = text
            } else {
                _events.value = VoiceEvent.Silence
                player.notifyStateChanged()
            }
        }
    }

    override fun setTranscriber(transcriber: suspend (ByteArray) -> String?) {
        this.transcriber = transcriber
    }

    override fun stopListening() {
        Log.d(TAG, "mic off: stopListening()")
        if (isRecording) {
            isRecording = false
        } else {
            mainHandler.post {
                speechRecognizer?.stopListening()
            }
        }
        if (_events.value != VoiceEvent.Speaking && _events.value != VoiceEvent.Processing) {
            abandonAudioFocus()
        }
    }

    private fun cancelActiveRecognitionForSpeechOutput() {
        isBargeInActive = false
        bargeInRestartScheduled = false
        if (isRecording) {
            isRecording = false
        }
        if (isRecognizerActive) {
            isRecognizerActive = false
            mainHandler.post { speechRecognizer?.cancel() }
        }
    }
    
    override fun speak(text: String, languageTag: String?, isInterruptible: Boolean) {
        // With interrupt mode OFF, cancel recognition so TTS is not cut by leftover STT.
        if (settingsManager.settings.value.speakingInterruptMode == SpeakingInterruptMode.OFF || !isInterruptible) {
            cancelActiveRecognitionForSpeechOutput()
        }
        requestAudioFocus()
        _events.value = VoiceEvent.Speaking
        player.notifyStateChanged()
        updateTtsLanguage(languageTag)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_ai_utterance")
        if (isInterruptible) {
            startBargeInWhileSpeakingIfNeeded()
        }
    }

    override fun playAudio(bytes: ByteArray) {
        // Same rationale as speak().
        if (settingsManager.settings.value.speakingInterruptMode == SpeakingInterruptMode.OFF) {
            cancelActiveRecognitionForSpeechOutput()
        }
        requestAudioFocus()
        _events.value = VoiceEvent.Speaking
        player.notifyStateChanged()
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
        startBargeInWhileSpeakingIfNeeded()
    }

    override fun stopSpeaking() {
        tts?.stop()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        stopBargeInListening()
        _events.value = VoiceEvent.Silence
        player.notifyStateChanged()
        abandonAudioFocus()
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
        Log.d(TAG, "startListeningInternal stopOutputs=$stopOutputs bargeIn=$bargeIn")
        mainHandler.post {
            val recognizer = getOrCreateRecognizer() ?: run {
                Log.e(TAG, "getOrCreateRecognizer() returned null, cannot start")
                return@post
            }

            if (stopOutputs) {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
                tts?.stop()
            }
            if (isRecognizerActive) {
                recognizer.cancel()
            }
            isRecognizerActive = true
            isBargeInActive = bargeIn
            bargeInRestartScheduled = false
            rmsCallCount = 0
            bufferCallCount = 0

            // Don't clear transcript/partial text when running the hidden barge-in recognizer
            // (it would wipe the UI while Julius is speaking).
            if (!bargeIn) {
                _partialText.value = ""
                _transcribedText.value = ""
            }

            Log.d(TAG, "SpeechRecognizer.startListening() called")
            recognizer.startListening(intent)
            if (!bargeIn) {
                _events.value = VoiceEvent.Listening
                player.notifyStateChanged()
            }
        }
    }

    private fun startBargeInListening() {
        startListeningInternal(stopOutputs = false, bargeIn = true)
    }

    private fun stopBargeInListening() {
        if (!isBargeInActive) return
        isBargeInActive = false
        isHeyJuliusKeywordBargeIn = false
        heyJuliusActivationInProgress = false
        if (isRecognizerActive) {
            speechRecognizer?.cancel()
            isRecognizerActive = false
        }
    }

    private fun startBargeInWhileSpeakingIfNeeded() {
        when (settingsManager.settings.value.speakingInterruptMode) {
            SpeakingInterruptMode.OFF -> return
            SpeakingInterruptMode.WAKE_WORD, SpeakingInterruptMode.ANY_SPEECH -> Unit
        }
        if (_events.value != VoiceEvent.Speaking) return
        if (isRecording) return
        if (heyJuliusActivationInProgress) return
        if (isRecognizerActive && isBargeInActive) return

        isHeyJuliusKeywordBargeIn =
            settingsManager.settings.value.speakingInterruptMode == SpeakingInterruptMode.WAKE_WORD
        startBargeInListening()
    }

    private fun normalizeForKeyword(text: String): String {
        // Keep letters/numbers/spaces only, collapse whitespace, lowercase.
        val cleaned = buildString(text.length) {
            for (c in text) {
                if (c.isLetterOrDigit() || c.isWhitespace()) append(c) else append(' ')
            }
        }
        return cleaned
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun isHeyJulius(text: String): Boolean {
        val n = normalizeForKeyword(text)
        return Regex("\\bhey\\s+julius\\b").containsMatchIn(n)
    }

    private fun isStopKeyword(text: String): Boolean {
        val n = normalizeForKeyword(text)
        return Regex("\\bstop\\b").containsMatchIn(n)
    }

    private fun activateStopKeyword() {
        Log.d(TAG, "\"stop\" detected: stop outputs + cancel recognition")
        tts?.stop()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        stopBargeInListening()
        if (isRecording) {
            isRecording = false
        }
        if (isRecognizerActive) {
            mainHandler.post { speechRecognizer?.cancel() }
            isRecognizerActive = false
        }

        _partialText.value = ""
        _transcribedText.value = ""
        _events.value = VoiceEvent.Silence
        player.notifyStateChanged()
        abandonAudioFocus()
    }

    private fun activateHeyJulius() {
        if (heyJuliusActivationInProgress) return
        heyJuliusActivationInProgress = true
        Log.d(TAG, "Hey Julius detected during speaking: interrupt + startListening")

        // Stop audio output, but keep audio focus so we can immediately listen.
        tts?.stop()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        // Cancel barge-in recognizer and restart normal listening shortly after.
        isHeyJuliusKeywordBargeIn = false
        if (isRecognizerActive) {
            mainHandler.post { speechRecognizer?.cancel() }
            isRecognizerActive = false
        }
        isBargeInActive = false

        mainHandler.postDelayed(
            {
                heyJuliusActivationInProgress = false
                startListening()
            },
            BARGE_IN_RESTART_DELAY_MS
        )
    }

    private fun observeSettings() {
        GlobalScope.launch(Dispatchers.Main) {
            settingsManager.settings.collect { settings ->
                if (settings.wakeWordEnabled) {
                    startWakeWordDetection()
                } else {
                    stopWakeWordDetection()
                }
            }
        }
    }

    private fun startWakeWordDetection() {
        if (wakeWordDetectionJob != null) return
        Log.d(TAG, "Starting wake word detection")
        wakeWordDetectionJob = GlobalScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_events.value == VoiceEvent.Silence && !isRecording && !isRecognizerActive) {
                    // This is a simplified placeholder for wake word detection.
                    // In a real scenario, we'd use a local engine like Porcupine.
                    // Here we'll periodically check if we should trigger.
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun stopWakeWordDetection() {
        Log.d(TAG, "Stopping wake word detection")
        wakeWordDetectionJob?.cancel()
        wakeWordDetectionJob = null
    }

    private fun requestAudioFocus() {
        if (!settingsManager.settings.value.muteMediaOnCar) return

        Log.d(TAG, "Requesting Audio Focus")
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                Log.d(TAG, "Audio Focus change: $focusChange")
            }
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        Log.d(TAG, "Audio Focus request result: $result")
    }

    private fun abandonAudioFocus() {
        if (audioFocusRequest != null) {
            Log.d(TAG, "Abandoning Audio Focus")
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            audioFocusRequest = null
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
            // Prefer offline recognition if available to satisfy "local if you can" requirement
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    private fun finalizeListening(text: String = "") {
        isRecognizerActive = false
        isBargeInActive = false
        val finalResult = text.trim()
        Log.d(TAG, "finalizeListening: finalResult=\"$finalResult\"")

        _partialText.value = ""

        if (finalResult.isNotBlank()) {
            // Signal processing if we have text
            _events.value = VoiceEvent.Processing
            player.notifyStateChanged()
            // Setting this triggers ConversationStore.onUserFinishedSpeaking
            _transcribedText.value = finalResult
        } else {
            if (_events.value != VoiceEvent.Silence) {
                _events.value = VoiceEvent.Silence
                player.notifyStateChanged()
                abandonAudioFocus()
            }
        }
    }

    // RecognitionListener
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech: mic ready, listening for speech")
        // Keep Speaking state when listening for barge-in
        if (!(isBargeInActive && _events.value == VoiceEvent.Speaking)) {
            _events.value = VoiceEvent.Listening
            player.notifyStateChanged()
        }
    }
    override fun onBeginningOfSpeech() {
        if (isBargeInActive && _events.value == VoiceEvent.Speaking) {
            // In keyword-only mode, don't stop speech until keyword is confirmed in onResults/onPartialResults
            if (!isHeyJuliusKeywordBargeIn) {
                tts?.stop()
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
                isBargeInActive = false
            }
        }
        _events.value = VoiceEvent.Listening
        player.notifyStateChanged()
        Log.d(TAG, "onBeginningOfSpeech: speech detected")
    }
    override fun onRmsChanged(rmsdB: Float) {
        rmsCallCount++
        if (rmsCallCount <= 3 || rmsCallCount % RMS_LOG_INTERVAL == 0) {
            Log.d(TAG, "onRmsChanged: rmsdB=$rmsdB (call #$rmsCallCount)")
        }
    }
    override fun onBufferReceived(buffer: ByteArray?) {
        bufferCallCount++
        val len = buffer?.size ?: 0
        if (bufferCallCount <= 3 || bufferCallCount % BUFFER_LOG_INTERVAL == 0) {
            Log.d(TAG, "onBufferReceived: bytes=$len (call #$bufferCallCount)")
        }
    }
    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech: waiting for final result")
        if (!(isBargeInActive && _events.value == VoiceEvent.Speaking)) {
            _events.value = VoiceEvent.Processing
            player.notifyStateChanged()
        }
    }
    override fun onError(error: Int) {
        val errorStr = when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            else -> "ERROR_OTHER($error)"
        }
        Log.w(TAG, "onError: code=$error $errorStr")
        isRecognizerActive = false

        if (isBargeInActive && _events.value == VoiceEvent.Speaking) {
            scheduleBargeInRestart()
            return
        }

        isBargeInActive = false
        finalizeListening()
    }
    override fun onResults(results: Bundle?) {
        isRecognizerActive = false

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        Log.d(TAG, "onResults: \"$text\"")

        if (isBargeInActive && _events.value == VoiceEvent.Speaking) {
            if (isHeyJuliusKeywordBargeIn && text.isNotBlank() && isHeyJulius(text)) {
                activateHeyJulius()
                return
            }
            if (isHeyJuliusKeywordBargeIn && text.isNotBlank() && isStopKeyword(text)) {
                activateStopKeyword()
                return
            }

            // Keyword-only mode: ignore non-keyword speech while Julius is speaking.
            if (isHeyJuliusKeywordBargeIn) {
                scheduleBargeInRestart()
                return
            }

            if (text.isNotBlank()) {
                // If we detected speech during normal barge-in, stop TTS and process it
                tts?.stop()
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                    }
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
                isBargeInActive = false
                finalizeListening(text)
            } else {
                scheduleBargeInRestart()
            }
            return
        }

        finalizeListening(text)
    }
    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""

        if (text.isNotBlank()) {
            Log.d(TAG, "onPartialResults: partial=\"$text\"")

            if (isBargeInActive && _events.value == VoiceEvent.Speaking) {
                if (isHeyJuliusKeywordBargeIn) {
                    if (isHeyJulius(text)) {
                        activateHeyJulius()
                    } else if (isStopKeyword(text)) {
                        activateStopKeyword()
                    }
                    // Don't touch UI transcript while speaking; barge-in is hidden.
                    return
                } else {
                    // In normal barge-in, as soon as we have a partial result, we stop TTS
                    tts?.stop()
                    try {
                        if (mediaPlayer?.isPlaying == true) {
                            mediaPlayer?.stop()
                        }
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }
                    isBargeInActive = false
                    _events.value = VoiceEvent.Listening
                    player.notifyStateChanged()
                }
            }

            _partialText.value = text
        }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {
        Log.d(TAG, "onEvent: eventType=$eventType")
    }
}

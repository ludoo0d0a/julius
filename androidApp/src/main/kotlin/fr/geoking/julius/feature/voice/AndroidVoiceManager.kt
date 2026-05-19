package fr.geoking.julius.feature.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.shared.logging.DebugLogStore
import fr.geoking.julius.shared.platform.PermissionManager
import fr.geoking.julius.shared.voice.VoiceEvent
import fr.geoking.julius.shared.voice.VoiceManager
import fr.geoking.julius.shared.voice.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@UnstableApi
class AndroidVoiceManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val permissionManager: PermissionManager,
    private val localTranscriber: fr.geoking.julius.shared.voice.LocalTranscriber? = null
) : VoiceManager, RecognitionListener, TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var carContext: CarContext? = null
    private var isRecording = false
    private var phoneRecordingJob: Job? = null
    private var carRecordingJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    /** Non-null while an Android Auto [Session] is active; enables car mic + [CarAudioRecord] when [useCarMic] is on. */
    fun setCarContext(carContext: CarContext?) {
        this.carContext = carContext
    }

    companion object {
        private const val TAG = "Julius/Voice"
        /** Car mic capture runs until [stopListening] or this cap (no VAD); avoids an infinite read loop on AA. */
        private const val MAX_CAR_RECORDING_MS = 30_000L
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
    private var isManualStopMode: Boolean = false
    private var rmsCallCount: Int = 0
    private var bufferCallCount: Int = 0
    private var transcriber: (suspend (ByteArray) -> TranscriptionResult?)? = null
    /** Accumulated final phrases while push-to-talk Vosk dictation is active. */
    private val dictateSessionText = StringBuilder()
    /** True when the current session streams partials via [localTranscriber] (no auto-stop on Vosk finals). */
    private var streamingLocalStt: Boolean = false

    private val player = object : SimpleBasePlayer(context.mainLooper) {
        fun notifyStateChanged() {
            invalidateState()
        }

        override fun getState(): State {
            val playbackState = when (_events.value) {
                VoiceEvent.Listening, VoiceEvent.Speaking, VoiceEvent.Processing -> STATE_READY
                VoiceEvent.Silence -> STATE_IDLE
            }
            val playWhenReady = _events.value != VoiceEvent.Silence

            return State.Builder()
                .setAvailableCommands(
                    Player.Commands.Builder()
                        .add(COMMAND_PLAY_PAUSE)
                        .add(COMMAND_STOP)
                        .build()
                )
                .setPlaybackState(playbackState)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
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
        // Defer TTS and MediaPlayer creation off the main thread to avoid ANR.
        // TextToSpeech constructor can block for several seconds on first use.
        scope.launch(Dispatchers.IO) {
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
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (_events.value == VoiceEvent.Speaking) {
                                _events.value = VoiceEvent.Silence
                                player.notifyStateChanged()
                                abandonAudioFocus()
                            }
                        }
                    }

                    @Deprecated(
                        "Overrides deprecated UtteranceProgressListener.onError",
                        ReplaceWith("onDone(utteranceId)")
                    )
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
        startListening(isManualStop = false)
    }

    override fun startListening(isManualStop: Boolean) {
        if (_events.value == VoiceEvent.Listening) {
            Log.d(TAG, "startListening() called but already listening, ignoring")
            return
        }
        Log.d(TAG, "mic on: startListening(isManualStop=$isManualStop)")
        DebugLogStore.addActionLog("Voice", "startListening(isManualStop=$isManualStop)")

        this.isManualStopMode = isManualStop

        // Signal Listening immediately so the UI responds without waiting for async permission check.
        _events.value = VoiceEvent.Listening
        player.notifyStateChanged()
        requestAudioFocus()

        // Request mic permission on-demand (Play Store builds won't auto-request on startup).
        scope.launch(Dispatchers.Main) {
            val granted = try {
                // Fast-path: if already granted, don't rely on any UI callback wiring.
                permissionManager.hasPermission(Manifest.permission.RECORD_AUDIO) ||
                    permissionManager.requestPermission(Manifest.permission.RECORD_AUDIO)
            } catch (_: Throwable) {
                false
            }
            if (!granted) {
                Log.e(TAG, "RECORD_AUDIO permission not granted")
                DebugLogStore.addActionLog("Voice", "Permission denied: RECORD_AUDIO (requested)")
                _events.value = VoiceEvent.Silence
                player.notifyStateChanged()
                abandonAudioFocus()
                return@launch
            }

            val currentCarContext = carContext

            val useLocal =
                settingsManager.settings.value.sttEnginePreference != fr.geoking.julius.shared.voice.SttEnginePreference.NativeOnly &&
                    localTranscriber?.isAvailable() == true

            // Stop any active TTS or media before starting mic
            stopSpeakingInternal()

            // Use the car's microphone whenever a car context is available and the setting is enabled.
            if (currentCarContext != null && settingsManager.settings.value.useCarMic) {
                if (useLocal) {
                    startCarListening(currentCarContext)
                } else {
                    // Fallback to native
                    startListeningInternal(stopOutputs = false) // stopSpeakingInternal already called
                }
            } else if (useLocal) {
                startPhoneRecording()
            } else {
                startListeningInternal(stopOutputs = false) // stopSpeakingInternal already called
            }
        }
    }

    private fun beginDictationSession() {
        dictateSessionText.clear()
        streamingLocalStt = true
        _partialText.value = ""
        _transcribedText.value = ""
    }

    /**
     * Handles streaming Vosk results: finals are appended to the session; listening continues until [stopListening].
     */
    private fun handleVoskStreamingResult(result: TranscriptionResult) {
        val text = result.text.trim()
        if (text.isBlank()) return
        if (result.isFinal) {
            if (dictateSessionText.isNotEmpty()) dictateSessionText.append(' ')
            dictateSessionText.append(text)
            _transcribedText.value = dictateSessionText.toString().trim()
            _partialText.value = ""
            localTranscriber?.reset()
        } else {
            val prefix = dictateSessionText.toString().trim()
            _partialText.value = if (prefix.isEmpty()) text else "$prefix $text"
        }
    }

    private fun finalizeDictationSession(fallbackAudio: ByteArray? = null, source: String = "Phone") {
        val flushed = localTranscriber?.flushPendingFinal()?.trim().orEmpty()
        if (flushed.isNotEmpty()) {
            if (dictateSessionText.isNotEmpty()) dictateSessionText.append(' ')
            dictateSessionText.append(flushed)
        }
        streamingLocalStt = false
        val sessionText = dictateSessionText.toString().trim()
        mainHandler.post {
            _partialText.value = ""
            if (sessionText.isNotBlank()) {
                _transcribedText.value = sessionText
            }
            if (_events.value != VoiceEvent.Speaking && _events.value != VoiceEvent.Processing) {
                _events.value = VoiceEvent.Silence
                player.notifyStateChanged()
            }
            val audio = fallbackAudio
            if (sessionText.isBlank() && audio != null && audio.isNotEmpty()) {
                scope.launch { processAudioData(audio, source) }
            } else {
                abandonAudioFocus()
            }
        }
    }

    private fun startPhoneRecording() {
        if (isRecording) return
        isRecording = true
        beginDictationSession()
        _events.value = VoiceEvent.Listening
        player.notifyStateChanged()

        phoneRecordingJob = scope.launch(Dispatchers.IO) {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "RECORD_AUDIO permission not granted for phone mic")
                    DebugLogStore.addActionLog("Voice", "Permission denied: RECORD_AUDIO")
                    isRecording = false
                    streamingLocalStt = false
                    mainHandler.post {
                        _events.value = VoiceEvent.Silence
                        player.notifyStateChanged()
                        abandonAudioFocus()
                    }
                    return@launch
                }

                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                @SuppressLint("MissingPermission")
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufferSize.coerceAtLeast(1024 * 4)
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed: state=${recorder.state}")
                    isRecording = false
                    streamingLocalStt = false
                    mainHandler.post {
                        _events.value = VoiceEvent.Silence
                        player.notifyStateChanged()
                        abandonAudioFocus()
                    }
                    return@launch
                }

                recorder.startRecording()
                DebugLogStore.addActionLog("Voice", "Phone recording started (Vosk)")
                localTranscriber?.reset()
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(1024 * 2)

                while (isRecording && isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        baos.write(chunk)
                        val result = localTranscriber?.transcribe(chunk)
                        if (result != null) {
                            handleVoskStreamingResult(result)
                        }
                    } else if (read < 0) {
                        break
                    }
                }

                recorder.stop()
                recorder.release()
                val audioData = baos.toByteArray()
                isRecording = false
                Log.d(TAG, "Phone recording stopped, size: ${audioData.size}")
                DebugLogStore.addActionLog("Voice", "Phone recording stopped, size: ${audioData.size}")

                if (streamingLocalStt) {
                    finalizeDictationSession(
                        fallbackAudio = audioData.takeIf { it.isNotEmpty() },
                        source = "Phone"
                    )
                } else if (audioData.isNotEmpty()) {
                    processAudioData(audioData, "Phone")
                } else {
                    mainHandler.post {
                        _events.value = VoiceEvent.Silence
                        player.notifyStateChanged()
                        abandonAudioFocus()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Phone recording failed", e)
                DebugLogStore.addActionLog("Voice", "Phone recording failed: ${e.message}")
                isRecording = false
                streamingLocalStt = false
                mainHandler.post {
                    _events.value = VoiceEvent.Silence
                    player.notifyStateChanged()
                    abandonAudioFocus()
                }
            } finally {
                phoneRecordingJob = null
            }
        }
    }

    private fun startCarListening(carContext: CarContext) {
        if (isRecording) return
        isRecording = true
        beginDictationSession()
        _events.value = VoiceEvent.Listening
        player.notifyStateChanged()

        carRecordingJob = scope.launch(Dispatchers.IO) {
            val maxDurationStop = launch {
                if (!isManualStopMode) {
                    delay(MAX_CAR_RECORDING_MS)
                    if (isRecording) {
                        Log.d(TAG, "Car recording: max duration reached (${MAX_CAR_RECORDING_MS}ms), stopping")
                        isRecording = false
                    }
                }
            }
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "RECORD_AUDIO permission not granted for car mic")
                    DebugLogStore.addActionLog("Voice", "Permission denied: RECORD_AUDIO (car)")
                    isRecording = false
                    streamingLocalStt = false
                    mainHandler.post {
                        _events.value = VoiceEvent.Silence
                        player.notifyStateChanged()
                        abandonAudioFocus()
                    }
                    return@launch
                }
                @SuppressLint("MissingPermission")
                val carAudioRecord = CarAudioRecord.create(carContext)
                carAudioRecord.startRecording()
                localTranscriber?.reset()
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)

                Log.d(TAG, "Car recording started")

                while (isRecording && isActive) {
                    val read = carAudioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        baos.write(chunk)
                        val result = localTranscriber?.transcribe(chunk)
                        if (result != null) {
                            handleVoskStreamingResult(result)
                        }
                    } else if (read < 0) {
                        break
                    }
                }

                carAudioRecord.stopRecording()
                val audioData = baos.toByteArray()
                isRecording = false
                Log.d(TAG, "Car recording stopped, size: ${audioData.size}")

                if (streamingLocalStt) {
                    finalizeDictationSession(
                        fallbackAudio = audioData.takeIf { it.isNotEmpty() },
                        source = "Car"
                    )
                } else if (audioData.isNotEmpty()) {
                    processAudioData(audioData, "Car")
                } else {
                    mainHandler.post {
                        _events.value = VoiceEvent.Silence
                        player.notifyStateChanged()
                        abandonAudioFocus()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Car recording failed", e)
                DebugLogStore.addActionLog("Voice", "Car recording failed: ${e.message}")
                isRecording = false
                streamingLocalStt = false
                mainHandler.post {
                    _events.value = VoiceEvent.Silence
                    player.notifyStateChanged()
                    abandonAudioFocus()
                }
            } finally {
                maxDurationStop.cancel()
                carRecordingJob = null
            }
        }
    }

    private suspend fun processAudioData(audioData: ByteArray, source: String) {
        mainHandler.post {
            _events.value = VoiceEvent.Processing
            player.notifyStateChanged()
        }

        // Reset localTranscriber before final batch transcription (it might have been used for partials)
        localTranscriber?.reset()
        val result = transcriber?.invoke(audioData)
        val text = result?.text ?: ""
        Log.d(TAG, "$source transcription: \"$text\"")
        DebugLogStore.addActionLog("Voice", "$source transcription: \"$text\"")

        mainHandler.post {
            _partialText.value = ""
            if (text.isNotBlank()) {
                _transcribedText.value = text
            } else {
                _events.value = VoiceEvent.Silence
                player.notifyStateChanged()
                abandonAudioFocus()
            }
        }
    }

    override fun setTranscriber(transcriber: suspend (ByteArray) -> TranscriptionResult?) {
        this.transcriber = transcriber
    }

    override fun stopListening() {
        Log.d(TAG, "mic off: stopListening()")
        isManualStopMode = false
        if (isRecording) {
            // Let the recording coroutine finish (release mic, flush Vosk); do not cancel abruptly.
            isRecording = false
        } else {
            mainHandler.post {
                speechRecognizer?.stopListening()
            }
            if (_events.value == VoiceEvent.Listening) {
                _events.value = VoiceEvent.Silence
                player.notifyStateChanged()
            }
            if (_events.value != VoiceEvent.Speaking && _events.value != VoiceEvent.Processing) {
                abandonAudioFocus()
            }
        }
    }

    override fun speak(text: String, languageTag: String?, isInterruptible: Boolean) {
        // Basic/simple: no barge-in or wake-word while speaking.
        requestAudioFocus()
        _events.value = VoiceEvent.Speaking
        player.notifyStateChanged()
        updateTtsLanguage(languageTag)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_ai_utterance")
    }

    override fun playAudio(bytes: ByteArray) {
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
        val locale = java.util.Locale.forLanguageTag(languageTag)
        return if (locale == java.util.Locale.ROOT) java.util.Locale.getDefault() else locale
    }

    private fun applyLocale(locale: java.util.Locale): Boolean {
        val ttsInstance = tts ?: return false
        val availability = ttsInstance.isLanguageAvailable(locale)
        return if (availability >= android.speech.tts.TextToSpeech.LANG_AVAILABLE) {
            ttsInstance.language = locale
            currentLanguageTag = locale.toLanguageTag()
            true
        } else {
            false
        }
    }

    private fun stopSpeakingInternal() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        tts?.stop()
    }

    private fun startListeningInternal(stopOutputs: Boolean) {
        val intent = buildRecognizerIntent()
        android.util.Log.d(TAG, "startListeningInternal stopOutputs=$stopOutputs")
        mainHandler.post {
            val recognizer = getOrCreateRecognizer() ?: run {
                android.util.Log.e(TAG, "getOrCreateRecognizer() returned null, cannot start")
                _events.value = VoiceEvent.Silence
                player.notifyStateChanged()
                abandonAudioFocus()
                return@post
            }

            if (stopOutputs) {
                stopSpeakingInternal()
            }
            if (isRecognizerActive) {
                recognizer.cancel()
            }
            isRecognizerActive = true
            rmsCallCount = 0
            bufferCallCount = 0

            _partialText.value = ""
            _transcribedText.value = ""

            android.util.Log.d(TAG, "SpeechRecognizer.startListening() called")
            recognizer.startListening(intent)
            _events.value = VoiceEvent.Listening
            player.notifyStateChanged()
        }
    }

    private fun requestAudioFocus() {
        android.util.Log.d(TAG, "Requesting Audio Focus")
        val playbackAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                android.util.Log.d(TAG, "Audio Focus change: $focusChange")
            }
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        android.util.Log.d(TAG, "Audio Focus request result: $result")
    }

    private fun abandonAudioFocus() {
        if (audioFocusRequest != null) {
            android.util.Log.d(TAG, "Abandoning Audio Focus")
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
            audioFocusRequest = null
        }
    }

    private fun getOrCreateRecognizer(): android.speech.SpeechRecognizer? {
        if (speechRecognizer == null) {
            speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
        }
        return speechRecognizer
    }

    private fun buildRecognizerIntent(): android.content.Intent {
        return android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Prefer offline recognition if available to satisfy "local if you can" requirement
            putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    private fun finalizeListening(text: String = "") {
        if (isManualStopMode) {
            Log.d(TAG, "finalizeListening: manual stop mode active, ignoring auto-stop. Current text: \"$text\"")
            // In manual mode, we might want to keep the partial text or results,
            // but we don't want to finish the session yet.
            if (text.isNotBlank()) {
                _partialText.value = text
            }
            return
        }
        isRecognizerActive = false
        val finalResult = text.trim()
        if (finalResult.isBlank()) {
            _events.value = VoiceEvent.Silence
            player.notifyStateChanged()
            abandonAudioFocus()
            return
        }
        android.util.Log.d(TAG, "finalizeListening: finalResult=\"$finalResult\"")

        _partialText.value = ""
        _transcribedText.value = finalResult
        _events.value = VoiceEvent.Silence
        player.notifyStateChanged()
        abandonAudioFocus()
    }

    // RecognitionListener
    override fun onReadyForSpeech(params: android.os.Bundle?) {
        android.util.Log.d(TAG, "onReadyForSpeech: mic ready, listening for speech")
        _events.value = VoiceEvent.Listening
        player.notifyStateChanged()
    }
    override fun onBeginningOfSpeech() {
        _events.value = VoiceEvent.Listening
        player.notifyStateChanged()
        android.util.Log.d(TAG, "onBeginningOfSpeech: speech detected")
    }
    override fun onRmsChanged(rmsdB: Float) {
        rmsCallCount++
        if (rmsCallCount <= 3 || rmsCallCount % RMS_LOG_INTERVAL == 0) {
            android.util.Log.d(TAG, "onRmsChanged: rmsdB=$rmsdB (call #$rmsCallCount)")
        }
    }
    override fun onBufferReceived(buffer: ByteArray?) {
        bufferCallCount++
        val len = buffer?.size ?: 0
        if (bufferCallCount <= 3 || bufferCallCount % BUFFER_LOG_INTERVAL == 0) {
            android.util.Log.d(TAG, "onBufferReceived: bytes=$len (call #$bufferCallCount)")
        }
    }
    override fun onEndOfSpeech() {
        android.util.Log.d(TAG, "onEndOfSpeech: waiting for final result")
        _events.value = VoiceEvent.Processing
        player.notifyStateChanged()
    }
    override fun onError(error: Int) {
        val errorStr = when (error) {
            android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            android.speech.SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            android.speech.SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            android.speech.SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            android.speech.SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            else -> "ERROR_OTHER($error)"
        }
        android.util.Log.w(TAG, "onError: code=$error $errorStr")
        isRecognizerActive = false

        finalizeListening()
    }
    override fun onResults(results: android.os.Bundle?) {
        isRecognizerActive = false

        val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        android.util.Log.d(TAG, "onResults: \"$text\"")

        finalizeListening(text)
    }
    override fun onPartialResults(partialResults: android.os.Bundle?) {
        val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""

        if (text.isNotBlank()) {
            android.util.Log.d(TAG, "onPartialResults: partial=\"$text\"")

            _partialText.value = text
        }
    }
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {
        android.util.Log.d(TAG, "onEvent: eventType=$eventType")
    }
}
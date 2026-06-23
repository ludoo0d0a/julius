package fr.geoking.julius.ui.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.min

private const val TAG = "Dictation"

/**
 * Lightweight STT for form dictation — isolated from [fr.geoking.julius.shared.voice.VoiceManager].
 * Returns a toggle: tap to start listening, tap again to stop and finalize.
 *
 * Aligned with the Vincent reference: one long-lived recognizer, fresh callbacks via
 * [rememberUpdatedState], and no [RecognizerIntent.EXTRA_PREFER_OFFLINE] unless requested.
 */
@Composable
fun rememberDictation(
    onText: (String) -> Unit,
    onLevel: (Float) -> Unit,
    onListening: (Boolean) -> Unit,
    languageTag: String = "fr-FR",
    preferOffline: Boolean = false,
): () -> Unit {
    val context = LocalContext.current
    val text by rememberUpdatedState(onText)
    val level by rememberUpdatedState(onLevel)
    val listeningCb by rememberUpdatedState(onListening)
    var listening by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            Log.w(TAG, "Speech recognition not available on this device")
            null
        }
    }

    fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    fun startListening() {
        val sr = recognizer ?: run {
            listeningCb(false)
            listening = false
            return
        }
        level(0f)
        listening = true
        listeningCb(true)
        sr.startListening(buildIntent())
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                level(min(1f, (rmsdB + 2f) / 12f))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                Log.w(TAG, "SpeechRecognizer error=$error")
                listening = false
                listeningCb(false)
                level(0f)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.firstTranscript()?.let(text)
            }

            override fun onResults(results: Bundle?) {
                results?.firstTranscript()?.let(text)
                listening = false
                listeningCb(false)
                level(0f)
            }
        })
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
            listeningCb(false)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingStart) {
            pendingStart = false
            startListening()
        } else {
            pendingStart = false
            listening = false
            listeningCb(false)
        }
    }

    return remember(recognizer) {
        {
            when {
                recognizer == null -> listeningCb(false)
                listening -> recognizer.stopListening()
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED -> startListening()
                else -> {
                    pendingStart = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}

private fun Bundle.firstTranscript(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

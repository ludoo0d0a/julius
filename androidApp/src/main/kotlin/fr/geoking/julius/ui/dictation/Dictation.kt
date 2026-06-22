package fr.geoking.julius.ui.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Lightweight STT for form dictation — isolated from [fr.geoking.julius.shared.voice.VoiceManager].
 * Returns a toggle: tap to start listening, tap again to stop and finalize.
 */
@Composable
fun rememberDictation(
    onText: (String) -> Unit,
    onLevel: (Float) -> Unit,
    onListening: (Boolean) -> Unit,
    languageTag: String = "fr-FR",
    preferOffline: Boolean = true,
): () -> Unit {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
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

    fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onListening(false)
            listening = false
            return
        }
        destroyRecognizer()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                onLevel(min(1f, (rmsdB + 2f) / 12f))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                listening = false
                onListening(false)
                onLevel(0f)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) onText(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) onText(text)
                listening = false
                onListening(false)
                onLevel(0f)
            }
        })
        onText("")
        onLevel(0f)
        listening = true
        onListening(true)
        sr.startListening(buildIntent())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingStart) {
            pendingStart = false
            startRecognizer()
        } else {
            pendingStart = false
            listening = false
            onListening(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.cancel()
            destroyRecognizer()
            onListening(false)
        }
    }

    return remember(context, languageTag, preferOffline) {
        {
            if (listening) {
                recognizer?.stopListening()
            } else {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    startRecognizer()
                } else {
                    pendingStart = true
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}

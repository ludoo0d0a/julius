package fr.geoking.julius.feature.voice

import android.content.Context
import android.util.Log
import fr.geoking.julius.shared.LocalTranscriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Local STT using Vosk. Expects 16 kHz 16-bit mono PCM.
 * Model is loaded from [modelDirPath] (e.g. app filesDir or a path from settings).
 * To bundle a model: put unpacked model in assets/models/vosk/ and it will be copied to filesDir on first use.
 */
class VoskTranscriber(
    private val context: Context,
    private val modelDirPath: String? = null
) : LocalTranscriber {

    @Volatile
    private var model: Model? = null

    @Volatile
    private var recognizer: Recognizer? = null

    private val lock = Any()

    override suspend fun transcribe(audioData: ByteArray): String? = withContext(Dispatchers.IO) {
        if (audioData.isEmpty()) return@withContext null
        try {
            val rec = getOrCreateRecognizer() ?: return@withContext null
            // Fresh utterance per buffer (Vosk/Kaldi state); matches typical Android batch usage.
            val result = synchronized(lock) {
                rec.reset()
                val accepted = rec.acceptWaveForm(audioData, audioData.size)
                if (accepted) rec.result else rec.partialResult
            }
            parseTextFromResult(result)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Vosk recognition failed", e)
            null
        }
    }

    private fun getOrCreateRecognizer(): Recognizer? {
        if (recognizer != null) return recognizer
        synchronized(lock) {
            if (recognizer != null) return recognizer
            val m = getOrCreateModel() ?: return null
            recognizer = Recognizer(m, SAMPLE_RATE)
            return recognizer
        }
    }

    private fun getOrCreateModel(): Model? {
        if (model != null) return model
        synchronized(lock) {
            if (model != null) return model
            val path = modelDirPath?.takeIf { File(it, "am/final.mdl").exists() }
                ?: ensureModelFromAssets()
            if (path == null) {
                Log.w(TAG, "No Vosk model path available; add model to assets/models/vosk/ or set path")
                return null
            }
            model = Model(path)
            return model
        }
    }

    /** Copies model from assets/models/vosk/ to filesDir if present; returns path to model dir. */
    private fun ensureModelFromAssets(): String? {
        val baseDir = File(context.filesDir, "vosk_model")
        val children = baseDir.list() ?: emptyArray()
        val existing = children.firstOrNull { File(baseDir, "$it/am/final.mdl").exists() }
        if (existing != null) return File(baseDir, existing).absolutePath
        return copyModelFromAssets("models/vosk", baseDir)
    }

    private fun copyModelFromAssets(assetPrefix: String, targetDir: File): String? {
        return try {
            val children = context.assets.list(assetPrefix) ?: return null
            if (children.isEmpty()) return null
            fun copyRecursive(prefix: String, dest: File) {
                context.assets.list(prefix)?.forEach { name ->
                    val path = "$prefix/$name"
                    val destFile = File(dest, name)
                    val sub = context.assets.list(path)
                    if (sub.isNullOrEmpty()) {
                        destFile.parentFile?.mkdirs()
                        context.assets.open(path).use { input ->
                            destFile.outputStream().use { input.copyTo(it) }
                        }
                    } else {
                        destFile.mkdirs()
                        copyRecursive(path, destFile)
                    }
                }
            }
            targetDir.mkdirs()
            copyRecursive(assetPrefix, targetDir)
            val firstChild = context.assets.list(assetPrefix)?.firstOrNull()
            if (firstChild != null && File(targetDir, "$firstChild/am/final.mdl").exists()) {
                File(targetDir, firstChild).absolutePath
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy Vosk model from assets", e)
            null
        }
    }

    private fun parseTextFromResult(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            if (obj.has("text")) obj.getString("text") else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Vosk result", e)
            null
        }
    }

    companion object {
        private const val TAG = "Julius/Vosk"
        /** Vosk expects 16 kHz. CarAudioRecord may differ; conversion can be added if needed. */
        private const val SAMPLE_RATE = 16000f
    }
}

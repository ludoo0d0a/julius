package fr.geoking.julius.feature.voice

import android.content.Context
import android.util.Log
import fr.geoking.julius.shared.voice.LocalTranscriber
import fr.geoking.julius.shared.voice.TranscriptionResult
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

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult? = withContext(Dispatchers.IO) {
        if (audioData.isEmpty()) return@withContext null
        try {
            val rec = getOrCreateRecognizer() ?: return@withContext null
            val chunkSize = 4096
            var offset = 0
            var finalResult: String? = null
            var partialResult: String? = null

            synchronized(lock) {
                while (offset < audioData.size) {
                    val len = (audioData.size - offset).coerceAtMost(chunkSize)
                    val chunk = audioData.copyOfRange(offset, offset + len)
                    val accepted = rec.acceptWaveForm(chunk, len)
                    if (accepted) {
                        finalResult = parseTextFromResult(rec.result)
                    } else {
                        partialResult = parseTextFromResult(rec.partialResult)
                    }
                    offset += len
                }
            }

            if (!finalResult.isNullOrBlank()) {
                TranscriptionResult(finalResult!!, isFinal = true)
            } else if (!partialResult.isNullOrBlank()) {
                TranscriptionResult(partialResult!!, isFinal = false)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Vosk recognition failed", e)
            null
        }
    }

    override fun reset() {
        synchronized(lock) {
            recognizer?.reset()
        }
    }

    override fun flushPendingFinal(): String? {
        synchronized(lock) {
            val rec = recognizer ?: return null
            val text = parseTextFromResult(rec.finalResult)?.trim()
            rec.reset()
            return text?.takeIf { it.isNotEmpty() }
        }
    }

    override fun isAvailable(): Boolean {
        if (model != null) return true
        val path = modelDirPath?.takeIf { File(it, "am/final.mdl").exists() }
            ?: getExistingModelPath()
        Log.d(TAG, "isAvailable: path=$path")
        return path != null
    }

    private fun getExistingModelPath(): String? {
        // 1. Check for models downloaded via VoskModelHelper
        val downloadedPath = VoskModelHelper(context).getModelPath()
        if (downloadedPath != null) return downloadedPath

        // 2. Legacy check if model is directly in vosk_model
        val baseDir = File(context.filesDir, "vosk_model")
        if (File(baseDir, "am/final.mdl").exists()) {
            return baseDir.absolutePath
        }

        // 3. Legacy check subdirectories in vosk_model (common if unzipped with a folder)
        val children = baseDir.listFiles() ?: emptyArray()
        for (child in children) {
            if (child.isDirectory && File(child, "am/final.mdl").exists()) {
                return child.absolutePath
            }
        }

        // 4. Check assets without copying just to see if it's there
        return try {
            val assetsPath = "models/vosk"
            val assetFiles = context.assets.list(assetsPath) ?: emptyArray()
            if (assetFiles.isEmpty()) {
                Log.d(TAG, "getExistingModelPath: no files in assets/$assetsPath")
                return null
            }

            // Look for am/final.mdl in assets
            if (assetFiles.contains("am")) {
                val amFiles = context.assets.list("$assetsPath/am") ?: emptyArray()
                if (amFiles.contains("final.mdl")) {
                    Log.d(TAG, "getExistingModelPath: found model directly in assets/$assetsPath")
                    return "pending_copy"
                }
            }

            // Look in one level of subdirectories in assets
            for (subDir in assetFiles) {
                val subFiles = context.assets.list("$assetsPath/$subDir") ?: emptyArray()
                if (subFiles.contains("am")) {
                    val subAmFiles = context.assets.list("$assetsPath/$subDir/am") ?: emptyArray()
                    if (subAmFiles.contains("final.mdl")) {
                        Log.d(TAG, "getExistingModelPath: found model in assets/$assetsPath/$subDir")
                        return "pending_copy"
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking assets for Vosk model", e)
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
                ?: VoskModelHelper(context).getModelPath()
                ?: ensureModelFromAssets()
            if (path == null) {
                Log.w(TAG, "No Vosk model path available; add model to assets/models/vosk/ or download one")
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
            when {
                obj.has("text") -> obj.getString("text")
                obj.has("partial") -> obj.getString("partial")
                else -> null
            }
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

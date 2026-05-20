package fr.geoking.julius.feature.voice

import android.content.Context
import fr.geoking.julius.shared.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class VoskModelHelper(private val context: Context) {

    private val baseDir = File(context.filesDir, "vosk_models")

    fun isModelDownloaded(): Boolean {
        if (!baseDir.exists()) return false
        return findModelDir(baseDir) != null
    }

    private fun findModelDir(dir: File): File? {
        if (File(dir, "am/final.mdl").exists()) return dir
        val children = dir.listFiles() ?: return null
        for (child in children) {
            if (child.isDirectory) {
                val found = findModelDir(child)
                if (found != null) return found
            }
        }
        return null
    }

    fun getModelPath(): String? {
        return findModelDir(baseDir)?.absolutePath
    }

    suspend fun downloadAndExtract(
        variant: VoskModelVariant,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "vosk_model.zip")
        try {
            val url = URL(variant.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${connection.responseCode}"))
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }

            connection.inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesDownloaded = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        onProgress(bytesDownloaded, totalBytes)
                    }
                }
            }

            // Extract
            if (baseDir.exists()) baseDir.deleteRecursively()
            baseDir.mkdirs()

            ZipUtils.unzip(tempZip, baseDir)

            val modelPath = getModelPath()
            if (modelPath != null) {
                Result.success(modelPath)
            } else {
                Result.failure(Exception("Model files not found in unzipped archive"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }
}

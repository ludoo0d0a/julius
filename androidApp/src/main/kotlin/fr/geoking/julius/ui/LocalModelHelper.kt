package fr.geoking.julius.ui

import android.content.Context
import fr.geoking.julius.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Available local GGUF model variants. User can choose which to download in Local agent settings.
 */
enum class LocalModelVariant(
    val displayName: String,
    val sizeDescription: String,
    val fileName: String,
    val downloadUrl: String
) {
    Phi2Q4_0(
        displayName = "Phi-2 (Q4_0)",
        sizeDescription = "~1.6 GB, good quality for small devices",
        fileName = "phi-2.Q4_0.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_0.gguf"
    ),
    Gemma2BQ4_0(
        displayName = "Gemma 2B (Q4_0)",
        sizeDescription = "~1.4 GB, optimized for mobile",
        fileName = "gemma-2-2b-Q4_0.gguf",
        downloadUrl = "https://huggingface.co/tensorblock/gemma-2-2b-GGUF/resolve/main/gemma-2-2b-Q4_0.gguf"
    ),
    TinyLlamaQ4_0(
        displayName = "TinyLlama (Q4_0)",
        sizeDescription = "~650 MB, fastest but lower quality",
        fileName = "ggml-model-q4_0.gguf",
        downloadUrl = "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v0.2-GGUF/resolve/main/ggml-model-q4_0.gguf"
    )
}

/**
 * Helper for the Local (embedded) agent: check if model exists, resolve display path, download GGUF.
 * Downloaded models are stored in app files dir: [context.filesDir]/models/[variant.fileName].
 */
class LocalModelHelper(private val context: Context) {

    companion object {
        /** Default asset-relative path used when no download has been done. */
        const val DEFAULT_ASSET_PATH = "models/phi-2.Q4_0.gguf"
    }

    private fun fileForVariant(variant: LocalModelVariant): File =
        File(context.filesDir, "models/${variant.fileName}")

    /**
     * Returns true if the model is available: either at the stored path (absolute file)
     * or in assets (asset-relative path).
     */
    fun isModelDownloaded(settings: AppSettings): Boolean {
        val path = settings.localModelPath
        if (path.isBlank()) return false
        return if (isAbsolutePath(path)) {
            File(path).exists()
        } else {
            try {
                context.assets.open(path).close()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Returns true if the given variant has been downloaded (file exists in app storage).
     */
    fun isVariantDownloaded(variant: LocalModelVariant): Boolean =
        fileForVariant(variant).exists()

    /**
     * Path to show in UI (absolute path or "assets: models/...").
     */
    fun getDisplayPath(settings: AppSettings): String {
        val path = settings.localModelPath
        if (path.isBlank()) return DEFAULT_ASSET_PATH
        return if (isAbsolutePath(path)) path else "assets: $path"
    }

    /**
     * Absolute path where the selected variant will be saved when downloading.
     */
    fun getDownloadDestinationPath(variant: LocalModelVariant): String =
        fileForVariant(variant).absolutePath

    /**
     * Downloads the given variant to app files dir. Reports progress (bytes read, total if known).
     * Returns the absolute path to use as [AppSettings.localModelPath] on success.
     */
    suspend fun download(
        variant: LocalModelVariant,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val destFile = fileForVariant(variant)
        try {
            val url = URL(variant.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${connection.responseCode}"))
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            destFile.parentFile?.mkdirs() ?: run {
                return@withContext Result.failure(Exception("Could not create models directory"))
            }

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
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

            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            if (destFile.exists()) destFile.delete()
            Result.failure(e)
        }
    }

    private fun isAbsolutePath(path: String): Boolean =
        path.startsWith("/") || path.contains(":\\")
}

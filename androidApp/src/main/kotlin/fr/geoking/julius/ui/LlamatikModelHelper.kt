package fr.geoking.julius.ui

import android.content.Context
import fr.geoking.julius.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

import fr.geoking.julius.AgentType

/**
 * Downloadable on-device model variants grouped by agent (GGUF and related formats).
 */
enum class LlamatikModelVariant(
    val agentType: AgentType,
    val displayName: String,
    val sizeDescription: String,
    val fileName: String,
    val downloadUrl: String
) {
    // GGUF Models (Llamatik, llama.cpp, PocketPal)
    Phi2Gguf(
        AgentType.Llamatik,
        displayName = "Phi-2 (Q4_0)",
        sizeDescription = "~1.6 GB, GGUF",
        fileName = "phi-2.Q4_0.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_0.gguf"
    ),
    Gemma2BGguf(
        AgentType.Llamatik,
        displayName = "Gemma 2B (Q4_0)",
        sizeDescription = "~1.4 GB, GGUF",
        fileName = "gemma-2-2b-Q4_0.gguf",
        downloadUrl = "https://huggingface.co/tensorblock/gemma-2-2b-GGUF/resolve/main/gemma-2-2b-Q4_0.gguf"
    ),
    Qwen05BGguf(
        AgentType.LlamaCpp,
        displayName = "Qwen 2.5 0.5B (Q4_K_M)",
        sizeDescription = "~390 MB, GGUF",
        fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    ),

    // MediaPipe / Gemini Nano / AI Edge (.bin / .task)
    Gemma2BMediaPipe(
        AgentType.GeminiNano,
        displayName = "Gemma 2B IT (Int4)",
        sizeDescription = "~1.35 GB, MediaPipe",
        fileName = "gemma-1.1-2b-it-gpu-int4.bin",
        downloadUrl = "https://huggingface.co/google/gemma-1.1-2b-it-gpu-int4/resolve/main/gemma-1.1-2b-it-gpu-int4.bin"
    ),
    Phi2MediaPipe(
        AgentType.MediaPipe,
        displayName = "Phi-2 (Int4)",
        sizeDescription = "~1.5 GB, MediaPipe",
        fileName = "phi-2-gpu-int4.bin",
        downloadUrl = "https://huggingface.co/google/phi-2-gpu-int4/resolve/main/phi-2-gpu-int4.bin"
    ),

    // MLC-LLM (Multi-file, but we point to a main one for identification)
    Llama3Mlc(
        AgentType.MlcLlm,
        displayName = "Llama-3-8B (Q4f16_1)",
        sizeDescription = "~4.5 GB, MLC format",
        fileName = "llama-3-8b-q4f16_1.mlc",
        downloadUrl = "https://huggingface.co/mlc-ai/Llama-3-8B-Instruct-q4f16_1-MLC/resolve/main/params/ndarray-cache.json"
    )
}

/**
 * Llamatik / on-device model path: check if model exists, resolve display path, download GGUF.
 * Downloaded models are stored in app files dir: [context.filesDir]/models/[variant.fileName].
 */
class LlamatikModelHelper(private val context: Context) {

    companion object {
        /** Default asset-relative path used when no download has been done. */
        const val DEFAULT_ASSET_PATH = "models/phi-2.Q4_0.gguf"
    }

    private fun fileForVariant(variant: LlamatikModelVariant): File =
        File(context.filesDir, "models/${variant.agentType.name}/${variant.fileName}")

    /**
     * Returns true if the model for the given agent is available.
     */
    fun isModelDownloaded(settings: AppSettings, agentType: AgentType): Boolean {
        val path = getModelPathForAgent(settings, agentType)
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

    private fun getModelPathForAgent(settings: AppSettings, agentType: AgentType): String {
        return when (agentType) {
            AgentType.Llamatik -> settings.llamatikModelPath
            // Other on-device agents share [AppSettings.llamatikModelPath] until split per agent.
            else -> settings.llamatikModelPath
        }
    }

    /**
     * Returns true if the given variant has been downloaded (file exists in app storage).
     */
    fun isVariantDownloaded(variant: LlamatikModelVariant): Boolean =
        fileForVariant(variant).exists()

    /**
     * Path to show in UI (absolute path or "assets: models/...").
     */
    fun getDisplayPath(settings: AppSettings, agentType: AgentType): String {
        val path = getModelPathForAgent(settings, agentType)
        if (path.isBlank()) return DEFAULT_ASSET_PATH
        return if (isAbsolutePath(path)) path else "assets: $path"
    }

    /**
     * Absolute path where the selected variant will be saved when downloading.
     */
    fun getDownloadDestinationPath(variant: LlamatikModelVariant): String =
        fileForVariant(variant).absolutePath

    /**
     * Downloads the given variant to app files dir. Reports progress (bytes read, total if known).
     * Returns the absolute path to use as [AppSettings.llamatikModelPath] on success.
     */
    suspend fun download(
        variant: LlamatikModelVariant,
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

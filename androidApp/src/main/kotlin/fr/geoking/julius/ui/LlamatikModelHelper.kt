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
        displayName = "Gemma 2B IT (Q4_K_M)",
        sizeDescription = "~1.7 GB, GGUF",
        fileName = "gemma-2-2b-it-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
    ),
    Llama32_1B_Gguf(
        AgentType.Llamatik,
        displayName = "Llama 3.2 1B (Q4_K_M)",
        sizeDescription = "~800 MB, GGUF",
        fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
    ),
    TinyLlamaGguf(
        AgentType.Llamatik,
        displayName = "TinyLlama 1.1B (Q4_K_M)",
        sizeDescription = "~670 MB, GGUF",
        fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
    ),
    Qwen05BGguf(
        AgentType.LlamaCpp,
        displayName = "Qwen 2.5 0.5B (Q4_K_M)",
        sizeDescription = "~400 MB, GGUF",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
    ),
    SmolLM2_135M_Gguf(
        AgentType.PocketPal,
        displayName = "SmolLM2 135M (Q4_K_M)",
        sizeDescription = "~100 MB, GGUF",
        fileName = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
    ),
    Qwen05B_Pocket_Gguf(
        AgentType.PocketPal,
        displayName = "Qwen 2.5 0.5B (Q4_K_M)",
        sizeDescription = "~400 MB, GGUF",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
    ),

    // MediaPipe / Gemini Nano / AI Edge (.bin / .task)
    Gemma2BMediaPipe(
        AgentType.GeminiNano,
        displayName = "Gemma 2B IT (Int4)",
        sizeDescription = "~1.35 GB, MediaPipe",
        fileName = "gemma-1.1-2b-it-gpu-int4.bin",
        downloadUrl = "https://huggingface.co/jeiku/Gemma-2b-it-MediaPipe/resolve/main/gemma-2b-it-gpu-int4.bin"
    ),
    Phi2MediaPipe(
        AgentType.MediaPipe,
        displayName = "Phi-2 (Int4)",
        sizeDescription = "~1.5 GB, MediaPipe",
        fileName = "phi-2-gpu-int4.bin",
        downloadUrl = "https://huggingface.co/jeiku/Phi-2-MediaPipe/resolve/main/phi-2-gpu-int4.bin"
    ),

    // RunAnywhere / AI Edge
    Qwen05B_Edge_Gguf(
        AgentType.AiEdge,
        displayName = "Qwen 2.5 0.5B (Q4_K_M)",
        sizeDescription = "~400 MB, GGUF",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
    ),
    SmolLM2_135M_Run_Gguf(
        AgentType.RunAnywhere,
        displayName = "SmolLM2 135M (Q4_K_M)",
        sizeDescription = "~100 MB, GGUF",
        fileName = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
    ),

    // MLC-LLM
    Phi2_Mlc(
        AgentType.MlcLlm,
        displayName = "Phi-2 (Q4f16_1)",
        sizeDescription = "~1.6 GB, MLC format",
        fileName = "phi-2-q4f16_1-MLC",
        downloadUrl = "https://huggingface.co/mlc-ai/phi-2-q4f16_1-MLC/resolve/main/params/ndarray-cache.json"
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

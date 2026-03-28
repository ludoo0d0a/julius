package fr.geoking.julius.agents

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloadable on-device models: **LiteRT-LM** (`.litertlm`, Google AI Edge GenAI) and **GGUF** (Llamatik / llama.cpp).
 *
 * [forAgentName] must match the app `AgentType` enum’s `.name` for the slot (e.g. `"Llamatik"`, `"GeminiNano"`).
 */
enum class LlamatikModelVariant(
    val forAgentName: String,
    val displayName: String,
    val sizeDescription: String,
    val fileName: String,
    val downloadUrl: String
) {
    // GGUF Models (Llamatik, llama.cpp, PocketPal)
    Phi2Gguf(
        "Llamatik",
        displayName = "Phi-2 (Q4_0)",
        sizeDescription = "~1.6 GB, GGUF",
        fileName = "phi-2.Q4_0.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_0.gguf"
    ),
    Gemma2BGguf(
        "Llamatik",
        displayName = "Gemma 2B IT (Q4_K_M)",
        sizeDescription = "~1.7 GB, GGUF",
        fileName = "gemma-2-2b-it-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
    ),
    Llama32_1B_Gguf(
        "Llamatik",
        displayName = "Llama 3.2 1B (Q4_K_M)",
        sizeDescription = "~800 MB, GGUF",
        fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
    ),
    TinyLlamaGguf(
        "Llamatik",
        displayName = "TinyLlama 1.1B (Q4_K_M)",
        sizeDescription = "~670 MB, GGUF",
        fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
    ),
    Qwen05BGguf(
        "LlamaCpp",
        displayName = "Qwen 2.5 0.5B (Q4_K_M)",
        sizeDescription = "~400 MB, GGUF",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
    ),
    SmolLM2_135M_Gguf(
        "PocketPal",
        displayName = "SmolLM2 135M (Q4_K_M)",
        sizeDescription = "~100 MB, GGUF",
        fileName = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
    ),
    Qwen05B_Pocket_Gguf(
        "PocketPal",
        displayName = "Qwen 2.5 0.5B (Q4_K_M)",
        sizeDescription = "~400 MB, GGUF",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
    ),

    // LiteRT-LM (GenAI priority for GeminiNano / MediaPipe / AiEdge agent slots)
    Gemma3_270m_LiteRt_GeminiNano(
        "GeminiNano",
        displayName = "Gemma 3 270M IT (Q8, LiteRT-LM)",
        sizeDescription = "~290 MB, .litertlm",
        fileName = "gemma3-270m-it-q8.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm"
    ),
    Gemma3_270m_LiteRt_MediaPipe(
        "MediaPipe",
        displayName = "Gemma 3 270M IT (Q8, LiteRT-LM)",
        sizeDescription = "~290 MB, .litertlm",
        fileName = "gemma3-270m-it-q8.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm"
    ),
    Gemma3_270m_LiteRt_AiEdge(
        "AiEdge",
        displayName = "Gemma 3 270M IT (Q8, LiteRT-LM)",
        sizeDescription = "~290 MB, .litertlm",
        fileName = "gemma3-270m-it-q8.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm"
    ),

    // Gemini Nano / MediaPipe / AiEdge: GGUF fallback (Llamatik)
    Gemma2BMediaPipe(
        "GeminiNano",
        displayName = "Gemma 2B IT (Q4_K_M)",
        sizeDescription = "~1.7 GB, GGUF",
        fileName = "gemma-2-2b-it-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"
    ),
    Phi2MediaPipe(
        "MediaPipe",
        displayName = "Phi-2 (Q4_0)",
        sizeDescription = "~1.6 GB, GGUF",
        fileName = "phi-2.Q4_0.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_0.gguf"
    ),

    // RunAnywhere / AI Edge
    Qwen05B_Edge_Gguf(
        "AiEdge",
        displayName = "Qwen 2.5 0.5B (Q4_K_M)",
        sizeDescription = "~400 MB, GGUF",
        fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
    ),
    SmolLM2_135M_Run_Gguf(
        "RunAnywhere",
        displayName = "SmolLM2 135M (Q4_K_M)",
        sizeDescription = "~100 MB, GGUF",
        fileName = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
    ),

    // MLC-LLM slot: same GGUF runtime as Llamatik (full phi-2 GGUF; not MLC bundle).
    Phi2_Mlc(
        "MlcLlm",
        displayName = "Phi-2 (Q4_0)",
        sizeDescription = "~1.6 GB, GGUF",
        fileName = "phi-2.Q4_0.gguf",
        downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_0.gguf"
    )
}

/**
 * On-device model path checks and downloads. Files live under [Context.getFilesDir]/models/<agentName>/.
 */
class LlamatikModelHelper(private val context: Context) {

    companion object {
        /** Default asset-relative path when no download has been done. */
        const val DEFAULT_ASSET_PATH = "models/phi-2.Q4_0.gguf"
    }

    private fun fileForVariant(variant: LlamatikModelVariant): File =
        File(context.filesDir, "models/${variant.forAgentName}/${variant.fileName}")

    /** True if [modelPath] exists as a file or as an asset (when not absolute). */
    fun isModelDownloaded(modelPath: String): Boolean {
        val path = modelPath.trim()
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

    fun isVariantDownloaded(variant: LlamatikModelVariant): Boolean =
        fileForVariant(variant).exists()

    fun getDisplayPath(modelPath: String): String {
        val path = modelPath.trim()
        if (path.isBlank()) return DEFAULT_ASSET_PATH
        return if (isAbsolutePath(path)) path else "assets: $path"
    }

    fun getDownloadDestinationPath(variant: LlamatikModelVariant): String =
        fileForVariant(variant).absolutePath

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

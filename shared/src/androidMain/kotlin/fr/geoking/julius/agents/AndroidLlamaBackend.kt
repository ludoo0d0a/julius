package fr.geoking.julius.agents

import android.content.Context
import com.llamatik.library.platform.LlamaBridge
import java.io.File

/**
 * Android [LlamaBackend]: resolves asset-relative GGUF paths like Llamatik's `@Composable`
 * [LlamaBridge.getModelPath] (copy under [Context.getCacheDir]) so JNI receives a real file path.
 */
class AndroidLlamaBackend(private val context: Context) : LlamaBackend {

    override fun getModelPath(assetPath: String): String {
        val trimmed = assetPath.trim()
        if (trimmed.isEmpty()) return trimmed
        val asFile = File(trimmed)
        if ((asFile.isAbsolute || trimmed.contains(":\\")) && asFile.exists()) {
            return asFile.absolutePath
        }
        val outFile = File(context.cacheDir, trimmed)
        if (!outFile.exists()) {
            outFile.parentFile?.mkdirs()
            context.assets.open(trimmed).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile.absolutePath
    }

    override fun initGenerateModel(fullPath: String): Boolean = LlamaBridge.initGenerateModel(fullPath)

    override fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String =
        LlamaBridge.generateWithContext(systemPrompt, contextBlock, userPrompt)

    override fun onGenerateModelInitialized() {
        LlamaBridge.updateGenerateParams(
            temperature = 0.7f,
            maxTokens = 256,
            topP = 0.95f,
            topK = 40,
            repeatPenalty = 1.1f
        )
    }

    override fun shutdown() = LlamaBridge.shutdown()
}

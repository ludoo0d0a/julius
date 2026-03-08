package fr.geoking.julius.agents

import com.llamatik.library.platform.LlamaBridge

/**
 * Default [LlamaBackend] that delegates to Llamatik's [LlamaBridge].
 * On Android, prefer resolving the model path in a Composable (LlamaBridge.getModelPath)
 * and passing a backend that returns that path, or place the model at a readable file path.
 */
object DefaultLlamaBackend : LlamaBackend {
    override fun getModelPath(assetPath: String): String = assetPath
    override fun initGenerateModel(fullPath: String): Boolean = LlamaBridge.initGenerateModel(fullPath)
    override fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String =
        LlamaBridge.generateWithContext(systemPrompt, contextBlock, userPrompt)
    override fun shutdown() = LlamaBridge.shutdown()
}

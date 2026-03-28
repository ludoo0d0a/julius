package fr.geoking.julius.agents

import com.llamatik.library.platform.LlamaBridge

/**
 * Default [LlamaBackend] that delegates to Llamatik's [LlamaBridge].
 * On Android, [getModelPath] does not copy assets; inject `AndroidLlamaBackend` from the app module so
 * [initGenerateModel] receives a real filesystem path (Llamatik copies assets under `cacheDir`, same as
 * the library's `@Composable` [LlamaBridge.getModelPath]).
 */
object DefaultLlamaBackend : LlamaBackend {
    override fun getModelPath(assetPath: String): String = assetPath
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

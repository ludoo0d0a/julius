package fr.geoking.julius.agents

/**
 * Fake [LlamaBackend] for unit testing [LlamatikAgent] without the native Llamatik dependency.
 */
class FakeLlamaBackend(
    private val initResult: Boolean = true,
    private val response: String = "Fake response.",
    private val initThrow: Throwable? = null,
    private val generateThrow: Throwable? = null
) : LlamaBackend {
    var getModelPathCalls = mutableListOf<String>()
    var initGenerateModelCalls = mutableListOf<String>()
    var generateWithContextCalls = mutableListOf<Triple<String, String, String>>()
    var shutdownCalls = 0

    override fun getModelPath(assetPath: String): String {
        getModelPathCalls.add(assetPath)
        return assetPath
    }

    override fun initGenerateModel(fullPath: String): Boolean {
        initGenerateModelCalls.add(fullPath)
        initThrow?.let { throw it }
        return initResult
    }

    override fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String {
        generateWithContextCalls.add(Triple(systemPrompt, contextBlock, userPrompt))
        generateThrow?.let { throw it }
        return response
    }

    override fun onGenerateModelInitialized() {}

    override fun shutdown() {
        shutdownCalls++
    }
}

package fr.geoking.julius.agents

/**
 * Abstraction over Llamatik LlamaBridge for on-device LLM inference.
 * Allows testing [LlamatikAgent] without the native Llamatik dependency.
 */
interface LlamaBackend {
    /** Resolves model path (e.g. copies asset to readable path on Android). */
    fun getModelPath(assetPath: String): String

    /** Initializes the generation model. Returns true on success. */
    fun initGenerateModel(fullPath: String): Boolean

    /** Generates text with system/context/user prompts. */
    fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String

    /**
     * Called after [initGenerateModel] returns true. Apply conversational defaults per Llamatik docs
     * ([LlamaBridge.updateGenerateParams](https://docs.llamatik.com/api/llamabridge/)).
     */
    fun onGenerateModelInitialized()

    /** Releases native resources. */
    fun shutdown()
}

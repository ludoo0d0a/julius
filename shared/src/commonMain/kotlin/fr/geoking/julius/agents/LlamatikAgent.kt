package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-device/offline LLM inference using Llamatik (GGUF).
 *
 * Runs without API keys or network. Models must be in GGUF format in assets or an accessible file path.
 *
 * Recommended models for Android:
 * - Phi-2 (Q4_0): ~1.6GB, good quality for small devices
 * - Gemma 2B (Q4_0): ~1.4GB, optimized for mobile
 * - TinyLlama (Q4_0): ~650MB, fastest but lower quality
 *
 * Model files should be placed in: androidApp/src/main/assets/models/
 * Example: androidApp/src/main/assets/models/phi-2.Q4_0.gguf
 *
 * @param modelPath Asset-relative or absolute path to the GGUF model (e.g. "models/phi-2.Q4_0.gguf").
 * @param backend Optional backend; if null, uses [DefaultLlamaBackend] (Llamatik). Inject a fake in tests.
 */
class LlamatikAgent(
    private val modelPath: String = "models/phi-2.Q4_0.gguf",
    private val backend: LlamaBackend? = DefaultLlamaBackend
) : ConversationalAgent {

    private val mutex = Mutex()
    private var isModelInitialized = false
    private val systemPrompt = "You are a helpful and concise voice assistant. Provide clear, brief responses suitable for voice interaction."

    private fun getBackend(): LlamaBackend = backend ?: DefaultLlamaBackend

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? =
        llamatikModelSetupFromInput(input)

    private fun initializeModelIfNeeded() {
        if (isModelInitialized) return
        val bridge = getBackend()
        try {
            val fullModelPath = bridge.getModelPath(modelPath)
            val ok = bridge.initGenerateModel(fullModelPath)
            if (!ok) {
                throw IllegalStateException("Failed to initialize Llamatik model at: $fullModelPath")
            }
            bridge.onGenerateModelInitialized()
            isModelInitialized = true
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(
                null,
                "Failed to load Llamatik model: ${e.message}. Make sure the model file exists at '$modelPath' in assets."
            )
        }
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        val bridge = getBackend()
        try {
            if (!isModelInitialized) {
                initializeModelIfNeeded()
            }
            val response = bridge.generateWithContext(
                systemPrompt = systemPrompt,
                contextBlock = "",
                userPrompt = input
            )
            return AgentResponse(text = response.trim(), audio = null)
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(null, "Error with Llamatik model: ${e.message}")
        }
    }

    /**
     * Cleanup resources when done. Call when switching away from [LlamatikAgent] or when the app is closing.
     */
    fun shutdown() {
        if (isModelInitialized) {
            try {
                getBackend().shutdown()
            } catch (e: Exception) {
                // ignore
            }
            isModelInitialized = false
        }
    }
}

package fr.geoking.julius.agents

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import fr.geoking.julius.shared.network.NetworkException
import fr.geoking.julius.shared.logging.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device inference via [Google AI Edge LiteRT-LM](https://ai.google.dev/edge/litert-lm/get-started/android).
 * [modelPath] must point to a `.litertlm` file on device storage (e.g. after download from Settings).
 *
 * Backends: tries **GPU**, then **NPU** ([nativeLibraryDir]), then **CPU** so devices use acceleration when the
 * model and runtime support it; otherwise load still succeeds on CPU.
 */
class LiteRtLmOnDeviceAgent(
    private val context: Context,
    private val modelPath: String,
    private val extendedActionsEnabled: Boolean = false,
) : ConversationalAgent {

    private val mutex = Mutex()
    private var engine: Engine? = null

    private val baseSystemInstruction =
        "You are a helpful, concise voice assistant. Give brief responses suitable for spoken output."

    private fun resolvedSystemInstruction(): String =
        if (extendedActionsEnabled) {
            "$baseSystemInstruction\n\n${ExtendedToolActionRegistry.localModelExtendedActionsSystemAddendum()}"
        } else {
            baseSystemInstruction
        }

    override fun evaluateSetupIssue(input: AgentSetupInput): AgentSetupDescriptor? =
        llamatikModelSetupFromInput(input)

    private fun engineConfigsForModel(modelAbsolutePath: String, cacheDir: String): List<EngineConfig> {
        val npuDir = context.applicationInfo.nativeLibraryDir
        return listOf(
            EngineConfig(modelPath = modelAbsolutePath, backend = Backend.GPU(), cacheDir = cacheDir),
            EngineConfig(
                modelPath = modelAbsolutePath,
                backend = Backend.NPU(nativeLibraryDir = npuDir),
                cacheDir = cacheDir,
            ),
            EngineConfig(modelPath = modelAbsolutePath, backend = Backend.CPU(), cacheDir = cacheDir),
        )
    }

    private fun ensureEngine(): Engine {
        engine?.let { return it }
        val path = modelPath.trim()
        if (path.isEmpty()) throw NetworkException(null, "LiteRT-LM model path is empty.")
        val f = File(path)
        if (!f.isFile) throw NetworkException(null, "LiteRT-LM model not found: $path")
        val cacheDir = context.cacheDir.absolutePath
        val modelAbs = f.absolutePath

        log.d { "LiteRT-LM initializing engine. path=$path" }

        var lastError: Throwable? = null
        for (cfg in engineConfigsForModel(modelAbs, cacheDir)) {
            val e = Engine(cfg)
            try {
                e.initialize()
                engine = e
                return e
            } catch (t: Throwable) {
                try {
                    e.close()
                } catch (_: Throwable) {
                }
                val mem = MemoryHelper.getMemoryReport(context)
                log.w(t) { "LiteRT-LM initialization failed for backend ${cfg.backend}. $mem" }
                lastError = t
            }
        }
        val mem = MemoryHelper.getMemoryReport(context)
        val detail = lastError?.toString() ?: "Unknown error"
        throw NetworkException(null, "LiteRT-LM failed to load model: $detail. $mem")
    }

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                val userText = extractLastUserMessage(input)
                val eng = ensureEngine()
                val convCfg = ConversationConfig(
                    systemInstruction = Contents.of(resolvedSystemInstruction()),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
                )
                eng.createConversation(convCfg).use { conversation ->
                    val msg = conversation.sendMessage(userText)
                    LocalExtendedToolSupport.augmentWithToolCallsIfNeeded(
                        modelText = messageText(msg),
                        fullConversationPrompt = input,
                        extendedActionsEnabled = extendedActionsEnabled,
                    )
                }
            } catch (e: NetworkException) {
                throw e
            } catch (t: Throwable) {
                val mem = MemoryHelper.getMemoryReport(context)
                val msg = t.toString()
                log.e(t) { "LiteRT-LM process crash. $msg. $mem" }
                throw NetworkException(null, "LiteRT-LM error: $msg. $mem")
            }
        }
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
        engine = null
    }

    private fun extractLastUserMessage(fullPrompt: String): String {
        val userPrefix = "User:"
        val lastUserIdx = fullPrompt.lastIndexOf(userPrefix)
        return if (lastUserIdx >= 0) {
            fullPrompt.substring(lastUserIdx + userPrefix.length).trim()
        } else {
            fullPrompt.trim()
        }
    }

    private fun messageText(msg: Message): String {
        val texts = msg.contents.contents.mapNotNull { c ->
            (c as? Content.Text)?.text
        }
        if (texts.isNotEmpty()) return texts.joinToString("")
        return msg.toString()
    }
}

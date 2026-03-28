package fr.geoking.julius.agents

/**
 * Snapshot of settings needed for [ConversationalAgent.evaluateSetupIssue].
 * Built on Android from app settings and the Llamatik model helper.
 */
data class AgentSetupInput(
    val openAiKey: String = "",
    val geminiKey: String = "",
    val perplexityKey: String = "",
    val elevenLabsKey: String = "",
    val deepgramKey: String = "",
    val firebaseAiKey: String = "",
    val opencodeZenKey: String = "",
    val completionsMeKey: String = "",
    val apifreellmKey: String = "",
    /** Display name of the selected agent (e.g. enum name) for user-facing messages. */
    val selectedAgentDisplayName: String,
    /** Whether the configured Llamatik / on-device model path resolves to an existing file or asset. */
    val hasLlamatikModelFile: Boolean = false,
    val firstLlamatikVariantDisplay: String? = null,
    val firstLlamatikVariantSizeDescription: String? = null
)

sealed class AgentSetupDescriptor {
    abstract val message: String

    data class MissingApiKey(override val message: String) : AgentSetupDescriptor()
    data class MissingLlamatikModel(override val message: String) : AgentSetupDescriptor()
}

fun llamatikModelSetupFromInput(input: AgentSetupInput): AgentSetupDescriptor.MissingLlamatikModel? {
    if (input.hasLlamatikModelFile) return null
    val message = if (input.firstLlamatikVariantDisplay != null && input.firstLlamatikVariantSizeDescription != null) {
        "No model file found. Download ${input.firstLlamatikVariantDisplay} (${input.firstLlamatikVariantSizeDescription}) in Settings. Tap to open."
    } else {
        "No model file found for ${input.selectedAgentDisplayName}. Configure or download a model in Settings. Tap to open."
    }
    return AgentSetupDescriptor.MissingLlamatikModel(message)
}

internal fun missingApiKeyMessage(agentDisplayName: String): String =
    "Add your API key for $agentDisplayName in Settings. Tap to open."

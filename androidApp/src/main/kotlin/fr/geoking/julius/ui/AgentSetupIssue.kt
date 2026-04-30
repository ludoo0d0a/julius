package fr.geoking.julius.ui

import fr.geoking.julius.AgentType
import fr.geoking.julius.AppSettings
import fr.geoking.julius.agents.AgentSetupDescriptor
import fr.geoking.julius.agents.AgentSetupInput
import fr.geoking.julius.agents.ConversationalAgent
import fr.geoking.julius.agents.LlamatikModelHelper
import fr.geoking.julius.agents.LlamatikModelVariant

/**
 * Proactive setup problem for the current conversational agent (API key or Llamatik model).
 * Android-facing type; logic lives on each [ConversationalAgent.evaluateSetupIssue].
 */
sealed class AgentSetupIssue {
    abstract val agent: AgentType
    abstract val message: String

    data class MissingApiKey(
        override val agent: AgentType,
        override val message: String
    ) : AgentSetupIssue()

    data class MissingLlamatikModel(
        override val agent: AgentType,
        override val message: String
    ) : AgentSetupIssue()
}

fun AppSettings.toAgentSetupInput(helper: LlamatikModelHelper): AgentSetupInput {
    val type = selectedAgent
    val firstVariant = LlamatikModelVariant.entries.firstOrNull { it.forAgentName == type.name }
    return AgentSetupInput(
        openAiKey = openAiKey,
        geminiKey = geminiKey,
        perplexityKey = perplexityKey,
        elevenLabsKey = elevenLabsKey,
        deepgramKey = deepgramKey,
        firebaseAiKey = firebaseAiKey,
        opencodeZenKey = opencodeZenKey,
        completionsMeKey = completionsMeKey,
        apifreellmKey = apifreellmKey,
        deepSeekKey = deepSeekKey,
        groqKey = groqKey,
        openRouterKey = openRouterKey,
        selectedAgentDisplayName = type.name,
        hasLlamatikModelFile = helper.isModelDownloaded(llamatikModelPath),
        firstLlamatikVariantDisplay = firstVariant?.displayName,
        firstLlamatikVariantSizeDescription = firstVariant?.sizeDescription
    )
}

private fun AgentSetupDescriptor.toAgentIssue(agentType: AgentType): AgentSetupIssue = when (this) {
    is AgentSetupDescriptor.MissingApiKey -> AgentSetupIssue.MissingApiKey(agentType, message)
    is AgentSetupDescriptor.MissingLlamatikModel -> AgentSetupIssue.MissingLlamatikModel(agentType, message)
}

fun evaluateAgentSetup(
    settings: AppSettings,
    helper: LlamatikModelHelper,
    agent: ConversationalAgent
): AgentSetupIssue? {
    val input = settings.toAgentSetupInput(helper)
    val descriptor = agent.evaluateSetupIssue(input) ?: return null
    return descriptor.toAgentIssue(settings.selectedAgent)
}

/** Stack to open phone Settings on the current agent's API key / model download screen. */
fun agentConfigSettingsPages(): List<SettingsScreenPage> = listOf(SettingsScreenPage.Main)

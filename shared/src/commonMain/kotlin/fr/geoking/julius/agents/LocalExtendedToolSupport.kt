package fr.geoking.julius.agents

import fr.geoking.julius.shared.ActionParser
import fr.geoking.julius.shared.ActionType
import fr.geoking.julius.shared.DeviceAction
import kotlin.random.Random

/**
 * Bridges local on-device agents to the same [ToolCall] / [ConversationStore] tool loop as OpenAI/Gemini.
 * Uses [ActionParser] on model output and on the last user turn (aligned with [ExtendedToolActionRegistry]).
 */
object LocalExtendedToolSupport {

    fun newLocalToolCallId(): String = "local_" + Random.nextLong().toULong().toString(16)

    fun briefAckForExtendedAction(action: DeviceAction): String = when (action.type) {
        ActionType.GET_LOCATION -> "Getting your location."
        ActionType.SHOW_MAP -> "Opening the map."
        ActionType.GET_BATTERY_LEVEL -> "Checking battery level."
        ActionType.GET_VOLUME_LEVEL -> "Checking volume levels."
        ActionType.NAVIGATE -> "Starting navigation."
        ActionType.GET_WEATHER -> "Getting weather."
        ActionType.GET_TRAFFIC -> "Loading traffic."
        ActionType.FIND_GAS_STATIONS, ActionType.FIND_ELECTRIC_STATIONS, ActionType.FIND_HYBRID_STATIONS ->
            "Searching nearby stations."
        ActionType.FIND_PARKING -> "Searching parking."
        ActionType.FIND_RESTAURANTS -> "Searching restaurants."
        ActionType.FIND_FASTFOOD -> "Searching fast food."
        ActionType.FIND_SERVICE_AREA -> "Searching service areas."
        ActionType.PLAY_MUSIC -> "Playing music."
        ActionType.PLAY_AUDIOBOOK -> "Playing audiobook."
        ActionType.CALL_CONTACT -> "Calling."
        ActionType.EMERGENCY_CALL -> "Emergency call."
        ActionType.FIND_HOSPITAL -> "Finding a hospital."
        ActionType.ROADSIDE_ASSISTANCE -> "Roadside assistance."
        else -> "OK."
    }

    /**
     * Last `User:` block in [ConversationStore] history (may span multiple lines until `Assistant:`).
     */
    fun extractLastUserTurn(fullConversationPrompt: String): String {
        val userPrefix = "User:"
        val lastUserIdx = fullConversationPrompt.lastIndexOf(userPrefix)
        if (lastUserIdx < 0) return fullConversationPrompt.trim()
        val after = fullConversationPrompt.substring(lastUserIdx + userPrefix.length).trim()
        val assistantIdx = after.indexOf("\nAssistant:")
        return if (assistantIdx >= 0) after.substring(0, assistantIdx).trim() else after.trim()
    }

    /**
     * When [extendedActionsEnabled], map parsed device intents to [AgentResponse.toolCalls] so the store runs
     * the same executor + follow-up [process] round as remote tool APIs.
     */
    fun augmentWithToolCallsIfNeeded(
        modelText: String,
        fullConversationPrompt: String,
        extendedActionsEnabled: Boolean,
    ): AgentResponse {
        val trimmed = modelText.trim()
        if (!extendedActionsEnabled) {
            return AgentResponse(text = trimmed, audio = null)
        }
        val lastUser = extractLastUserTurn(fullConversationPrompt)
        val action =
            ActionParser.parseActionFromResponse(trimmed)
                ?: ActionParser.parseActionFromResponse(lastUser)
                ?: return AgentResponse(text = trimmed, audio = null)
        return AgentResponse(
            text = trimmed.ifBlank { briefAckForExtendedAction(action) },
            toolCalls = listOf(ToolCall(id = newLocalToolCallId(), action = action)),
            audio = null,
        )
    }
}

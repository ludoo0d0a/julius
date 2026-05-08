package fr.geoking.julius.agents

import fr.geoking.julius.shared.action.ActionType

/**
 * Single source of truth for extended / device tool names used by OpenAI, Gemini, and local agents.
 * OpenAI-style function names match [AGENTS.md] extended actions.
 */
object ExtendedToolActionRegistry {

    fun apiToolNameToActionType(name: String): ActionType? = when (name) {
        "get_battery_level" -> ActionType.GET_BATTERY_LEVEL
        "get_volume_levels" -> ActionType.GET_VOLUME_LEVEL
        "play_music" -> ActionType.PLAY_MUSIC
        "play_audiobook" -> ActionType.PLAY_AUDIOBOOK
        else -> null
    }

    /**
     * Appended to on-device LLM system prompts when extended actions are enabled.
     * Wording aligns with [fr.geoking.julius.shared.action.ActionParser] so replies can trigger the same device-action pipeline as cloud agents.
     */
    fun localModelExtendedActionsSystemAddendum(): String = """
Extended device capabilities (same as cloud Julius tools). When the user wants one of these, acknowledge briefly and include recognizable phrases in your reply so the app can run the action:
• Battery: say "battery level" or "niveau de batterie".
• Volume: say "volume levels" or "niveau du volume".
• Music: "play music …" / "jouer musique …"; audiobook: "audiobook" / "livre audio".
Keep answers short for voice. Do not claim you executed an action unless your wording matches the patterns above.
    """.trimIndent()
}

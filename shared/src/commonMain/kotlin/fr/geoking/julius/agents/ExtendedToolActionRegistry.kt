package fr.geoking.julius.agents

import fr.geoking.julius.shared.ActionType

/**
 * Single source of truth for extended / device tool names used by OpenAI, Gemini, and local agents.
 * OpenAI-style function names match [AGENTS.md] extended actions.
 */
object ExtendedToolActionRegistry {

    fun apiToolNameToActionType(name: String): ActionType? = when (name) {
        "get_location" -> ActionType.GET_LOCATION
        "show_map" -> ActionType.SHOW_MAP
        "get_battery_level" -> ActionType.GET_BATTERY_LEVEL
        "get_volume_levels" -> ActionType.GET_VOLUME_LEVEL
        "find_gas_stations_nearby" -> ActionType.FIND_GAS_STATIONS
        "find_electric_stations_nearby" -> ActionType.FIND_ELECTRIC_STATIONS
        "find_hybrid_stations_nearby" -> ActionType.FIND_HYBRID_STATIONS
        "find_parking_nearby" -> ActionType.FIND_PARKING
        "find_restaurants_nearby" -> ActionType.FIND_RESTAURANTS
        "find_fastfood_nearby" -> ActionType.FIND_FASTFOOD
        "find_service_area_nearby" -> ActionType.FIND_SERVICE_AREA
        "get_traffic_info" -> ActionType.GET_TRAFFIC
        "get_weather" -> ActionType.GET_WEATHER
        "play_music" -> ActionType.PLAY_MUSIC
        "play_audiobook" -> ActionType.PLAY_AUDIOBOOK
        "call_contact" -> ActionType.CALL_CONTACT
        "find_nearest_hospital" -> ActionType.FIND_HOSPITAL
        "request_roadside_assistance" -> ActionType.ROADSIDE_ASSISTANCE
        "emergency_call" -> ActionType.EMERGENCY_CALL
        "navigate_to" -> ActionType.NAVIGATE
        else -> null
    }

    /**
     * Appended to on-device LLM system prompts when extended actions are enabled.
     * Wording aligns with [fr.geoking.julius.shared.ActionParser] so replies can trigger the same device-action pipeline as cloud agents.
     */
    fun localModelExtendedActionsSystemAddendum(): String = """
Extended device capabilities (same as cloud Julius tools). When the user wants one of these, acknowledge briefly and include recognizable phrases in your reply so the app can run the action:
• Location / map: say "current location", "where am I", "show map", or "open map".
• Battery: say "battery level" or "niveau de batterie".
• Volume: say "volume levels" or "niveau du volume".
• Fuel: "gas station", "station essence", "carburant".
• Charging: "charging station", "electric station", "borne électrique".
• Hybrid: "hybrid station", "electric and gas station".
• Parking, restaurants, fast food, service area: use those words (EN/FR as appropriate).
• Traffic: "traffic" / "trafic"; weather: "weather" / "météo" / "weather in Paris".
• Music: "play music …" / "jouer musique …"; audiobook: "audiobook" / "livre audio".
• Navigation: "navigate to …" / "naviguer vers …".
• Call: "call …" / "appeler …"; emergency: "emergency call", "112".
• Hospital, roadside assistance: "hospital", "roadside assistance", "dépannage".
Keep answers short for voice. Do not claim you executed an action unless your wording matches the patterns above.
    """.trimIndent()
}

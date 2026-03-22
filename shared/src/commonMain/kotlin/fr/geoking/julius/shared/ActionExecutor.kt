package fr.geoking.julius.shared

interface ActionExecutor {
    suspend fun executeAction(action: DeviceAction): ActionResult
}

enum class ActionType {
    OPEN_APP,
    SEND_MESSAGE,
    PLAY_MUSIC,
    NAVIGATE,
    SET_ALARM,
    GET_LOCATION,
    GET_BATTERY_LEVEL,
    GET_VOLUME_LEVEL,
    REQUEST_PERMISSION,
    FIND_GAS_STATIONS,
    FIND_ELECTRIC_STATIONS,
    FIND_PARKING,
    FIND_RESTAURANTS,
    FIND_FASTFOOD,
    FIND_SERVICE_AREA,
    GET_TRAFFIC,
    GET_WEATHER,
    PLAY_AUDIOBOOK,
    CALL_CONTACT,
    FIND_HOSPITAL,
    FIND_RADARS,
    ROADSIDE_ASSISTANCE,
    EMERGENCY_CALL,
    OTHER
}

data class DeviceAction(
    val type: ActionType,
    val target: String? = null, // App package, phone number, etc.
    val data: Map<String, String> = emptyMap() // Additional parameters
)

data class ActionResult(
    val success: Boolean,
    val message: String
)


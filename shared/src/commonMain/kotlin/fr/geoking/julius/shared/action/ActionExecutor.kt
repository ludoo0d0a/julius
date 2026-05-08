package fr.geoking.julius.shared.action

interface ActionExecutor {
    suspend fun executeAction(action: DeviceAction): ActionResult
}

enum class ActionType {
    OPEN_APP,
    SEND_MESSAGE,
    PLAY_MUSIC,
    SET_ALARM,
    GET_BATTERY_LEVEL,
    GET_VOLUME_LEVEL,
    REQUEST_PERMISSION,
    PLAY_AUDIOBOOK,
    GET_NETWORK_STATUS,
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


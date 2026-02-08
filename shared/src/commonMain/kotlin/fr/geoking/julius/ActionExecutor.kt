package fr.geoking.julius.shared

interface ActionExecutor {
    suspend fun executeAction(action: DeviceAction): ActionResult
}

enum class ActionType {
    OPEN_APP,
    SEND_MESSAGE,
    MAKE_CALL,
    PLAY_MUSIC,
    NAVIGATE,
    SET_ALARM,
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


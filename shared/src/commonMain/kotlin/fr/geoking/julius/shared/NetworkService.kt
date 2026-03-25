package fr.geoking.julius.shared

import kotlinx.coroutines.flow.StateFlow

enum class NetworkType {
    GPRS,
    EDGE,
    TWO_G,
    THREE_G,
    FOUR_G,
    FIVE_G,
    WIFI,
    UNKNOWN,
    NONE
}

data class NetworkStatus(
    val countryCode: String? = null,
    val countryName: String? = null,
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val isRoaming: Boolean = false,
    val operatorName: String? = null,
    val isConnected: Boolean = false
)

interface NetworkService {
    val status: StateFlow<NetworkStatus>
    suspend fun getCurrentStatus(): NetworkStatus
}

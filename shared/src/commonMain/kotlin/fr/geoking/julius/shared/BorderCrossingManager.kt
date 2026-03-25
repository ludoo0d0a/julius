package fr.geoking.julius.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BorderCrossingManager(
    private val scope: CoroutineScope,
    private val networkService: NetworkService,
    private val conversationStore: ConversationStore
) {
    private var lastCountryCode: String? = null

    init {
        scope.launch {
            networkService.status.collectLatest { status ->
                val currentCountry = status.countryCode
                if (currentCountry != null && lastCountryCode != null && currentCountry != lastCountryCode) {
                    val countryName = status.countryName ?: currentCountry
                    val roamingInfo = if (status.isRoaming) " (roaming)" else ""
                    val networkInfo = if (status.isConnected) " via ${status.networkType.name}$roamingInfo" else ""

                    val message = "Welcome to $countryName! You are connected to ${status.operatorName}$networkInfo."

                    // Add message to UI and speak it (interruptible)
                    conversationStore.onUserFinishedSpeaking("CROSS_BORDER_EVENT_INTERNAL: $message")
                }
                if (currentCountry != null) {
                    lastCountryCode = currentCountry
                }
            }
        }
    }
}

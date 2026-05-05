package fr.geoking.julius.shared.location

import fr.geoking.julius.shared.conversation.ConversationStore
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.platform.PlatformNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BorderCrossingManager(
    private val scope: CoroutineScope,
    private val networkService: NetworkService,
    private val conversationStore: ConversationStore,
    private val notificationManager: PlatformNotificationManager
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

                    val networkSwitchInfo = when {
                        status.telephonyCountryCode == null -> ""
                        status.telephonyCountryCode == currentCountry -> " Your network has successfully switched to $countryName."
                        status.telephonyCountryCode == lastCountryCode -> " You are still using the network from ${lastCountryCode}."
                        else -> " Your network is connected to ${status.telephonyCountryCode}."
                    }

                    val fullMessage = "Welcome to $countryName! You are connected to ${status.operatorName}$networkInfo.$networkSwitchInfo"
                    val toastMessage = "Welcome to $countryName; network ${status.operatorName}$networkInfo"

                    // Show high priority notification (HUN)
                    notificationManager.showHighPriorityNotification(
                        title = "Border Crossed",
                        message = toastMessage
                    )

                    // Add message to UI and speak it (interruptible)
                    conversationStore.onUserFinishedSpeaking("CROSS_BORDER_EVENT_INTERNAL: $fullMessage")
                }
                if (currentCountry != null) {
                    lastCountryCode = currentCountry
                }
            }
        }
    }
}

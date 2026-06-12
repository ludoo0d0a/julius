package fr.geoking.julius.navigation

sealed class HarnessRoute {
    data object QueueDashboard : HarnessRoute()
    data object Projects : HarnessRoute()
    data class Features(val sourceName: String, val displayName: String) : HarnessRoute()
    data class Conversations(
        val sourceName: String,
        val featureId: String,
        val featureTitle: String,
    ) : HarnessRoute()
    data class Chat(val sessionId: String) : HarnessRoute()
    data class PrConflict(val sessionId: String) : HarnessRoute()
    data class ActivitiesDebug(val sessionId: String, val activitiesJson: String) : HarnessRoute()
    data class AddFeature(val sourceName: String? = null) : HarnessRoute()
    data class EditFeature(val featureId: String) : HarnessRoute()
}

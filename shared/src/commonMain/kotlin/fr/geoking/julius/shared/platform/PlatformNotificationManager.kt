package fr.geoking.julius.shared.platform

interface PlatformNotificationManager {
    fun showHighPriorityNotification(title: String, message: String)
    fun showPersistentNotification(title: String, message: String, id: Int)
    fun cancelNotification(id: Int)
}

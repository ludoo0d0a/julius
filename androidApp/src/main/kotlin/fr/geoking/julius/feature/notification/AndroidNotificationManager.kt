package fr.geoking.julius.feature.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.geoking.julius.R
import fr.geoking.julius.shared.platform.PlatformNotificationManager

class AndroidNotificationManager(private val context: Context) : PlatformNotificationManager {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_ALERTS = "julius_alerts"
        const val CHANNEL_ID_PERSISTENT = "julius_persistent"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alerts like border crossings"
                enableVibration(true)
            }

            val persistentChannel = NotificationChannel(
                CHANNEL_ID_PERSISTENT,
                "Background Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notifications for background monitoring"
            }

            notificationManager.createNotificationChannel(alertsChannel)
            notificationManager.createNotificationChannel(persistentChannel)
        }
    }

    override fun showHighPriorityNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun showPersistentNotification(title: String, message: String, id: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PERSISTENT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        notificationManager.notify(id, notification)
    }

    override fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}

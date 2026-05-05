package fr.geoking.julius.auto

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.geoking.julius.R
import fr.geoking.julius.feature.notification.AndroidNotificationManager
import fr.geoking.julius.shared.location.BorderCrossingManager
import org.koin.android.ext.android.inject

class BorderMonitorService : Service() {

    private val borderCrossingManager: BorderCrossingManager by inject()

    private fun showForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AndroidNotificationManager.CHANNEL_ID_PERSISTENT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Julius Border Monitoring")
            .setContentText("Monitoring for border crossings...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1001, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            stopSelf()
            return START_NOT_STICKY
        }

        showForegroundNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

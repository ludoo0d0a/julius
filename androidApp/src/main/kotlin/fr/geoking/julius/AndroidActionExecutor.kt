package fr.geoking.julius

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import fr.geoking.julius.shared.ActionExecutor
import fr.geoking.julius.shared.ActionResult
import fr.geoking.julius.shared.ActionType
import fr.geoking.julius.shared.DeviceAction
import fr.geoking.julius.shared.PermissionManager

class AndroidActionExecutor(
    private val context: Context,
    private val permissionManager: PermissionManager
) : ActionExecutor {

    override suspend fun executeAction(action: DeviceAction): ActionResult {
        return try {
            when (action.type) {
                ActionType.OPEN_APP -> openApp(action.target)
                ActionType.SEND_MESSAGE -> sendMessage(action.target, action.data["message"] ?: "")
                ActionType.MAKE_CALL -> makeCall(action.target)
                ActionType.PLAY_MUSIC -> playMusic()
                ActionType.NAVIGATE -> navigate(action.target, action.data)
                ActionType.SET_ALARM -> setAlarm(action.data)
                ActionType.GET_LOCATION -> getLocation()
                ActionType.GET_BATTERY_LEVEL -> getBatteryLevel()
                ActionType.GET_VOLUME_LEVEL -> getVolumeLevels()
                ActionType.REQUEST_PERMISSION -> requestPermission(action.target)
                ActionType.OTHER -> executeOtherAction(action)
            }
        } catch (e: Exception) {
            ActionResult(false, "Error executing action: ${e.message}")
        }
    }

    private fun openApp(packageName: String?): ActionResult {
        if (packageName == null) {
            return ActionResult(false, "No app package specified")
        }

        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ActionResult(true, "Opened app: $packageName")
            } else {
                // Try to resolve by app name
                val intent2 = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val resolveInfos = context.packageManager.queryIntentActivities(intent2, 0)
                val matching = resolveInfos.find { 
                    it.loadLabel(context.packageManager).toString().contains(packageName, ignoreCase = true) 
                }
                
                if (matching != null) {
                    intent2.setClassName(matching.activityInfo.packageName, matching.activityInfo.name)
                    context.startActivity(intent2)
                    return ActionResult(true, "Opened app: ${matching.loadLabel(context.packageManager)}")
                }
                
                return ActionResult(false, "App not found: $packageName")
            }
        } catch (e: Exception) {
            return ActionResult(false, "Failed to open app: ${e.message}")
        }
    }

    private fun sendMessage(phoneNumber: String?, message: String): ActionResult {
        if (phoneNumber == null || message.isEmpty()) {
            return ActionResult(false, "Phone number or message missing")
        }

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("sms:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ActionResult(true, "Opening message to $phoneNumber")
            } else {
                return ActionResult(false, "No messaging app available")
            }
        } catch (e: Exception) {
            return ActionResult(false, "Failed to send message: ${e.message}")
        }
    }

    private fun makeCall(phoneNumber: String?): ActionResult {
        if (phoneNumber == null) {
            return ActionResult(false, "Phone number missing")
        }

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ActionResult(true, "Calling $phoneNumber")
            } else {
                return ActionResult(false, "No phone app available")
            }
        } catch (e: Exception) {
            return ActionResult(false, "Failed to make call: ${e.message}")
        }
    }

    private fun playMusic(): ActionResult {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ActionResult(true, "Opening music player")
            } else {
                // Try generic media intent
                val intent2 = Intent(Intent.ACTION_VIEW).apply {
                    setType("audio/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent2.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent2)
                    return ActionResult(true, "Opening media player")
                }
                return ActionResult(false, "No music app available")
            }
        } catch (e: Exception) {
            return ActionResult(false, "Failed to play music: ${e.message}")
        }
    }

    private fun navigate(destination: String?, data: Map<String, String>): ActionResult {
        if (destination == null) {
            return ActionResult(false, "Destination missing")
        }

        try {
            val query = destination.replace(" ", "+")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                this.data = Uri.parse("geo:0,0?q=$query")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.apps.maps") // Prefer Google Maps
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ActionResult(true, "Navigating to $destination")
            } else {
                // Fallback to generic geo intent
                val intent2 = Intent(Intent.ACTION_VIEW).apply {
                    this.data = Uri.parse("geo:0,0?q=$query")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent2.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent2)
                    return ActionResult(true, "Navigating to $destination")
                }
                return ActionResult(false, "No navigation app available")
            }
        } catch (e: Exception) {
            return ActionResult(false, "Failed to navigate: ${e.message}")
        }
    }

    private fun setAlarm(data: Map<String, String>): ActionResult {
        try {
            val hour = data["hour"]?.toIntOrNull() ?: return ActionResult(false, "Hour missing")
            val minute = data["minute"]?.toIntOrNull() ?: return ActionResult(false, "Minute missing")
            val message = data["message"] ?: "Alarm"

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ActionResult(true, "Setting alarm for $hour:$minute")
            } else {
                return ActionResult(false, "No alarm app available")
            }
        } catch (e: Exception) {
            return ActionResult(false, "Failed to set alarm: ${e.message}")
        }
    }

    private suspend fun getLocation(): ActionResult {
        if (!permissionManager.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return ActionResult(false, "Location permission not granted. Please ask user to allow location access.")
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                ActionResult(true, "Current location: lat=${location.latitude}, lon=${location.longitude}")
            } else {
                ActionResult(false, "Could not determine current location. GPS might be disabled or no fix yet.")
            }
        } catch (e: Exception) {
            ActionResult(false, "Error getting location: ${e.message}")
        }
    }

    private fun getBatteryLevel(): ActionResult {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = level * 100 / scale.toFloat()

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL

            ActionResult(true, "Battery level: ${batteryPct.toInt()}%, Charging: $isCharging")
        } catch (e: Exception) {
            ActionResult(false, "Error getting battery level: ${e.message}")
        }
    }

    private fun getVolumeLevels(): ActionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)

            ActionResult(true, "Volume levels: Media $mediaVolume/$maxMedia, Alarm $alarmVolume/$maxAlarm, Ring $ringVolume/$maxRing")
        } catch (e: Exception) {
            ActionResult(false, "Error getting volume levels: ${e.message}")
        }
    }

    private suspend fun requestPermission(permission: String?): ActionResult {
        if (permission == null) return ActionResult(false, "No permission specified")

        // Map simplified names to Android permissions
        val androidPermission = when (permission.lowercase()) {
            "location" -> Manifest.permission.ACCESS_FINE_LOCATION
            "contacts" -> Manifest.permission.READ_CONTACTS
            "phone" -> Manifest.permission.CALL_PHONE
            else -> permission
        }

        val granted = permissionManager.requestPermission(androidPermission)
        return if (granted) {
            ActionResult(true, "Permission $permission granted")
        } else {
            ActionResult(false, "Permission $permission denied by user")
        }
    }

    private fun executeOtherAction(action: DeviceAction): ActionResult {
        // Generic action execution
        val intentAction = action.data["intent_action"] ?: return ActionResult(false, "No intent action specified")
        
        try {
            val intent = Intent(intentAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Add any extra data
                action.data.forEach { (key, value) ->
                    if (key != "intent_action") {
                        putExtra(key, value)
                    }
                }
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ActionResult(true, "Executed action: $intentAction")
            } else {
                return ActionResult(false, "Action not available: $intentAction")
            }
        } catch (e: Exception) {
            return ActionResult(false, "Failed to execute action: ${e.message}")
        }
    }

    companion object {
        // Common app package names
        val APP_PACKAGES = mapOf(
            "spotify" to "com.spotify.music",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "whatsapp" to "com.whatsapp",
            "messenger" to "com.facebook.orca",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "phone" to "com.android.dialer"
        )
    }
}

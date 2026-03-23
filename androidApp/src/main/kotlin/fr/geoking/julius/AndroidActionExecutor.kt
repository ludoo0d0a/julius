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
import fr.geoking.julius.shared.WeatherLookup

class AndroidActionExecutor(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val weatherLookup: WeatherLookup
) : ActionExecutor {

    override suspend fun executeAction(action: DeviceAction): ActionResult {
        return try {
            when (action.type) {
                ActionType.OPEN_APP -> openApp(action.target)
                ActionType.SEND_MESSAGE -> ActionResult(
                    success = false,
                    message = "SMS sending is temporarily disabled in this build."
                )
                ActionType.PLAY_MUSIC -> playMusic(action.target)
                ActionType.NAVIGATE -> navigate(action.target, action.data)
                ActionType.SET_ALARM -> setAlarm(action.data)
                ActionType.GET_LOCATION -> getLocation()
                ActionType.GET_BATTERY_LEVEL -> getBatteryLevel()
                ActionType.GET_VOLUME_LEVEL -> getVolumeLevels()
                ActionType.REQUEST_PERMISSION -> requestPermission(action.target)
                ActionType.FIND_GAS_STATIONS -> findGasStations()
                ActionType.FIND_ELECTRIC_STATIONS -> findElectricStations()
                ActionType.FIND_PARKING -> findNearby("parking")
                ActionType.FIND_RESTAURANTS -> findNearby("restaurant")
                ActionType.FIND_FASTFOOD -> findNearby("fast food")
                ActionType.FIND_SERVICE_AREA -> findNearby("rest area")
                ActionType.GET_TRAFFIC -> getTraffic()
                ActionType.GET_WEATHER -> weatherLookup.getCurrentWeather(action.target)
                ActionType.PLAY_AUDIOBOOK -> playAudiobook()
                ActionType.CALL_CONTACT -> callContact(action.target)
                ActionType.FIND_HOSPITAL -> findNearby("hospital")
                ActionType.FIND_RADARS -> findNearby("radar")
                ActionType.SHOW_MAP -> showMap()
                ActionType.ROADSIDE_ASSISTANCE -> roadsideAssistance()
                ActionType.EMERGENCY_CALL -> emergencyCall()
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

    // SMS / direct calling are disabled for Play policy compliance.

    private fun playMusic(query: String? = null): ActionResult {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!query.isNullOrBlank()) {
                    putExtra(android.app.SearchManager.QUERY, query)
                }
            }

            // Try Spotify first
            val spotifyPackage = "com.spotify.music"
            try {
                context.packageManager.getPackageInfo(spotifyPackage, 0)
                intent.setPackage(spotifyPackage)
            } catch (e: PackageManager.NameNotFoundException) {
                // Spotify not installed
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ActionResult(true, if (query != null) "Playing $query" else "Opening music player")
            } else {
                // If Spotify failed or not found, try without package
                intent.setPackage(null)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return ActionResult(true, if (query != null) "Playing $query" else "Opening music player")
                }

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
                var addressInfo = ""
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val parts = mutableListOf<String>()
                        for (i in 0..address.maxAddressLineIndex) {
                            parts.add(address.getAddressLine(i))
                        }
                        addressInfo = " Address: ${parts.joinToString(", ")}."
                    }
                } catch (e: Exception) {
                    // Ignore geocoding errors, fallback to coordinates only
                }

                ActionResult(true, "Current location: lat=${location.latitude}, lon=${location.longitude}.$addressInfo")
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

    private fun findGasStations(): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("julius://map/gas_stations")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                `package` = context.packageName
            }
            context.startActivity(intent)
            ActionResult(true, "Opening internal gas station search")
        } catch (e: Exception) {
            // Fallback to external if internal fails
            findNearby("gas station")
        }
    }

    private fun findElectricStations(): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("julius://map/electric_stations")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                `package` = context.packageName
            }
            context.startActivity(intent)
            ActionResult(true, "Opening internal electric station search")
        } catch (e: Exception) {
            // Fallback to external if internal fails
            findNearby("electric charging station")
        }
    }

    private fun showMap(): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("julius://map/current_location")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                `package` = context.packageName
            }
            context.startActivity(intent)
            ActionResult(true, "Opening map at current location")
        } catch (e: Exception) {
            // Fallback to external if internal fails
            findNearby("")
        }
    }

    private fun findNearby(query: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:0,0?q=$query")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.apps.maps")
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ActionResult(true, "Searching for $query nearby")
            } else {
                val intent2 = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("geo:0,0?q=$query")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent2)
                ActionResult(true, "Searching for $query nearby")
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to find $query: ${e.message}")
        }
    }

    private fun getTraffic(): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("google.navigation:q=0,0&layer=t")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.apps.maps")
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ActionResult(true, "Showing traffic layer")
            } else {
                ActionResult(false, "Maps app not available for traffic layer")
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to get traffic: ${e.message}")
        }
    }

    private fun playAudiobook(): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("audio/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ActionResult(true, "Opening audiobook player")
            } else {
                ActionResult(false, "No audiobook player available")
            }
        } catch (e: Exception) {
            ActionResult(false, "Failed to play audiobook: ${e.message}")
        }
    }

    private fun callContact(number: String?): ActionResult {
        if (number == null) return ActionResult(false, "No number or contact specified")
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opening dialer for $number")
        } catch (e: Exception) {
            ActionResult(false, "Failed to call contact: ${e.message}")
        }
    }

    private fun roadsideAssistance(): ActionResult {
        // Broad search or common roadside numbers depending on locale could go here.
        // For now, search for roadside assistance nearby.
        return findNearby("roadside assistance")
    }

    private fun emergencyCall(): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:112")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "Opening dialer for emergency call (112)")
        } catch (e: Exception) {
            ActionResult(false, "Failed to initiate emergency call: ${e.message}")
        }
    }

    private suspend fun requestPermission(permission: String?): ActionResult {
        if (permission == null) return ActionResult(false, "No permission specified")

        // Map simplified names to Android permissions
        val androidPermission = when (permission.lowercase()) {
            "location" -> Manifest.permission.ACCESS_FINE_LOCATION
            "contacts" -> Manifest.permission.READ_CONTACTS
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
        )
    }
}

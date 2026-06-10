package fr.geoking.julius

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import fr.geoking.julius.shared.action.ActionExecutor
import fr.geoking.julius.shared.action.ActionResult
import fr.geoking.julius.shared.action.ActionType
import fr.geoking.julius.shared.action.DeviceAction
import fr.geoking.julius.shared.network.NetworkService
import fr.geoking.julius.shared.network.NetworkType
import fr.geoking.julius.queue.AccountAllocator
import fr.geoking.julius.queue.CodingAgentQueueEngine
import fr.geoking.julius.queue.currentDayEpochUtc
import fr.geoking.julius.queue.enabledAccountsFor
import fr.geoking.julius.queue.queuePolicyFor
import fr.geoking.julius.shared.platform.PermissionManager
import kotlinx.coroutines.flow.first

class AndroidActionExecutor(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val networkService: NetworkService,
    private val julesRepository: fr.geoking.julius.repository.JulesRepository,
    private val featureRepository: fr.geoking.julius.repository.FeatureRepository,
    private val settingsManager: SettingsManager,
    private val queueEngine: CodingAgentQueueEngine,
    private val accountAllocator: AccountAllocator,
    private val accountDailyUsageDao: fr.geoking.julius.persistence.AccountDailyUsageDao,
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
                ActionType.SET_ALARM -> setAlarm(action.data)
                ActionType.GET_BATTERY_LEVEL -> getBatteryLevel()
                ActionType.GET_VOLUME_LEVEL -> getVolumeLevels()
                ActionType.REQUEST_PERMISSION -> requestPermission(action.target)
                ActionType.PLAY_AUDIOBOOK -> playAudiobook()
                ActionType.GET_NETWORK_STATUS -> getNetworkStatus()
                ActionType.CREATE_FEATURE -> createFeature(action.target)
                ActionType.MERGE_PR -> mergePr()
                ActionType.REPLAY_FEATURE -> replayFeature(action.target)
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

    private fun getBatteryLevel(): ActionResult {
        return try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = level * 100 / scale.toFloat()

            val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL

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

    private suspend fun getNetworkStatus(): ActionResult {
        return try {
            val status = networkService.getCurrentStatus()
            val country = status.countryName ?: status.countryCode ?: "Unknown"
            val type = when (status.networkType) {
                NetworkType.WIFI -> "WiFi"
                NetworkType.FIVE_G -> "5G"
                NetworkType.FOUR_G -> "4G"
                NetworkType.THREE_G -> "3G"
                NetworkType.TWO_G -> "2G"
                NetworkType.EDGE -> "Edge"
                NetworkType.GPRS -> "GPRS"
                else -> "Mobile"
            }
            val connected = if (status.isConnected) "Connected" else "Disconnected"
            val roaming = if (status.isRoaming) "roaming" else "home network"

            val quality = when (status.signalLevel) {
                4 -> "excellent"
                3 -> "good"
                2 -> "fair"
                1 -> "poor"
                else -> if (status.isConnected) "unknown quality" else "no signal"
            }

            val countryAlignment = if (status.telephonyCountryCode != null && status.countryCode != null &&
                                      status.telephonyCountryCode != status.countryCode) {
                " although your network is still from ${status.telephonyCountryCode}"
            } else {
                ""
            }

            val msg = if (status.isConnected) {
                "You are connected to the $type network of ${status.operatorName} in $country$countryAlignment. The connection is $quality on your $roaming."
            } else {
                "You are currently disconnected from the network in $country. Status: $quality."
            }

            ActionResult(true, msg)
        } catch (e: Exception) {
            ActionResult(false, "Failed to get network status: ${e.message}")
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

    private suspend fun createFeature(title: String?): ActionResult {
        if (title == null) return ActionResult(false, "No title for the feature")
        val sources = julesRepository.getSourcesCached()
        val source = sources.firstOrNull()?.name ?: ""
        val id = featureRepository.addFeature(title, "", 0, source)
        queueEngine.tick()
        return ActionResult(true, "Feature queued: $title (ID: $id)")
    }

    private suspend fun mergePr(): ActionResult {
        val githubToken = settingsManager.settings.value.githubApiKey
        if (githubToken.isBlank()) return ActionResult(false, "GitHub API key not set")

        val sources = julesRepository.getSourcesCached()
        for (source in sources) {
            val sessions = julesRepository.getSessions(emptyList(), source.name, githubToken).first()
            val sessionWithPr = sessions.find { it.prUrl != null && it.prState == "open" }
            if (sessionWithPr != null) {
                val res = julesRepository.mergePr(githubToken, sessionWithPr.id, sessionWithPr.prUrl!!, deleteBranch = true)
                return if (res.isSuccess) {
                    ActionResult(true, "Successfully merged PR for ${source.name}")
                } else {
                    ActionResult(false, "Failed to merge PR: ${res.exceptionOrNull()?.message}")
                }
            }
        }

        return ActionResult(false, "No open Pull Request found to merge.")
    }

    private suspend fun replayFeature(title: String?): ActionResult {
        if (title == null) return ActionResult(false, "No feature name specified")
        val features = featureRepository.getAllFeatures().first()
        val feature = features.find { it.title.contains(title, ignoreCase = true) }
            ?: return ActionResult(false, "Feature not found: $title")

        val settings = settingsManager.settings.value
        val backend = settings.codingAgentBackend
        val account = accountAllocator.selectAccount(
            backend = backend,
            accounts = settings.agentAccounts,
            policy = settings.queuePolicyFor(backend),
            activeSessions = julesRepository.getAllActiveSessions(),
            dailyUsage = accountDailyUsageDao.getAllForDay(currentDayEpochUtc()),
            dayEpoch = currentDayEpochUtc(),
        ) ?: return ActionResult(false, "No agent account available (daily limit or not configured)")
        featureRepository.replayFeature(feature.id, account)
        queueEngine.tick()
        return ActionResult(true, "Replaying prompts for feature: ${feature.title}")
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

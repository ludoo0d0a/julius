package com.antigravity.voiceai.shared

/**
 * Helper class to parse natural language commands and extract device actions.
 * This enables Droidrun-like functionality where voice commands can trigger device actions.
 */
object ActionParser {
    
    /**
     * Parse a text response from an AI agent and extract any device actions.
     * Looks for action indicators in the text and extracts parameters.
     * Supports both English and French commands.
     */
    fun parseActionFromResponse(text: String): DeviceAction? {
        val lowerText = text.lowercase()
        
        // Check for different action types (English and French)
        when {
            // Open app commands - English: open, launch, start | French: ouvrir, lancer, démarrer
            lowerText.contains("open ") || lowerText.contains("launch ") || 
            lowerText.contains("start ") || lowerText.contains("ouvrir ") || 
            lowerText.contains("lancer ") || lowerText.contains("démarrer ") ||
            lowerText.contains("ouvre ") || lowerText.contains("lance ") -> {
                val appName = extractAppName(lowerText)
                val packageName = appName?.let { resolveAppPackage(it) }
                return DeviceAction(
                    type = ActionType.OPEN_APP,
                    target = packageName ?: appName,
                    data = emptyMap()
                )
            }
            
            // Send message commands - English: send message, text | French: envoyer message, envoyer sms, envoyer texto
            lowerText.contains("send message") || lowerText.contains("text ") || 
            lowerText.contains("envoyer message") || lowerText.contains("envoyer sms") ||
            lowerText.contains("envoyer texto") || lowerText.contains("envoyer un message") ||
            lowerText.contains("envoie message") || lowerText.contains("envoie sms") -> {
                val phoneNumber = extractPhoneNumber(text)
                val message = extractMessage(text)
                return DeviceAction(
                    type = ActionType.SEND_MESSAGE,
                    target = phoneNumber,
                    data = mapOf("message" to (message ?: ""))
                )
            }
            
            // Make call commands - English: call, phone | French: appeler, téléphoner
            lowerText.contains("call ") || lowerText.contains("phone ") || 
            lowerText.contains("appeler ") || lowerText.contains("téléphoner ") ||
            lowerText.contains("appelle ") || lowerText.contains("téléphone ") -> {
                val phoneNumber = extractPhoneNumber(text)
                return DeviceAction(
                    type = ActionType.MAKE_CALL,
                    target = phoneNumber,
                    data = emptyMap()
                )
            }
            
            // Navigate commands - English: navigate to, directions to, go to, drive to
            // French: naviguer vers, aller à, se rendre à, conduire à
            lowerText.contains("navigate to") || lowerText.contains("directions to") || 
            lowerText.contains("go to") || lowerText.contains("drive to") ||
            lowerText.contains("naviguer vers") || lowerText.contains("aller à") ||
            lowerText.contains("se rendre à") || lowerText.contains("conduire à") ||
            lowerText.contains("va à") || lowerText.contains("va vers") ||
            lowerText.contains("itinéraire vers") -> {
                val destination = extractDestination(text)
                return DeviceAction(
                    type = ActionType.NAVIGATE,
                    target = destination,
                    data = emptyMap()
                )
            }
            
            // Play music commands - English: play music, play song | French: jouer musique, lancer musique
            lowerText.contains("play music") || lowerText.contains("play song") ||
            lowerText.contains("jouer musique") || lowerText.contains("lancer musique") ||
            lowerText.contains("jouer de la musique") || lowerText.contains("mets de la musique") ||
            lowerText.contains("mettre musique") -> {
                return DeviceAction(
                    type = ActionType.PLAY_MUSIC,
                    target = null,
                    data = emptyMap()
                )
            }
            
            // Set alarm commands - English: set alarm, alarm for | French: mettre réveil, réveil pour
            lowerText.contains("set alarm") || lowerText.contains("alarm for") ||
            lowerText.contains("mettre réveil") || lowerText.contains("réveil pour") ||
            lowerText.contains("met un réveil") || lowerText.contains("programmer réveil") ||
            lowerText.contains("alarme pour") -> {
                val (hour, minute) = extractTime(text)
                val message = extractAlarmMessage(text)
                return DeviceAction(
                    type = ActionType.SET_ALARM,
                    target = null,
                    data = mapOf(
                        "hour" to hour.toString(),
                        "minute" to minute.toString(),
                        "message" to message
                    )
                )
            }
        }
        
        return null
    }
    
    private fun extractAppName(text: String): String? {
        val patterns = listOf(
            // English patterns
            Regex("open (.+?)(?:\s|$)", RegexOption.IGNORE_CASE),
            Regex("launch (.+?)(?:\s|$)", RegexOption.IGNORE_CASE),
            Regex("start (.+?)(?:\s|$)", RegexOption.IGNORE_CASE),
            // French patterns
            Regex("ouvrir (.+?)(?:\s|$)", RegexOption.IGNORE_CASE),
            Regex("ouvre (.+?)(?:\s|$)", RegexOption.IGNORE_CASE),
            Regex("lancer (.+?)(?:\s|$)", RegexOption.IGNORE_CASE),
            Regex("lance (.+?)(?:\s|$)", RegexOption.IGNORE_CASE),
            Regex("démarrer (.+?)(?:\s|$)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                var appName = match.groupValues[1].trim()
                // Remove common trailing words (English and French)
                appName = appName.removeSuffix(" app")
                    .removeSuffix(" application")
                    .removeSuffix(" application")
                    .removeSuffix(" l'application")
                    .removeSuffix(" l'app")
                    .trim()
                return appName
            }
        }
        return null
    }
    
    private fun extractPhoneNumber(text: String): String? {
        // Match phone number patterns (US, French, and international formats)
        val patterns = listOf(
            // US format: 123-456-7890 or (123) 456-7890
            Regex("\b\d{3}[-.]?\d{3}[-.]?\d{4}\b"),
            Regex("\b\(\d{3}\)\s?\d{3}[-.]?\d{4}\b"),
            Regex("\b\+?1?[-.]?\d{3}[-.]?\d{3}[-.]?\d{4}\b"),
            // French format: 06 12 34 56 78 or 0612345678 or +33 6 12 34 56 78
            Regex("\b(?:\+33|0033|0)[-.\s]?[1-9][-.\s]?\d{2}[-.\s]?\d{2}[-.\s]?\d{2}[-.\s]?\d{2}[-.\s]?\d{2}\b"),
            // Generic 10-digit numbers (French format)
            Regex("\b0[1-9]\d{8}\b"),
            // International format with country code
            Regex("\b\+\d{1,3}[-.\s]?\d{1,4}[-.\s]?\d{1,4}[-.\s]?\d{1,9}\b")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                var phone = match.value.replace(Regex("[^0-9+]"), "")
                // Normalize French numbers: remove leading 0 if it's a French mobile number
                // and convert to international format if needed
                if (phone.startsWith("0") && phone.length == 10) {
                    // Keep as is - Android can handle French format
                }
                return phone
            }
        }
        
        // Try to extract contact name and return as-is (would need contact lookup in real implementation)
        return null
    }
    
    private fun extractMessage(text: String): String? {
        val patterns = listOf(
            // English patterns
            Regex("message (?:to .+? )?(?:saying|that|:)? (.+)", RegexOption.IGNORE_CASE),
            Regex("text (?:to .+? )?(?:saying|that|:)? (.+)", RegexOption.IGNORE_CASE),
            Regex("send (?:a )?message (?:to .+? )?(?:saying|that|:)? (.+)", RegexOption.IGNORE_CASE),
            // French patterns
            Regex("message (?:à .+? )?(?:disant|qui dit|:)? (.+)", RegexOption.IGNORE_CASE),
            Regex("envoyer (?:un )?(?:message|sms|texto) (?:à .+? )?(?:disant|qui dit|:)? (.+)", RegexOption.IGNORE_CASE),
            Regex("envoie (?:un )?(?:message|sms|texto) (?:à .+? )?(?:disant|qui dit|:)? (.+)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }
    
    private fun extractDestination(text: String): String? {
        val patterns = listOf(
            // English patterns
            Regex("(?:navigate to|directions to|go to|drive to) (.+)", RegexOption.IGNORE_CASE),
            // French patterns
            Regex("(?:naviguer vers|aller à|se rendre à|conduire à|va à|va vers|itinéraire vers) (.+)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                var destination = match.groupValues[1].trim()
                // Remove trailing punctuation and action words (English and French)
                destination = destination.removeSuffix(".")
                    .removeSuffix(" please")
                    .removeSuffix(" s'il vous plaît")
                    .removeSuffix(" stp")
                    .removeSuffix(" merci")
                    .trim()
                return destination
            }
        }
        return null
    }
    
    private fun extractTime(text: String): Pair<Int, Int> {
        // Try to extract time like "3:30" or "3 pm" or "15:00" (English)
        // Or "3h30" or "15h00" or "3 heures" (French)
        val timePatterns = listOf(
            // Standard format: 3:30 or 15:30
            Regex("(\d{1,2}):(\d{2})"),
            // French format: 3h30 or 15h00
            Regex("(\d{1,2})h(\d{2})"),
            Regex("(\d{1,2})h(\d{2})"),
            // AM/PM format: 3 pm or 3am
            Regex("(\d{1,2})\s*(am|pm)", RegexOption.IGNORE_CASE),
            // O'clock format: 3 o'clock
            Regex("(\d{1,2}) o'clock", RegexOption.IGNORE_CASE),
            // French heure format: 3 heures or 15 heures
            Regex("(\d{1,2})\s*heures?", RegexOption.IGNORE_CASE),
            // French avec minutes: 3 heures 30
            Regex("(\d{1,2})\s*heures?\s*(\d{1,2})", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in timePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val hourStr = match.groupValues[1]
                val hour = hourStr.toIntOrNull() ?: continue
                
                // Handle AM/PM
                if (pattern.pattern.contains("am|pm", ignoreCase = true)) {
                    val amPm = match.groupValues[2].lowercase()
                    val adjustedHour = if (amPm == "pm" && hour != 12) hour + 12 else if (amPm == "am" && hour == 12) 0 else hour
                    return Pair(adjustedHour, 0)
                }
                // Handle French "heures" with minutes
                else if (pattern.pattern.contains("heures")) {
                    val minuteStr = match.groupValues.getOrNull(2) ?: "0"
                    val minute = minuteStr.toIntOrNull() ?: 0
                    return Pair(hour, minute)
                }
                // Handle standard format (includes French h format)
                else {
                    val minuteStr = match.groupValues.getOrNull(2) ?: "0"
                    val minute = minuteStr.toIntOrNull() ?: 0
                    return Pair(hour, minute)
                }
            }
        }
        
        // Default to 9:00 if not found
        val defaultHour = 9
        val defaultMinute = 0
        return Pair(defaultHour, defaultMinute)
    }
    
    private fun extractAlarmMessage(text: String): String {
        // Try to extract alarm message/name (English and French)
        val messagePatterns = listOf(
            Regex("alarm (?:for|named|called) (.+)", RegexOption.IGNORE_CASE),
            Regex("réveil (?:pour|nommé|appelé) (.+)", RegexOption.IGNORE_CASE),
            Regex("alarme (?:pour|nommée|appelée) (.+)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in messagePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim().removeSuffix(".") ?: "Alarm"
            }
        }
        
        // Default messages in both languages
        return if (text.contains(Regex("réveil|alarme", RegexOption.IGNORE_CASE))) {
            "Réveil"
        } else {
            "Alarm"
        }
    }
    
    private fun resolveAppPackage(appName: String): String? {
        // Common app package mappings (English and French names)
        val appPackages = mapOf(
            // Spotify
            "spotify" to "com.spotify.music",
            // Maps / Cartes
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "cartes" to "com.google.android.apps.maps",
            "google cartes" to "com.google.android.apps.maps",
            // Gmail
            "gmail" to "com.google.android.gm",
            // WhatsApp
            "whatsapp" to "com.whatsapp",
            // Messenger
            "messenger" to "com.facebook.orca",
            "facebook messenger" to "com.facebook.orca",
            // YouTube
            "youtube" to "com.google.android.youtube",
            // Chrome
            "chrome" to "com.android.chrome",
            // Phone / Téléphone
            "phone" to "com.android.dialer",
            "téléphone" to "com.android.dialer",
            "telephone" to "com.android.dialer",
            // Contacts
            "contacts" to "com.android.contacts",
            // Calendar / Calendrier
            "calendar" to "com.google.android.calendar",
            "calendrier" to "com.google.android.calendar",
            // Music / Musique
            "music" to "com.android.music",
            "musique" to "com.android.music",
            // Camera / Appareil photo
            "camera" to "com.android.camera2",
            "appareil photo" to "com.android.camera2",
            // Photos
            "photos" to "com.google.android.apps.photos",
            "photo" to "com.google.android.apps.photos"
        )
        
        val lowerAppName = appName.lowercase()
        return appPackages.entries.find { 
            it.key in lowerAppName || lowerAppName in it.key 
        }?.value
    }
}

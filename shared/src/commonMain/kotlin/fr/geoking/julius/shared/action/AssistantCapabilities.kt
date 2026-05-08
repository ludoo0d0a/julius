package fr.geoking.julius.shared.action

/**
 * Describes what Julius can do on the device (tools, parsed voice actions, map/car utilities).
 * When the user asks for a capability overview, [llmInstructionForCapabilitiesOverview] is merged into the system prompt.
 */
object AssistantCapabilities {

    fun isCapabilitiesHelpQuery(userText: String): Boolean {
        val n = normalizeForIntent(userText)
        if (n.length > 120) return false
        return capabilityPhrases.any { n.contains(it) }
    }

    /**
     * @param languageTag BCP 47 tag (e.g. fr-FR); French if primary subtag is "fr", otherwise English.
     */
    fun capabilitiesSummary(languageTag: String?): String {
        val fr = languageTag?.take(2)?.equals("fr", ignoreCase = true) == true
        return if (fr) summaryFr() else summaryEn()
    }

    /**
     * Appended to the system prompt when the user asks for a capability overview.
     * The model should answer naturally but cover every item in [capabilitiesSummary].
     */
    fun llmInstructionForCapabilitiesOverview(languageTag: String?): String {
        val fr = languageTag?.take(2)?.equals("fr", ignoreCase = true) == true
        val summary = capabilitiesSummary(languageTag)
        return if (fr) {
            "L'utilisateur demande ce que tu peux faire. Réponds de façon naturelle et adaptée à la conduite vocale, " +
                "mais tu dois couvrir toutes les capacités ci-dessous — tu peux reformuler, sans rien omettre ni ajouter " +
                "de fonctionnalités absentes de cette liste :\n\n$summary"
        } else {
            "The user is asking what you can do. Answer in a natural, voice-driving-friendly way, but you must cover " +
                "every capability below — you may rephrase, but do not omit anything and do not claim features not " +
                "listed here:\n\n$summary"
        }
    }

    /** Substring match after [normalizeForIntent]; avoids Java `\\b` issues with accented letters. */
    private val capabilityPhrases: List<String> = listOf(
        "what can you do",
        "what can u do",
        "what are you able to do",
        "what are your capabilities",
        "what can you help me with",
        "what can you help me do",
        "list your features",
        "list your capabilities",
        "list your actions",
        "list your commands",
        "list features",
        "list capabilities",
        "what features do you have",
        "what actions can you",
        "what commands can you",
        "tell me what you can do",
        "what else can you do",
        "how do i use you",
        "what do you do",
        "que peux tu faire",
        "qu est ce que tu peux faire",
        "qu est ce que tu sais faire",
        "tu peux faire quoi",
        "ce que tu peux faire",
        "à quoi tu sers",
        "a quoi tu sers",
        "quelles sont tes capacités",
        "quelles sont tes capacites",
        "liste des actions",
        "liste des fonctions",
        "liste des capacités",
        "liste des capacites",
        "comment tu fonctionnes"
    )

    private fun normalizeForIntent(text: String): String =
        text.lowercase()
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")

    private fun summaryEn(): String = buildString {
        append(
            "I can chat and answer questions with your selected assistant, and run actions on your phone for driving. "
        )
        append("I can run a few on-device actions. ")
        append(
            "Device: battery level, volume levels, approximate location when allowed, open apps by name, play specific music or audiobooks, set an alarm. "
        )
        append("Sending SMS from the assistant is not available in this build. ")
        append(
            "Ask naturally, for example: play music…, set an alarm…, what's my battery level, or open Spotify."
        )
    }

    private fun summaryFr(): String = buildString {
        append(
            "Je peux discuter et répondre à des questions avec l’assistant que vous avez choisi, et lancer des actions sur le téléphone pour la conduite. "
        )
        append("Je peux aussi lancer quelques actions sur l’appareil. ")
        append(
            "Téléphone : niveau de batterie, volumes, position approximative si autorisé, ouvrir des apps par nom, lancer de la musique spécifique ou un livre audio, régler une alarme. "
        )
        append("L’envoi de SMS depuis l’assistant n’est pas disponible dans cette version. ")
        append(
            "Demandez naturellement, par exemple : joue…, règle une alarme…, quel est le niveau de batterie, ou ouvre Spotify."
        )
    }
}

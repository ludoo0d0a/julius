package fr.geoking.julius.shared

object SpeechLanguageResolver {
    private data class LanguageAliases(val tag: String, val aliases: Set<String>)

    private val languageAliases = listOf(
        LanguageAliases("en", setOf("english", "anglais")),
        LanguageAliases("fr", setOf("french", "francais", "fran\u00E7ais")),
        LanguageAliases("es", setOf("spanish", "espanol", "espagnol")),
        LanguageAliases("de", setOf("german", "allemand")),
        LanguageAliases("it", setOf("italian", "italien")),
        LanguageAliases("pt", setOf("portuguese", "portugais")),
        LanguageAliases("ar", setOf("arabic", "arabe")),
        LanguageAliases("ja", setOf("japanese", "japonais")),
        LanguageAliases("ko", setOf("korean", "coreen", "cor\u00E9en")),
        LanguageAliases("zh", setOf("chinese", "mandarin", "chinois")),
        LanguageAliases("ru", setOf("russian", "russe")),
        LanguageAliases("hi", setOf("hindi"))
    )

    private val aliasToTag = languageAliases.flatMap { spec ->
        spec.aliases.map { alias -> alias.lowercase() to spec.tag }
    }.toMap()

    private val explicitLanguageRegex = run {
        val aliasPattern = aliasToTag.keys.joinToString("|") { Regex.escape(it) }
        Regex(
            "\\b(?:talk|speak|respond|reply|answer|switch|parle|parler|reponds|repondez|repondre|r\u00E9ponds|r\u00E9pondez|r\u00E9pondre)\\s+(?:in|to|en)?\\s*(?:l'|le |la |the )?($aliasPattern)\\b",
            RegexOption.IGNORE_CASE
        )
    }

    fun extractPreferredLanguageTag(text: String): String? {
        val match = explicitLanguageRegex.find(text) ?: return null
        val alias = match.groupValues[1].lowercase()
        return aliasToTag[alias]
    }

    fun detectLanguageTag(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        if (containsAnyInRanges(trimmed, hiraganaRanges) || containsAnyInRanges(trimmed, katakanaRanges)) {
            return "ja"
        }
        if (containsAnyInRanges(trimmed, hangulRanges)) return "ko"
        if (containsAnyInRanges(trimmed, arabicRanges)) return "ar"
        if (containsAnyInRanges(trimmed, cyrillicRanges)) return "ru"
        if (containsAnyInRanges(trimmed, devanagariRanges)) return "hi"
        if (containsAnyInRanges(trimmed, cjkRanges)) return "zh"

        val tokens = wordRegex.findAll(trimmed.lowercase())
            .map { it.value }
            .filter { it.length >= 2 }
            .toList()

        if (tokens.size < 3) return null

        val scores = stopwordsByTag.mapValues { (_, stopwords) ->
            tokens.count { it in stopwords }
        }

        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted.firstOrNull() ?: return null
        val second = sorted.drop(1).firstOrNull()

        if (best.value < 2) return null
        if (second != null && best.value == second.value) return null

        return best.key
    }

    private val stopwordsByTag: Map<String, Set<String>> = mapOf(
        "en" to setOf("the", "and", "you", "your", "is", "are", "to", "of", "for", "with", "this", "that"),
        "fr" to setOf("le", "la", "les", "et", "est", "vous", "tu", "je", "de", "des", "pour", "avec", "pas", "une", "un", "ce", "cette"),
        "es" to setOf("el", "la", "los", "las", "y", "es", "de", "para", "con", "que", "una", "un", "por"),
        "de" to setOf("der", "die", "das", "und", "ist", "sind", "nicht", "mit", "fur", "auf", "ein", "eine", "dass", "ich", "du"),
        "it" to setOf("il", "lo", "la", "gli", "le", "di", "che", "per", "con", "una", "un", "non", "io", "tu"),
        "pt" to setOf("para", "com", "que", "uma", "um", "nao", "voce", "tambem", "mais")
    )

    private val wordRegex = Regex("\\p{L}+")

    private val hiraganaRanges = listOf(0x3040..0x309F)
    private val katakanaRanges = listOf(0x30A0..0x30FF, 0x31F0..0x31FF)
    private val hangulRanges = listOf(0xAC00..0xD7AF)
    private val cjkRanges = listOf(0x4E00..0x9FFF)
    private val arabicRanges = listOf(0x0600..0x06FF, 0x0750..0x077F, 0x08A0..0x08FF)
    private val cyrillicRanges = listOf(0x0400..0x04FF, 0x0500..0x052F)
    private val devanagariRanges = listOf(0x0900..0x097F)

    private fun containsAnyInRanges(text: String, ranges: List<IntRange>): Boolean {
        for (ch in text) {
            val code = ch.code
            for (range in ranges) {
                if (code in range) {
                    return true
                }
            }
        }
        return false
    }
}

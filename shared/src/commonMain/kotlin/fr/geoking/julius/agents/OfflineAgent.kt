package fr.geoking.julius.agents

import fr.geoking.julius.shared.ActionParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * OfflineAgent - Fully offline agent.
 * Supports: basic math, counting (EN/FR), hangman, quote of the day.
 * When [extendedActionsEnabled] is true, matching utterances yield [ToolCall]s like cloud agents (same store loop).
 */
class OfflineAgent(
    private val extendedActionsEnabled: Boolean = false,
) : ConversationalAgent {

    private val mutex = Mutex()

    override suspend fun process(input: String): AgentResponse = mutex.withLock {
        val lastUserMessage = extractLastUserMessage(input)
        val normalized = lastUserMessage.trim().lowercase()

        if (extendedActionsEnabled) {
            ActionParser.parseActionFromResponse(lastUserMessage)?.let { action ->
                return AgentResponse(
                    text = LocalExtendedToolSupport.briefAckForExtendedAction(action),
                    toolCalls = listOf(ToolCall(id = LocalExtendedToolSupport.newLocalToolCallId(), action = action)),
                    audio = null,
                )
            }
        }

        // 1. Check for active hangman game first
        HangmanState.current?.let { game ->
            val result = processHangmanGuess(game, normalized)
            if (result != null) return result
        }

        // 2. Quote of the day
        if (isQuoteRequest(normalized)) {
            return AgentResponse(text = getRandomQuote(normalized), audio = null)
        }

        // 3. Hangman start
        if (isHangmanStart(normalized)) {
            return startHangman(normalized)
        }

        // 4. Basic math
        val mathResult = tryComputeMath(normalized)
        if (mathResult != null) {
            return AgentResponse(text = mathResult, audio = null)
        }

        // 5. Counting
        val countResult = tryCounting(normalized)
        if (countResult != null) {
            return AgentResponse(text = countResult, audio = null)
        }

        // Fallback
        val isFrench = normalized.contains(Regex("\\b(oui|non|merci|bonjour|salut|quoi|comment|pourquoi)\\b")) ||
            normalized.contains("français") || normalized.contains("french") || normalized.contains("en français")
        return AgentResponse(
            text = if (isFrench) {
                buildString {
                    append("Je suis un agent hors ligne. Je peux : calculer, compter, pendu, citation du jour.")
                    if (extendedActionsEnabled) {
                        append(" Avec actions étendues : navigation, carte, météo, stations, etc. — utilise les mêmes formulations que pour l'assistant vocal.")
                    }
                }
            } else {
                buildString {
                    append("I'm an offline agent. I can: math, counting, hangman, quote of the day.")
                    if (extendedActionsEnabled) {
                        append(" With extended actions enabled, try navigate, weather, gas station, map, and other driving phrases like the cloud assistant.")
                    }
                }
            },
            audio = null
        )
    }

    private fun extractLastUserMessage(fullPrompt: String): String {
        val userPrefix = "User:"
        val lastUserIdx = fullPrompt.lastIndexOf(userPrefix)
        return if (lastUserIdx >= 0) {
            fullPrompt.substring(lastUserIdx + userPrefix.length).trim()
        } else {
            fullPrompt
        }
    }

    // --- Math ---
    private fun formatTwoDecimals(value: Double): String {
        val scaled = (value * 100).toLong()
        val intPart = scaled / 100
        val frac = kotlin.math.abs(scaled % 100)
        return "$intPart.${frac.toString().padStart(2, '0')}"
    }

    private fun tryComputeMath(input: String): String? {
        val mathRegex = Regex("""(\d+(?:\.\d+)?)\s*(plus|moins|times|fois|multiplied by|multiplié par|divided by|divisé par|\*|/|\+|-)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val match = mathRegex.find(input) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2].lowercase()
        val b = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when (op) {
            "plus", "+" -> a + b
            "moins", "-" -> a - b
            "times", "fois", "*", "multiplied by", "multiplié par" -> a * b
            "divided by", "divisé par", "/" -> if (b != 0.0) a / b else return "Cannot divide by zero."
            else -> return null
        }
        val isInt = result == result.toLong().toDouble()
        val resultStr = if (isInt) result.toLong().toString() else formatTwoDecimals(result)
        val isFrench = input.contains("plus") || input.contains("moins") || input.contains("fois") || input.contains("divisé")
        return if (isFrench) "$a $op $b = $resultStr" else "$a $op $b equals $resultStr"
    }

    // --- Counting ---
    private fun tryCounting(input: String): String? {
        val countEn = Regex("""count\s+(?:(?:to|up\s+to)\s+)?(\d+)(?:\s+in\s+english)?""", RegexOption.IGNORE_CASE)
        val countFr = Regex("""compte\s+(?:jusqu'?à\s+)?(\d+)(?:\s+en\s+français)?""", RegexOption.IGNORE_CASE)
        val countFromToEn = Regex("""count\s+from\s+(\d+)\s+to\s+(\d+)""", RegexOption.IGNORE_CASE)
        val countFromToFr = Regex("""compte\s+de\s+(\d+)\s+à\s+(\d+)""", RegexOption.IGNORE_CASE)

        val (s, e, isFrench) = when {
            countFromToEn.find(input) != null -> {
                val m = countFromToEn.find(input)!!
                Triple(m.groupValues[1].toIntOrNull() ?: return null, m.groupValues[2].toIntOrNull() ?: return null, false)
            }
            countFromToFr.find(input) != null -> {
                val m = countFromToFr.find(input)!!
                Triple(m.groupValues[1].toIntOrNull() ?: return null, m.groupValues[2].toIntOrNull() ?: return null, true)
            }
            countEn.find(input) != null -> {
                val m = countEn.find(input)!!
                Triple(1, m.groupValues[1].toIntOrNull() ?: return null, false)
            }
            countFr.find(input) != null -> {
                val m = countFr.find(input)!!
                Triple(1, m.groupValues[1].toIntOrNull() ?: return null, true)
            }
            else -> return null
        }
        if (s > e || e - s > 50) return null

        val numbers = if (isFrench) {
            (s..e).map { n -> numberToFrench(n) }
        } else {
            (s..e).map { it.toString() }
        }
        return numbers.joinToString(", ")
    }

    private fun numberToFrench(n: Int): String {
        val units = listOf("", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf")
        val teens = listOf("dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf")
        val tens = listOf("", "dix", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante-dix", "quatre-vingt", "quatre-vingt-dix")
        return when {
            n == 0 -> "zéro"
            n in 1..9 -> units[n]
            n in 10..19 -> teens[n - 10]
            n in 20..69 -> {
                val t = n / 10
                val u = n % 10
                if (u == 0) tens[t] else "${tens[t]}-${units[u]}"
            }
            n in 70..79 -> "soixante-${teens[n - 70]}"
            n in 80..99 -> {
                val u = n % 20
                if (u == 0) "quatre-vingts" else "quatre-vingt-${numberToFrench(u)}"
            }
            else -> n.toString()
        }
    }

    // --- Hangman ---
    private object HangmanState {
        var current: HangmanGame? = null
    }

    private data class HangmanGame(
        val word: String,
        val guessed: Set<Char> = emptySet(),
        val wrongCount: Int = 0
    ) {
        val maxWrong = 6
        fun maskedWord(): String = word.map { if (it in guessed) it else '_' }.joinToString(" ")
        fun isWon(): Boolean = word.all { it in guessed }
        fun isLost(): Boolean = wrongCount >= maxWrong
    }

    private val hangmanWords = listOf(
        "apple", "banana", "orange", "grape", "lemon", "melon", "mango", "cherry",
        "house", "table", "chair", "water", "bread", "music", "happy", "sunny",
        "pomme", "maison", "table", "chaise", "eau", "pain", "musique", "soleil"
    )

    private fun isHangmanStart(input: String): Boolean {
        return input.contains("hangman") || input.contains("pendu") ||
            input.contains("play hangman") || input.contains("jouer au pendu") ||
            input.contains("word game") || input.contains("jeu du pendu")
    }

    private fun startHangman(input: String): AgentResponse {
        val word = hangmanWords.random()
        HangmanState.current = HangmanGame(word = word)
        val isFrench = input.contains("pendu") || input.contains("français")
        val masked = HangmanState.current!!.maskedWord()
        return AgentResponse(
            text = if (isFrench) {
                "Le pendu ! Le mot fait ${word.length} lettres : $masked. Devine une lettre."
            } else {
                "Hangman! The word has ${word.length} letters: $masked. Guess a letter."
            },
            audio = null
        )
    }

    private fun processHangmanGuess(game: HangmanGame, input: String): AgentResponse? {
        val letterMatch = Regex("""(?:letter|lettre)\s*([a-zA-Zàâäéèêëïîôùûüÿæœç])""", RegexOption.IGNORE_CASE).find(input)
            ?: Regex("""\b([a-zA-Zàâäéèêëïîôùûüÿæœç])\b""").find(input)
        val letter = letterMatch?.groupValues?.get(1)?.firstOrNull()?.lowercaseChar() ?: return null

        val wordLower = game.word.lowercase()
        val isFrench = input.contains("lettre") || input.contains("français")
        val (newGuessed, newWrong) = if (letter in wordLower) {
            Pair(game.guessed + letter, game.wrongCount)
        } else {
            Pair(game.guessed, game.wrongCount + 1)
        }

        val newGame = HangmanGame(word = game.word, guessed = newGuessed, wrongCount = newWrong)
        HangmanState.current = if (newGame.isWon() || newGame.isLost()) null else newGame

        val masked = newGame.maskedWord()
        return when {
            newGame.isWon() -> AgentResponse(
                text = if (isFrench) "Bravo ! Le mot était « ${game.word} ». Tu as gagné !" else "You won! The word was « ${game.word} ».",
                audio = null
            )
            newGame.isLost() -> AgentResponse(
                text = if (isFrench) "Perdu ! Le mot était « ${game.word} ». Tu peux dire « jouer au pendu » pour recommencer." else "Game over! The word was « ${game.word} ». Say \"play hangman\" to play again.",
                audio = null
            )
            letter in wordLower -> AgentResponse(
                text = if (isFrench) "Oui, $letter ! $masked — ${newGame.wrongCount} erreurs. Encore une lettre ?" else "Yes, $letter! $masked — ${newGame.wrongCount} wrong. Another letter?",
                audio = null
            )
            else -> AgentResponse(
                text = if (isFrench) "Non, pas $letter. $masked — ${newGame.wrongCount} erreurs sur 6. Une autre lettre ?" else "No, not $letter. $masked — ${newGame.wrongCount} wrong out of 6. Another letter?",
                audio = null
            )
        }
    }

    // --- Quote of the day ---
    private fun isQuoteRequest(input: String): Boolean {
        return input.contains("quote of the day") || input.contains("citation du jour") ||
            input.contains("quote du jour") || input.contains("citation of the day") ||
            input.contains("daily quote") || input.contains("citation du jour") ||
            input.matches(Regex(".*(quote|citation).*", RegexOption.IGNORE_CASE))
    }

    private fun getRandomQuote(input: String): String {
        val useFrench = input.contains("français") || input.contains("french") || input.contains("citation") || Random.nextBoolean()
        val quotes = if (useFrench) QUOTES_FR else QUOTES_EN
        val (quote, author) = quotes[Random.nextInt(quotes.size)]
        return "\"$quote\" — $author"
    }

    private val QUOTES_EN = listOf(
        "The only way to do great work is to love what you do." to "Steve Jobs",
        "Innovation distinguishes between a leader and a follower." to "Steve Jobs",
        "Life is what happens when you're busy making other plans." to "John Lennon",
        "The future belongs to those who believe in the beauty of their dreams." to "Eleanor Roosevelt",
        "It is during our darkest moments that we must focus to see the light." to "Aristotle",
        "The only impossible journey is the one you never begin." to "Tony Robbins",
        "In the middle of difficulty lies opportunity." to "Albert Einstein",
        "Be the change that you wish to see in the world." to "Mahatma Gandhi",
        "The best time to plant a tree was 20 years ago. The second best time is now." to "Chinese Proverb",
        "Success is not final, failure is not fatal: it is the courage to continue that counts." to "Winston Churchill",
        "Do what you can, with what you have, where you are." to "Theodore Roosevelt",
        "It does not matter how slowly you go as long as you do not stop." to "Confucius",
        "Everything you've ever wanted is on the other side of fear." to "George Addair",
        "Believe you can and you're halfway there." to "Theodore Roosevelt",
        "The only limit to our realization of tomorrow will be our doubts of today." to "Franklin D. Roosevelt",
        "What lies behind us and what lies before us are tiny matters compared to what lies within us." to "Ralph Waldo Emerson",
        "The journey of a thousand miles begins with a single step." to "Lao Tzu",
        "You miss 100% of the shots you don't take." to "Wayne Gretzky",
        "Whether you think you can or you think you can't, you're right." to "Henry Ford",
        "I have not failed. I've just found 10,000 ways that won't work." to "Thomas Edison",
        "Our greatest glory is not in never falling, but in rising every time we fall." to "Confucius",
        "The only thing we have to fear is fear itself." to "Franklin D. Roosevelt",
        "It is never too late to be what you might have been." to "George Eliot",
        "Try not to become a man of success, but rather try to become a man of value." to "Albert Einstein",
        "The mind is everything. What you think you become." to "Buddha",
        "An unexamined life is not worth living." to "Socrates",
        "Happiness is not something ready made. It comes from your own actions." to "Dalai Lama",
        "The best revenge is massive success." to "Frank Sinatra",
        "Act as if what you do makes a difference. It does." to "William James"
    )

    private val QUOTES_FR = listOf(
        "La vie est ce qui arrive quand on est occupé à faire d'autres projets." to "John Lennon",
        "Le futur appartient à ceux qui croient à la beauté de leurs rêves." to "Eleanor Roosevelt",
        "Soyez le changement que vous voulez voir dans le monde." to "Mahatma Gandhi",
        "Le meilleur moment pour planter un arbre était il y a 20 ans. Le deuxième meilleur moment est maintenant." to "Proverbe chinois",
        "Le succès n'est pas final, l'échec n'est pas fatal : c'est le courage de continuer qui compte." to "Winston Churchill",
        "Faites ce que vous pouvez, avec ce que vous avez, là où vous êtes." to "Theodore Roosevelt",
        "Peu importe la lenteur, tant que vous ne vous arrêtez pas." to "Confucius",
        "Tout ce que vous avez toujours voulu est de l'autre côté de la peur." to "George Addair",
        "Le voyage de mille lieues commence par un premier pas." to "Lao Tzu",
        "Que vous pensiez en être capable ou non, vous avez raison." to "Henry Ford",
        "Je n'ai pas échoué. J'ai trouvé 10 000 façons qui ne marchent pas." to "Thomas Edison",
        "Notre plus grande gloire n'est pas de ne jamais tomber, mais de nous relever à chaque chute." to "Confucius",
        "Il n'est jamais trop tard pour être ce que vous auriez pu être." to "George Eliot",
        "L'esprit est tout. Ce que vous pensez, vous le devenez." to "Bouddha",
        "Une vie sans examen ne vaut pas la peine d'être vécue." to "Socrate",
        "Le bonheur n'est pas quelque chose de tout fait. Il vient de vos propres actions." to "Dalaï Lama",
        "La meilleure revanche, c'est le succès." to "Frank Sinatra",
        "Agissez comme si ce que vous faites faisait une différence. C'est le cas." to "William James",
        "La simplicité est la sophistication suprême." to "Léonard de Vinci",
        "Rien n'est permanent sauf le changement." to "Héraclite",
        "Connais-toi toi-même." to "Socrate",
        "L'imagination est plus importante que le savoir." to "Albert Einstein",
        "La seule façon de faire du bon travail est d'aimer ce que vous faites." to "Steve Jobs",
        "L'innovation distingue un leader d'un suiveur." to "Steve Jobs",
        "Au milieu de la difficulté se trouve l'opportunité." to "Albert Einstein",
        "Croyez que vous pouvez et vous êtes à mi-chemin." to "Theodore Roosevelt",
        "Ce qui est derrière nous et devant nous sont des détails comparés à ce qui est en nous." to "Ralph Waldo Emerson",
        "Vous ratez 100% des tirs que vous ne tentez pas." to "Wayne Gretzky",
        "Notre plus grande gloire n'est pas de ne jamais tomber." to "Confucius",
        "Le bonheur est la seule chose qui se double quand on le partage." to "Albert Schweitzer"
    )
}

package fr.geoking.julius.agents

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class OfflineAgentTests {

    private val agent = OfflineAgent()

    @Test
    fun testMath_Plus() = runBlocking {
        val response = agent.process("5 plus 3")
        assertTrue(response.text.contains("8"), "Expected 8 in: ${response.text}")
        assertTrue(response.text.contains("equals") || response.text.contains("="), "Expected equals: ${response.text}")
    }

    @Test
    fun testMath_Minus() = runBlocking {
        val response = agent.process("10 - 4")
        assertTrue(response.text.contains("6"), "Expected 6 in: ${response.text}")
    }

    @Test
    fun testMath_Times() = runBlocking {
        val response = agent.process("3 times 4")
        assertTrue(response.text.contains("12"), "Expected 12 in: ${response.text}")
    }

    @Test
    fun testMath_French() = runBlocking {
        val response = agent.process("5 plus 3")
        assertTrue(response.text.contains("8"), "Expected 8: ${response.text}")
    }

    @Test
    fun testCount_English() = runBlocking {
        val response = agent.process("count to 5")
        assertTrue(response.text.contains("1, 2, 3, 4, 5") || response.text.contains("1,2,3,4,5"), "Expected count: ${response.text}")
    }

    @Test
    fun testCount_French() = runBlocking {
        val response = agent.process("compte jusqu'à 3")
        assertContains(response.text, "un", ignoreCase = true)
        assertContains(response.text, "deux", ignoreCase = true)
        assertContains(response.text, "trois", ignoreCase = true)
    }

    @Test
    fun testQuoteOfTheDay() = runBlocking {
        val response = agent.process("quote of the day")
        assertTrue(response.text.contains("—"), "Expected quote format with em-dash: ${response.text}")
        assertTrue(response.text.length > 10, "Quote too short: ${response.text}")
    }

    @Test
    fun testHangmanStart() = runBlocking {
        val response = agent.process("play hangman")
        assertTrue(
            response.text.contains("letters") || response.text.contains("lettres"),
            "Expected 'letters' or 'lettres': ${response.text}"
        )
        assertTrue(
            response.text.contains("Guess") || response.text.contains("Devine") || response.text.contains("_"),
            "Expected hangman prompt: ${response.text}"
        )
    }

    @Test
    fun testFallback_English() = runBlocking {
        val response = agent.process("hello random stuff")
        assertTrue(
            response.text.contains("offline") || response.text.contains("math") || response.text.contains("count"),
            "Expected help message: ${response.text}"
        )
    }

    @Test
    fun testFallback_French() = runBlocking {
        val response = agent.process("bonjour")
        assertTrue(
            response.text.contains("hors ligne") || response.text.contains("calculer") || response.text.contains("compter"),
            "Expected French help: ${response.text}"
        )
    }
}

package fr.geoking.julius.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ActionParserTest {

    @Test
    fun testParsePlayMusic() {
        val actions = listOf(
            "play music" to null,
            "play music please" to null,
            "play music." to null,
            "play song" to null,
            "play Bohemian Rhapsody" to "Bohemian Rhapsody",
            "play music Bohemian Rhapsody" to "Bohemian Rhapsody",
            "play song Imagine" to "Imagine",
            "play Imagine by John Lennon" to "Imagine by John Lennon"
        )

        for ((input, expectedQuery) in actions) {
            val action = ActionParser.parseActionFromResponse(input)
            assertNotNull(action, "Failed to parse action for: $input")
            assertEquals(ActionType.PLAY_MUSIC, action.type)
            assertEquals(expectedQuery, action.target, "Incorrect query for: $input")
        }
    }

    @Test
    fun testParseJouerMusique() {
        val actions = listOf(
            "jouer de la musique" to null,
            "mets de la musique" to null,
            "jouer musique" to null,
            "joue Bohemian Rhapsody" to "Bohemian Rhapsody",
            "lancer musique Imagine" to "Imagine",
            "jouer Imagine de John Lennon" to "Imagine de John Lennon",
            "joue du rock" to "du rock"
        )

        for ((input, expectedQuery) in actions) {
            val action = ActionParser.parseActionFromResponse(input)
            assertNotNull(action, "Failed to parse action for: $input")
            assertEquals(ActionType.PLAY_MUSIC, action.type)
            assertEquals(expectedQuery, action.target, "Incorrect query for: $input")
        }
    }

    @Test
    fun testParseNavigate() {
        val action = ActionParser.parseActionFromResponse("navigate to Paris")
        assertNotNull(action)
        assertEquals(ActionType.NAVIGATE, action.type)
        assertEquals("Paris", action.target)
    }

    @Test
    fun testParseUnknown() {
        val action = ActionParser.parseActionFromResponse("hello world")
        assertNull(action)
    }

    @Test
    fun testParseElectricStations() {
        val actions = listOf(
            "find a charging station nearby",
            "find electric station",
            "trouve une borne electrique",
            "je veux recharger"
        )

        for (input in actions) {
            val action = ActionParser.parseActionFromResponse(input)
            assertNotNull(action, "Failed to parse action for: $input")
            assertEquals(ActionType.FIND_ELECTRIC_STATIONS, action.type)
        }
    }

    @Test
    fun testParseHybridStations() {
        val actions = listOf(
            "find hybrid station nearby",
            "find electric and fuel station nearby",
            "find electric and gas station",
            "trouve borne et station essence"
        )

        for (input in actions) {
            val action = ActionParser.parseActionFromResponse(input)
            assertNotNull(action, "Failed to parse action for: $input")
            assertEquals(ActionType.FIND_HYBRID_STATIONS, action.type)
        }
    }
}

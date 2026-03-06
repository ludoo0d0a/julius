package fr.geoking.julius.agents

import fr.geoking.julius.shared.NetworkException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class LocalAgentTests {

    @Test
    fun testLocalAgent_ThrowsWhenDisabled() = runBlocking {
        val agent = LocalAgent()
        try {
            agent.process("test prompt")
            throw AssertionError("Expected NetworkException was not thrown.")
        } catch (e: NetworkException) {
            assertTrue(
                e.message?.contains("disabled", ignoreCase = true) == true ||
                    e.message?.contains("LlamaBridge", ignoreCase = true) == true,
                "Exception should mention disabled or LlamaBridge: ${e.message}"
            )
        }
    }
}

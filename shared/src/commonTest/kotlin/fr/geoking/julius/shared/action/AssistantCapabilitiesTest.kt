package fr.geoking.julius.shared.action

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantCapabilitiesTest {

    @Test
    fun detectsEnglishCapabilityQuestions() {
        assertTrue(AssistantCapabilities.isCapabilitiesHelpQuery("What can you do?"))
        assertTrue(AssistantCapabilities.isCapabilitiesHelpQuery("tell me what you can do"))
        assertTrue(AssistantCapabilities.isCapabilitiesHelpQuery("List your features"))
    }

    @Test
    fun detectsFrenchCapabilityQuestions() {
        assertTrue(AssistantCapabilities.isCapabilitiesHelpQuery("Que peux-tu faire ?"))
        assertTrue(AssistantCapabilities.isCapabilitiesHelpQuery("Qu'est-ce que tu peux faire ?"))
        assertTrue(AssistantCapabilities.isCapabilitiesHelpQuery("À quoi tu sers"))
    }

    @Test
    fun ignoresUnrelatedOrOverlongInput() {
        assertFalse(AssistantCapabilities.isCapabilitiesHelpQuery("Navigate to Paris"))
        assertFalse(AssistantCapabilities.isCapabilitiesHelpQuery("help me find parking"))
        val long = "what can you do ".repeat(20).trim()
        assertFalse(AssistantCapabilities.isCapabilitiesHelpQuery(long))
    }

    @Test
    fun summaryMatchesLanguageTag() {
        assertTrue(AssistantCapabilities.capabilitiesSummary("en-US").contains("Navigation", ignoreCase = true))
        assertTrue(AssistantCapabilities.capabilitiesSummary("fr-FR").contains("Navigation", ignoreCase = true))
        assertTrue(AssistantCapabilities.capabilitiesSummary("fr").contains("météo", ignoreCase = true))
    }

    @Test
    fun llmInjectionEmbedsSummary() {
        val en = AssistantCapabilities.llmInstructionForCapabilitiesOverview("en")
        assertTrue(en.contains("The user is asking", ignoreCase = true))
        assertTrue(en.contains(AssistantCapabilities.capabilitiesSummary("en")))
        val fr = AssistantCapabilities.llmInstructionForCapabilitiesOverview("fr-FR")
        assertTrue(fr.contains("utilisateur", ignoreCase = true))
        assertTrue(fr.contains(AssistantCapabilities.capabilitiesSummary("fr")))
    }
}

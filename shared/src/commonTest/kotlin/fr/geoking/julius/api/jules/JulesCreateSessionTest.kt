package fr.geoking.julius.api.jules

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JulesCreateSessionTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @Test
    fun createSessionRequest_matchesDocumentedCurlBody() {
        val body = JulesClient.CreateSessionRequest(
            prompt = "Create a boba app!",
            sourceContext = JulesClient.SourceContext(
                source = "sources/github/bobalover/boba",
                githubRepoContext = JulesClient.GitHubRepoContext(startingBranch = "main"),
            ),
            title = "Boba App",
            automationMode = JulesClient.AutomationMode.AUTO_CREATE_PR,
        )
        val encoded = json.encodeToString(JulesClient.CreateSessionRequest.serializer(), body)

        assertTrue(encoded.contains("\"prompt\":\"Create a boba app!\""))
        assertTrue(encoded.contains("\"source\":\"sources/github/bobalover/boba\""))
        assertTrue(encoded.contains("\"startingBranch\":\"main\""))
        assertTrue(encoded.contains("\"automationMode\":\"AUTO_CREATE_PR\""))
        assertTrue(encoded.contains("\"title\":\"Boba App\""))
        assertTrue(!encoded.contains("requirePlanApproval"))
    }

    @Test
    fun createSessionRequest_roundTripsThroughDecoder() {
        val original = JulesClient.CreateSessionRequest(
            prompt = "Fix bug",
            sourceContext = JulesClient.SourceContext(
                source = "sources/github/acme/app",
                githubRepoContext = JulesClient.GitHubRepoContext(startingBranch = "develop"),
            ),
            requirePlanApproval = true,
        )
        val encoded = json.encodeToString(JulesClient.CreateSessionRequest.serializer(), original)
        val decoded = json.decodeFromString(JulesClient.CreateSessionRequest.serializer(), encoded)
        assertEquals(original, decoded)
    }
}

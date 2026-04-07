package fr.geoking.julius.api.jules

import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Client for the [Jules API](https://developers.google.com/jules/api) (Google).
 * Lets you list sources (e.g. GitHub repos), create sessions, send messages, and list activities.
 * Auth: pass API key in [apiKey] for each call (use X-Goog-Api-Key header).
 */
class JulesClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://jules.googleapis.com/v1alpha"
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** API key from Jules web app Settings. Required for all requests. */
    private fun requireApiKey(apiKey: String): String {
        if (apiKey.isBlank()) {
            throw NetworkException(null, "Jules API key is required. Get one at jules.google.com Settings.")
        }
        return apiKey
    }

    private fun apiKeyHeader(apiKey: String): String {
        requireApiKey(apiKey)
        return apiKey
    }

    // --- Sources ---

    @Serializable
    data class JulesSource(
        val name: String = "",
        val id: String = "",
        val githubRepo: JulesGitHubRepo? = null
    )

    @Serializable
    data class JulesGitHubRepo(
        val owner: String = "",
        val repo: String = ""
    )

    @Serializable
    data class ListSourcesResponse(
        val sources: List<JulesSource> = emptyList(),
        val nextPageToken: String? = null
    )

    suspend fun listSources(apiKey: String, pageToken: String? = null): ListSourcesResponse {
        val token = apiKeyHeader(apiKey)
        val url = buildString {
            append("$baseUrl/sources")
            if (pageToken != null) append("?pageToken=$pageToken")
        }
        val response = client.get(url) {
            header("X-Goog-Api-Key", token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Jules listSources: $body")
        }
        return json.decodeFromString(ListSourcesResponse.serializer(), body)
    }

    // --- Sessions ---

    @Serializable
    data class SourceContext(
        val source: String,
        val githubRepoContext: GitHubRepoContext? = null
    )

    @Serializable
    data class GitHubRepoContext(
        val startingBranch: String? = null
    )

    @Serializable
    data class CreateSessionRequest(
        val prompt: String,
        val sourceContext: SourceContext,
        val automationMode: String? = null,
        val title: String? = null,
        val requirePlanApproval: Boolean? = null
    )

    @Serializable
    data class JulesSession(
        val name: String = "",
        val id: String = "",
        val title: String = "",
        val sourceContext: SourceContext? = null,
        val prompt: String = "",
        val state: String? = null,
        val outputs: List<JulesOutput>? = null
    )

    @Serializable
    data class JulesOutput(
        val pullRequest: JulesPullRequest? = null
    )

    @Serializable
    data class JulesPullRequest(
        val url: String? = null,
        val title: String? = null,
        val description: String? = null
    )

    suspend fun createSession(
        apiKey: String,
        prompt: String,
        source: String,
        startingBranch: String? = "main",
        title: String? = null,
        automationMode: String? = null,
        requirePlanApproval: Boolean? = null
    ): JulesSession {
        val token = apiKeyHeader(apiKey)
        val body = CreateSessionRequest(
            prompt = prompt,
            sourceContext = SourceContext(
                source = source,
                githubRepoContext = if (startingBranch != null) GitHubRepoContext(startingBranch = startingBranch) else null
            ),
            automationMode = automationMode,
            title = title,
            requirePlanApproval = requirePlanApproval
        )
        val response = client.post("$baseUrl/sessions") {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val responseBody = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "Jules createSession: $responseBody")
        }
        return json.decodeFromString(JulesSession.serializer(), responseBody)
    }

    suspend fun getSession(apiKey: String, sessionId: String): JulesSession {
        val token = apiKeyHeader(apiKey)
        val response = client.get("$baseUrl/sessions/$sessionId") {
            header("X-Goog-Api-Key", token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Jules getSession: $body")
        }
        return json.decodeFromString(JulesSession.serializer(), body)
    }

    @Serializable
    data class ListSessionsResponse(
        val sessions: List<JulesSession>? = null,
        val nextPageToken: String? = null
    )

    suspend fun listSessions(apiKey: String, pageSize: Int = 10, pageToken: String? = null): ListSessionsResponse {
        val token = apiKeyHeader(apiKey)
        val url = buildString {
            append("$baseUrl/sessions?pageSize=$pageSize")
            if (pageToken != null) append("&pageToken=$pageToken")
        }
        val response = client.get(url) {
            header("X-Goog-Api-Key", token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Jules listSessions: $body")
        }
        return json.decodeFromString(ListSessionsResponse.serializer(), body)
    }

    suspend fun deleteSession(apiKey: String, sessionId: String) {
        val token = apiKeyHeader(apiKey)
        val response = client.delete("$baseUrl/sessions/$sessionId") {
            header("X-Goog-Api-Key", token)
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            throw NetworkException(response.status.value, "Jules deleteSession: $body")
        }
    }

    // --- Send message ---

    @Serializable
    data class SendMessageRequest(val prompt: String)

    suspend fun sendMessage(apiKey: String, sessionId: String, prompt: String) {
        val token = apiKeyHeader(apiKey)
        val response = client.post("$baseUrl/sessions/$sessionId:sendMessage") {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(prompt = prompt))
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            throw NetworkException(response.status.value, "Jules sendMessage: $body")
        }
    }

    // --- Approve plan ---

    suspend fun approvePlan(apiKey: String, sessionId: String) {
        val token = apiKeyHeader(apiKey)
        val response = client.post("$baseUrl/sessions/$sessionId:approvePlan") {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(JsonObject(emptyMap()))
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            throw NetworkException(response.status.value, "Jules approvePlan: $body")
        }
    }

    // --- Activities ---

    @Serializable
    data class ListActivitiesResponse(
        val activities: List<JulesActivity> = emptyList(),
        val nextPageToken: String? = null
    )

    @Serializable
    data class JulesActivity(
        val name: String = "",
        val id: String = "",
        val createTime: String = "",
        @SerialName("originator") val originator: String = "",
        val planGenerated: PlanGenerated? = null,
        val planApproved: PlanApproved? = null,
        val progressUpdated: ProgressUpdated? = null,
        val sessionCompleted: JsonObject? = null,
        val messageSent: MessageSent? = null,
        val artifacts: List<JsonObject>? = null
    )

    @Serializable
    data class PlanGenerated(val plan: JulesPlan? = null)

    @Serializable
    data class JulesPlan(
        val id: String? = null,
        val steps: List<JulesPlanStep> = emptyList()
    )

    @Serializable
    data class JulesPlanStep(
        val id: String? = null,
        val title: String = "",
        val index: Int? = null
    )

    @Serializable
    data class PlanApproved(val planId: String? = null)

    @Serializable
    data class ProgressUpdated(
        val title: String = "",
        val description: String? = null
    )

    @Serializable
    data class MessageSent(val prompt: String? = null)

    suspend fun listActivities(apiKey: String, sessionId: String, pageSize: Int = 30, pageToken: String? = null): ListActivitiesResponse {
        val token = apiKeyHeader(apiKey)
        val url = buildString {
            append("$baseUrl/sessions/$sessionId/activities?pageSize=$pageSize")
            if (pageToken != null) append("&pageToken=$pageToken")
        }
        val response = client.get(url) {
            header("X-Goog-Api-Key", token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "Jules listActivities: $body")
        }
        return json.decodeFromString(ListActivitiesResponse.serializer(), body)
    }

    /**
     * Flatten activities into a linear list of chat-like items for UI.
     * User messages and agent progress/plan/messages are turned into [JulesChatItem].
     */
    fun activitiesToChatItems(activities: List<JulesActivity>): List<JulesChatItem> {
        val items = mutableListOf<JulesChatItem>()
        for (a in activities.sortedBy { it.createTime }) {
            when {
                a.originator == "user" -> {
                    when {
                        a.messageSent != null -> items.add(
                            JulesChatItem.UserMessage(a.id, a.createTime, a.messageSent.prompt ?: "")
                        )
                        a.planApproved != null -> items.add(
                            JulesChatItem.AgentMessage(a.id, a.createTime, "Plan approved.")
                        )
                    }
                }
                a.originator == "agent" -> {
                    val text = when {
                        a.planGenerated != null -> {
                            val plan = a.planGenerated.plan
                            if (plan != null && plan.steps.isNotEmpty()) {
                                "**Plan:**\n" + plan.steps.sortedBy { it.index ?: 0 }.joinToString("\n") { "${it.index?.plus(1) ?: ""}. ${it.title}" }
                            } else "Plan generated."
                        }
                        a.progressUpdated != null -> {
                            val t = a.progressUpdated.title
                            val d = a.progressUpdated.description
                            if (d.isNullOrBlank()) t else "$t\n$d"
                        }
                        a.sessionCompleted != null -> "Session completed."
                        else -> null
                    }
                    if (text != null) {
                        items.add(JulesChatItem.AgentMessage(a.id, a.createTime, text))
                    }
                }
            }
        }
        return items
    }
}

@Serializable
sealed class JulesChatItem {
    @Serializable
    data class UserMessage(val id: String, val createTime: String, val text: String) : JulesChatItem()

    @Serializable
    data class AgentMessage(val id: String, val createTime: String, val text: String) : JulesChatItem()
}

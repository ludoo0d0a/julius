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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** API key from Jules web app Settings. Required for all requests. */
    private fun requireApiKey(apiKey: String): String {
        if (apiKey.isBlank()) {
            throw NetworkException(
                httpCode = null,
                message = "Jules API key is required. Get one at jules.google.com Settings.",
                provider = "Jules"
            )
        }
        return apiKey
    }

    private fun apiKeyHeader(apiKey: String): String {
        requireApiKey(apiKey)
        return apiKey
    }

    private fun sessionName(id: String): String = if (id.startsWith("sessions/")) id else "sessions/$id"
    private fun sourceName(id: String): String = if (id.startsWith("sources/")) id else "sources/$id"

    // --- Sources ---

    // Source — https://developers.google.com/jules/api/reference/rest/v1alpha/sources#Source
    @Serializable
    data class JulesSource(
        val name: String = "",
        val id: String = "",
        val githubRepo: JulesGitHubRepo? = null
    )

    // GitHubRepo — https://developers.google.com/jules/api/reference/rest/v1alpha/sources#GitHubRepo
    @Serializable
    data class JulesGitHubRepo(
        val owner: String = "",
        val repo: String = "",
        val isPrivate: Boolean? = null,
        val defaultBranch: JulesGitHubBranch? = null,
        val branches: List<JulesGitHubBranch> = emptyList()
    )

    // GitHubBranch — https://developers.google.com/jules/api/reference/rest/v1alpha/sources#GitHubBranch
    @Serializable
    data class JulesGitHubBranch(
        val displayName: String = ""
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
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules listSources: $body",
                url = url,
                provider = "Jules"
            )
        }
        return json.decodeFromString(ListSourcesResponse.serializer(), body)
    }

    // --- Sessions ---

    /** [AutomationMode](https://developers.google.com/jules/api/reference/rest/v1alpha/sessions#AutomationMode). */
    object AutomationMode {
        const val UNSPECIFIED = "AUTOMATION_MODE_UNSPECIFIED"
        const val AUTO_CREATE_PR = "AUTO_CREATE_PR"
    }

    /** [Session state](https://developers.google.com/jules/api/reference/rest/v1alpha/sessions#State). */
    object SessionState {
        const val UNSPECIFIED = "STATE_UNSPECIFIED"
        const val QUEUED = "QUEUED"
        const val PLANNING = "PLANNING"
        const val AWAITING_PLAN_APPROVAL = "AWAITING_PLAN_APPROVAL"
        const val AWAITING_USER_FEEDBACK = "AWAITING_USER_FEEDBACK"
        const val IN_PROGRESS = "IN_PROGRESS"
        const val PAUSED = "PAUSED"
        const val FAILED = "FAILED"
        const val COMPLETED = "COMPLETED"
    }

    // SourceContext — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions#SourceContext
    @Serializable
    data class SourceContext(
        val source: String,
        val githubRepoContext: GitHubRepoContext? = null
    )

    // GitHubRepoContext — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions#GitHubRepoContext
    @Serializable
    data class GitHubRepoContext(
        val startingBranch: String? = null
    )

    // Request body for sessions.create — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions/create
    @Serializable
    data class CreateSessionRequest(
        val prompt: String,
        val sourceContext: SourceContext,
        val title: String? = null,
        val requirePlanApproval: Boolean? = null,
        val automationMode: String? = null
    )

    // Session — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions#Session
    @Serializable
    data class JulesSession(
        val name: String = "",
        val id: String = "",
        val prompt: String = "",
        val sourceContext: SourceContext? = null,
        val title: String = "",
        // Input-only fields: part of the resource definition but not returned in responses.
        val requirePlanApproval: Boolean? = null,
        val automationMode: String? = null,
        val state: String? = null,
        val url: String? = null,
        val createTime: String = "",
        val updateTime: String = "",
        val outputs: List<JulesOutput>? = null
    )

    // SessionOutput — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions#SessionOutput
    @Serializable
    data class JulesOutput(
        val pullRequest: JulesPullRequest? = null
    )

    // PullRequest — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions#PullRequest
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
        startingBranch: String? = null,
        title: String? = null,
        automationMode: String? = null,
        requirePlanApproval: Boolean? = null
    ): JulesSession {
        val token = apiKeyHeader(apiKey)
        val body = CreateSessionRequest(
            prompt = prompt,
            sourceContext = SourceContext(
                source = sourceName(source),
                // startingBranch is required by the API when using a GitHub source.
                githubRepoContext = GitHubRepoContext(startingBranch = startingBranch ?: "main"),
            ),
            title = title,
            requirePlanApproval = requirePlanApproval,
            automationMode = automationMode,
        )
        val fullUrl = "$baseUrl/sessions"
        val response = client.post(fullUrl) {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val responseBody = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules createSession: $responseBody",
                url = fullUrl,
                provider = "Jules",
                requestBody = json.encodeToString(CreateSessionRequest.serializer(), body)
            )
        }
        return json.decodeFromString(JulesSession.serializer(), responseBody)
    }

    suspend fun getSession(apiKey: String, sessionId: String): JulesSession {
        val token = apiKeyHeader(apiKey)
        val url = "$baseUrl/${sessionName(sessionId)}"
        val response = client.get(url) {
            header("X-Goog-Api-Key", token)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules getSession: $body",
                url = url,
                provider = "Jules"
            )
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
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules listSessions: $body",
                url = url,
                provider = "Jules"
            )
        }
        return json.decodeFromString(ListSessionsResponse.serializer(), body)
    }

    // Note: delete / pause / resume are not part of the public Jules API reference
    // (https://developers.google.com/jules/api/reference/rest). They are kept here because
    // the alpha service still accepts them and the app relies on them.
    suspend fun deleteSession(apiKey: String, sessionId: String) {
        val token = apiKeyHeader(apiKey)
        val url = "$baseUrl/${sessionName(sessionId)}"
        val response = client.delete(url) {
            header("X-Goog-Api-Key", token)
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules deleteSession: $body",
                url = url,
                provider = "Jules"
            )
        }
    }

    suspend fun pauseSession(apiKey: String, sessionId: String) {
        val token = apiKeyHeader(apiKey)
        val url = "$baseUrl/${sessionName(sessionId)}:pause"
        val body = JsonObject(emptyMap())
        val response = client.post(url) {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            val responseBody = response.bodyAsText()
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules pauseSession: $responseBody",
                url = url,
                provider = "Jules",
                requestBody = body.toString()
            )
        }
    }

    suspend fun resumeSession(apiKey: String, sessionId: String) {
        val token = apiKeyHeader(apiKey)
        val url = "$baseUrl/${sessionName(sessionId)}:resume"
        val body = JsonObject(emptyMap())
        val response = client.post(url) {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            val responseBody = response.bodyAsText()
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules resumeSession: $responseBody",
                url = url,
                provider = "Jules",
                requestBody = body.toString()
            )
        }
    }

    // --- Send message ---

    @Serializable
    data class SendMessageRequest(val prompt: String)

    suspend fun sendMessage(apiKey: String, sessionId: String, prompt: String) {
        val token = apiKeyHeader(apiKey)
        val url = "$baseUrl/${sessionName(sessionId)}:sendMessage"
        val body = SendMessageRequest(prompt = prompt)
        val response = client.post(url) {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            val responseBody = response.bodyAsText()
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules sendMessage: $responseBody",
                url = url,
                provider = "Jules",
                requestBody = json.encodeToString(SendMessageRequest.serializer(), body)
            )
        }
    }

    // --- Approve plan ---

    suspend fun approvePlan(apiKey: String, sessionId: String) {
        val token = apiKeyHeader(apiKey)
        val url = "$baseUrl/${sessionName(sessionId)}:approvePlan"
        val body = JsonObject(emptyMap())
        val response = client.post(url) {
            header("X-Goog-Api-Key", token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            val responseBody = response.bodyAsText()
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules approvePlan: $responseBody",
                url = url,
                provider = "Jules",
                requestBody = body.toString()
            )
        }
    }

    // --- Activities ---

    @Serializable
    data class ListActivitiesResponse(
        val activities: List<JulesActivity> = emptyList(),
        val nextPageToken: String? = null
    )

    // Activity — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions.activities#Activity
    @Serializable
    data class JulesActivity(
        val name: String = "",
        val id: String = "",
        val description: String? = null,
        val createTime: String = "",
        @SerialName("originator") val originator: String = "",
        val artifacts: List<JulesArtifact>? = null,
        // Union field `activity`.
        val agentMessaged: AgentMessaged? = null,
        val userMessaged: UserMessaged? = null,
        val planGenerated: PlanGenerated? = null,
        val planApproved: PlanApproved? = null,
        val progressUpdated: ProgressUpdated? = null,
        val sessionCompleted: SessionCompleted? = null,
        val sessionFailed: SessionFailed? = null
    )

    @Serializable
    data class JulesArtifact(
        val bashOutput: BashOutput? = null,
        val changeSet: ChangeSet? = null,
        val media: Media? = null
    )

    @Serializable
    data class BashOutput(
        val command: String = "",
        val output: String = "",
        val exitCode: Int? = null
    )

    @Serializable
    data class ChangeSet(
        val source: String = "",
        val gitPatch: GitPatch? = null
    )

    @Serializable
    data class GitPatch(
        val unidiffPatch: String? = null,
        val baseCommitId: String? = null,
        val suggestedCommitMessage: String? = null
    )

    @Serializable
    data class Media(
        val data: String = "", // base64
        val mimeType: String = ""
    )

    @Serializable
    data class PlanGenerated(val plan: JulesPlan? = null)

    @Serializable
    data class JulesPlan(
        val id: String? = null,
        val createTime: String = "",
        val steps: List<JulesPlanStep> = emptyList()
    )

    @Serializable
    data class JulesPlanStep(
        val id: String? = null,
        val title: String = "",
        val description: String? = null,
        val index: Int? = null
    )

    @Serializable
    data class PlanApproved(val planId: String? = null)

    @Serializable
    data class ProgressUpdated(
        val title: String = "",
        val description: String? = null
    )

    // SessionCompleted — https://developers.google.com/jules/api/reference/rest/v1alpha/sessions.activities#SessionCompleted
    // Documented as having no fields.
    @Serializable
    class SessionCompleted

    @Serializable
    data class SessionFailed(val reason: String? = null)

    @Serializable
    data class UserMessaged(val userMessage: String)

    @Serializable
    data class AgentMessaged(val agentMessage: String)

    suspend fun listActivities(apiKey: String, sessionId: String, pageSize: Int = 30, pageToken: String? = null): ListActivitiesResponse {
        val token = apiKeyHeader(apiKey)
        val url = buildString {
            append("$baseUrl/${sessionName(sessionId)}/activities?pageSize=$pageSize")
            if (pageToken != null) append("&pageToken=$pageToken")
        }
        val response = client.get(url) {
            header("X-Goog-Api-Key", token)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw NetworkException(
                httpCode = response.status.value,
                message = "Jules listActivities: $body",
                url = url,
                provider = "Jules"
            )
        }
        return json.decodeFromString(ListActivitiesResponse.serializer(), body)
    }

    /**
     * Extract display text from an activity, including special handling for "Updated" actions.
     */
    fun extractText(a: JulesActivity): String {
        val baseText = when {
            a.userMessaged != null -> a.userMessaged.userMessage
            a.agentMessaged != null -> a.agentMessaged.agentMessage
            a.planGenerated != null -> {
                val plan = a.planGenerated.plan
                if (plan != null && plan.steps.isNotEmpty()) {
                    "**Plan:**\n" + plan.steps.sortedBy { it.index ?: 0 }.joinToString("\n") { "${it.index?.plus(1) ?: ""}. ${it.title}" }
                } else "Plan generated."
            }
            a.progressUpdated != null -> {
                val t = a.progressUpdated.title
                val d = a.progressUpdated.description
                if (t == "Updated") {
                    val filenames = a.artifacts?.mapNotNull { it.changeSet?.source }?.distinct() ?: emptyList()
                    if (filenames.isNotEmpty()) "Updated ${filenames.joinToString(", ")}" else t
                } else {
                    if (d.isNullOrBlank()) t else "$t\n$d"
                }
            }
            a.sessionCompleted != null -> "Session completed."
            a.sessionFailed != null -> "Session failed: ${a.sessionFailed.reason ?: "Unknown error"}"
            a.planApproved != null && a.originator == "user" -> "Plan approved. 🚀"
            else -> a.description ?: "Activity"
        }

        // Per the API, git patches surface via Artifact.changeSet.gitPatch (not session outputs).
        val commitMsg = a.artifacts?.firstNotNullOfOrNull { it.changeSet?.gitPatch?.suggestedCommitMessage }
        return when {
            !commitMsg.isNullOrBlank() && !baseText.contains(commitMsg) ->
                "$baseText\n\nPatch prêt — $commitMsg"
            else -> baseText
        }
    }

    /**
     * Flatten activities into a linear list of chat-like items for UI.
     * Consecutive agent activities are grouped into chunks, split by main sections (headers).
     */
    fun activitiesToChatItems(activities: List<JulesActivity>): List<JulesChatItem> {
        val sorted = activities.sortedBy { it.createTime }
        val items = mutableListOf<JulesChatItem>()

        var currentAgentGroup: JulesChatItem.AgentMessage? = null
        var planApprovedShown = false

        fun flush() {
            currentAgentGroup?.let { group ->
                // Ensure text is populated for TTS (concatenation of sub-items)
                val text = if (group.subItems.size > 1) {
                    group.subItems.joinToString("\n") { it.text }
                } else {
                    group.subItems.firstOrNull()?.text ?: group.title
                }

                // If a generic 'Progress' group contains only one item, its text is promoted to the group title
                val title = if (group.title == "Progress" && group.subItems.size == 1) {
                    group.subItems.first().text
                } else {
                    group.title
                }

                items.add(group.copy(title = title, text = text))
            }
            currentAgentGroup = null
        }

        for (a in sorted) {
            // Deduplicate Plan Approved (often appears multiple times in activities)
            if (a.planApproved != null) {
                if (planApprovedShown) continue
                planApprovedShown = true

                flush()
                items.add(
                    JulesChatItem.UserMessage(
                        id = a.id,
                        createTime = a.createTime,
                        text = "Plan approved. 🚀"
                    )
                )
                continue
            }

            if (a.originator == "user") {
                flush()
                val text = when {
                    a.userMessaged != null -> a.userMessaged.userMessage
                    else -> null
                }
                if (text != null) {
                    items.add(JulesChatItem.UserMessage(a.id, a.createTime, text))
                }
                continue
            }

            // Agent activity
            val text = extractText(a)
            val type = when {
                a.planGenerated != null -> "plan"
                a.progressUpdated != null -> "progress"
                a.sessionCompleted != null -> "completion"
                else -> "other"
            }

            // Parse text to detect main sections for chunking
            val blocks = parseJulesMessage(text)
            val hasMainSection = blocks.any { it is MessageBlock.Header || it is MessageBlock.SectionHeader }

            val subItem = JulesChatItem.AgentSubItem(a.id, a.createTime, text, type)

            // Start a new chunk if we hit a main section or if it's a major non-progress event
            if (hasMainSection || type != "progress") {
                flush()
                currentAgentGroup = JulesChatItem.AgentMessage(
                    id = a.id,
                    createTime = a.createTime,
                    title = if (type == "progress") "Progress" else text,
                    subItems = listOf(subItem)
                )
                // If it's not progress, flush it immediately so the next activity doesn't merge into it unless it's progress
                if (type != "progress") flush()
            } else {
                // Try to group into current progress chunk
                if (currentAgentGroup != null && currentAgentGroup!!.title == "Progress") {
                    currentAgentGroup = currentAgentGroup!!.copy(
                        subItems = currentAgentGroup!!.subItems + subItem
                    )
                } else {
                    flush()
                    currentAgentGroup = JulesChatItem.AgentMessage(
                        id = a.id,
                        createTime = a.createTime,
                        title = "Progress",
                        subItems = listOf(subItem)
                    )
                }
            }
        }
        flush()
        return items
    }
}

@Serializable
sealed class JulesChatItem {
    abstract val id: String
    abstract val createTime: String

    @Serializable
    data class UserMessage(
        override val id: String,
        override val createTime: String,
        val text: String
    ) : JulesChatItem()

    @Serializable
    data class AgentMessage(
        override val id: String,
        override val createTime: String,
        val title: String,
        val subItems: List<AgentSubItem> = emptyList(),
        val text: String = ""
    ) : JulesChatItem()

    @Serializable
    data class AgentSubItem(
        val id: String,
        val createTime: String,
        val text: String,
        val type: String? = null
    )
}

/** First pull request from session outputs (getSession / listSessions). */
fun JulesClient.JulesSession.primaryPullRequest(): JulesClient.JulesPullRequest? =
    outputs?.firstNotNullOfOrNull { it.pullRequest }

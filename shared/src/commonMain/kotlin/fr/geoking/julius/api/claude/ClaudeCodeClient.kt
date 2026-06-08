package fr.geoking.julius.api.claude

import fr.geoking.julius.api.jules.JulesChatItem
import fr.geoking.julius.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Client for Anthropic [Claude Managed Agents](https://platform.claude.com/docs/en/managed-agents/overview).
 * Remote cloud agent: mounts a GitHub repo, processes prompts, commits and pushes to branches/PRs.
 */
class ClaudeCodeClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.anthropic.com"
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val BETA_HEADER = "managed-agents-2026-04-01"
        private const val API_VERSION = "2023-06-01"
        private val PR_URL_REGEX = Regex("https://github\\.com/[^/\\s]+/[^/\\s]+/pull/\\d+")
        private val BRANCH_URL_REGEX = Regex("https://github\\.com/[^/\\s]+/[^/\\s]+/tree/[^\\s]+")
    }

    private fun requireApiKey(apiKey: String): String {
        if (apiKey.isBlank()) {
            throw NetworkException(
                httpCode = null,
                message = "Anthropic API key is required. Get one at platform.claude.com.",
                provider = "Claude Code"
            )
        }
        return apiKey
    }

    private fun HttpRequestBuilder.applyAnthropicHeaders(apiKey: String) {
        header("x-api-key", requireApiKey(apiKey))
        header("anthropic-version", API_VERSION)
        header("anthropic-beta", BETA_HEADER)
    }

    // --- Agent & environment bootstrap ---

    @Serializable
    data class ClaudeAgent(
        val id: String = "",
        val name: String = "",
        val version: Int? = null
    )

    @Serializable
    data class ClaudeEnvironment(
        val id: String = "",
        val name: String = ""
    )

    suspend fun createAgent(apiKey: String, name: String = "Julius Coding Agent"): ClaudeAgent {
        val url = "$baseUrl/v1/agents"
        val body = buildJsonObject {
            put("name", name)
            put("model", "claude-opus-4-8")
            put(
                "system",
                "You are a skilled software engineer working in a GitHub repository. " +
                    "Implement requested changes, run relevant checks when appropriate, commit with clear messages, " +
                    "push to a feature branch, and open a pull request when the work is ready. " +
                    "Summarize what you changed when finished."
            )
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        put("type", "agent_toolset_20260401")
                        putJsonObject("default_config") { put("enabled", true) }
                    }
                )
            }
        }
        val response = client.post(url) {
            applyAnthropicHeaders(apiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return decodeOrThrow(response.bodyAsText(), response.status.value, url, ClaudeAgent.serializer())
    }

    suspend fun createEnvironment(apiKey: String, name: String): ClaudeEnvironment {
        val url = "$baseUrl/v1/environments"
        val body = buildJsonObject {
            put("name", name)
            putJsonObject("config") {
                put("type", "cloud")
                putJsonObject("networking") { put("type", "unrestricted") }
            }
        }
        val response = client.post(url) {
            applyAnthropicHeaders(apiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return decodeOrThrow(response.bodyAsText(), response.status.value, url, ClaudeEnvironment.serializer())
    }

    // --- Sessions ---

    @Serializable
    data class ClaudeSession(
        val id: String = "",
        val title: String = "",
        val status: String = "",
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("updated_at") val updatedAt: String = ""
    )

    @Serializable
    data class ListSessionsResponse(
        val data: List<ClaudeSession> = emptyList(),
        @SerialName("has_more") val hasMore: Boolean = false,
        @SerialName("last_id") val lastId: String? = null
    )

    suspend fun createSession(
        apiKey: String,
        agentId: String,
        agentVersion: Int?,
        environmentId: String,
        prompt: String,
        owner: String,
        repo: String,
        githubToken: String,
        title: String? = null,
        startingBranch: String = "main"
    ): ClaudeSession {
        val url = "$baseUrl/v1/sessions"
        val agentRef: JsonElement = if (agentVersion != null) {
            buildJsonObject {
                put("type", "agent")
                put("id", agentId)
                put("version", agentVersion)
            }
        } else {
            JsonPrimitive(agentId)
        }
        val body = buildJsonObject {
            put("agent", agentRef)
            put("environment_id", environmentId)
            if (!title.isNullOrBlank()) put("title", title)
            putJsonArray("resources") {
                add(
                    buildJsonObject {
                        put("type", "github_repository")
                        put("url", "https://github.com/$owner/$repo")
                        put("authorization_token", githubToken)
                        putJsonObject("checkout") {
                            put("type", "branch")
                            put("name", startingBranch)
                        }
                    }
                )
            }
        }
        val response = client.post(url) {
            applyAnthropicHeaders(apiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val session = decodeOrThrow(response.bodyAsText(), response.status.value, url, ClaudeSession.serializer())
        if (prompt.isNotBlank()) {
            sendMessage(apiKey, session.id, prompt)
        }
        return session
    }

    suspend fun getSession(apiKey: String, sessionId: String): ClaudeSession {
        val url = "$baseUrl/v1/sessions/$sessionId"
        val response = client.get(url) { applyAnthropicHeaders(apiKey) }
        return decodeOrThrow(response.bodyAsText(), response.status.value, url, ClaudeSession.serializer())
    }

    suspend fun listSessions(apiKey: String, limit: Int = 50): ListSessionsResponse {
        val url = "$baseUrl/v1/sessions?limit=$limit"
        val response = client.get(url) { applyAnthropicHeaders(apiKey) }
        return decodeOrThrow(response.bodyAsText(), response.status.value, url, ListSessionsResponse.serializer())
    }

    suspend fun deleteSession(apiKey: String, sessionId: String) {
        val url = "$baseUrl/v1/sessions/$sessionId"
        val response = client.delete(url) { applyAnthropicHeaders(apiKey) }
        if (response.status.value !in 200..299) {
            throw networkError(response.status.value, response.bodyAsText(), url)
        }
    }

    suspend fun archiveSession(apiKey: String, sessionId: String) {
        val url = "$baseUrl/v1/sessions/$sessionId/archive"
        val response = client.post(url) {
            applyAnthropicHeaders(apiKey)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        if (response.status.value !in 200..299) {
            throw networkError(response.status.value, response.bodyAsText(), url)
        }
    }

    // --- Events ---

    @Serializable
    data class ClaudeEvent(
        val id: String = "",
        val type: String = "",
        @SerialName("processed_at") val processedAt: String? = null,
        val content: JsonArray? = null,
        val name: String? = null,
        val input: JsonObject? = null
    )

    @Serializable
    data class ListEventsResponse(
        val data: List<ClaudeEvent> = emptyList(),
        @SerialName("has_more") val hasMore: Boolean = false,
        @SerialName("last_id") val lastId: String? = null
    )

    suspend fun sendMessage(apiKey: String, sessionId: String, prompt: String) {
        val url = "$baseUrl/v1/sessions/$sessionId/events"
        val body = buildJsonObject {
            putJsonArray("events") {
                add(
                    buildJsonObject {
                        put("type", "user.message")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", prompt)
                                }
                            )
                        }
                    }
                )
            }
        }
        val response = client.post(url) {
            applyAnthropicHeaders(apiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (response.status.value !in 200..299) {
            throw networkError(response.status.value, response.bodyAsText(), url)
        }
    }

    suspend fun listEvents(apiKey: String, sessionId: String, limit: Int = 1000): ListEventsResponse {
        val url = "$baseUrl/v1/sessions/$sessionId/events?limit=$limit"
        val response = client.get(url) { applyAnthropicHeaders(apiKey) }
        return decodeOrThrow(response.bodyAsText(), response.status.value, url, ListEventsResponse.serializer())
    }

    fun mapSessionState(status: String?): String = when (status?.lowercase()) {
        "running" -> "IN_PROGRESS"
        "idle" -> "IDLE"
        "terminated" -> "COMPLETED"
        "rescheduled" -> "QUEUED"
        else -> status?.uppercase() ?: "UNKNOWN"
    }

    fun sessionUrl(sessionId: String): String = "https://platform.claude.com/sessions/$sessionId"

    fun extractText(event: ClaudeEvent): String = when {
        event.type == "user.message" -> extractContentText(event.content)
        event.type == "agent.message" -> extractContentText(event.content)
        event.type == "agent.thinking" -> extractContentText(event.content).let { if (it.isBlank()) "" else "Thinking: $it" }
        event.type == "agent.tool_use" -> "Using tool: ${event.name ?: "tool"}"
        event.type == "agent.mcp_tool_use" -> "GitHub: ${event.name ?: "tool"}"
        event.type == "session.status_running" -> "Working..."
        event.type == "session.status_idle" -> "Waiting for input."
        event.type == "session.status_terminated" -> "Session completed."
        event.type == "session.error" -> "Session error."
        else -> ""
    }.trim()

    fun eventsToChatItems(events: List<ClaudeEvent>): List<JulesChatItem> {
        val sorted = events
            .filter { it.processedAt != null || it.type.startsWith("user.") || it.type.startsWith("agent.") || it.type.startsWith("session.status") }
            .sortedBy { it.processedAt ?: "" }
        val items = mutableListOf<JulesChatItem>()
        var currentAgentGroup: JulesChatItem.AgentMessage? = null

        fun flush() {
            currentAgentGroup?.let { group ->
                val text = if (group.subItems.size > 1) {
                    group.subItems.joinToString("\n") { it.text }
                } else {
                    group.subItems.firstOrNull()?.text ?: group.title
                }
                val title = if (group.title == "Progress" && group.subItems.size == 1) {
                    group.subItems.first().text
                } else {
                    group.title
                }
                items.add(group.copy(title = title, text = text))
            }
            currentAgentGroup = null
        }

        for (event in sorted) {
            val text = extractText(event)
            if (text.isBlank() && event.type !in listOf("session.status_terminated")) continue
            val timestamp = event.processedAt ?: ""

            when {
                event.type == "user.message" -> {
                    flush()
                    items.add(JulesChatItem.UserMessage(event.id, timestamp, text))
                }
                event.type in progressTypes -> {
                    val subItem = JulesChatItem.AgentSubItem(event.id, timestamp, text, "progress")
                    if (currentAgentGroup?.title == "Progress") {
                        currentAgentGroup = currentAgentGroup!!.copy(
                            subItems = currentAgentGroup!!.subItems + subItem
                        )
                    } else {
                        flush()
                        currentAgentGroup = JulesChatItem.AgentMessage(
                            id = event.id,
                            createTime = timestamp,
                            title = "Progress",
                            subItems = listOf(subItem)
                        )
                    }
                }
                else -> {
                    flush()
                    val enriched = enrichWithGitHubUrls(text, events)
                    items.add(
                        JulesChatItem.AgentMessage(
                            id = event.id,
                            createTime = timestamp,
                            title = enriched,
                            subItems = listOf(JulesChatItem.AgentSubItem(event.id, timestamp, enriched, "agent_message")),
                            text = enriched
                        )
                    )
                }
            }
        }
        flush()
        return items
    }

    fun findPullRequestUrl(events: List<ClaudeEvent>): String? {
        for (event in events.asReversed()) {
            val text = extractText(event)
            PR_URL_REGEX.find(text)?.value?.let { return it }
        }
        return null
    }

    fun findBranchUrl(owner: String, repo: String, events: List<ClaudeEvent>): String? {
        for (event in events.asReversed()) {
            val text = extractText(event)
            BRANCH_URL_REGEX.find(text)?.value?.let { return it }
        }
        return null
    }

    private val progressTypes = setOf(
        "agent.tool_use",
        "agent.mcp_tool_use",
        "agent.tool_result",
        "agent.mcp_tool_result",
        "session.status_running"
    )

    private fun enrichWithGitHubUrls(text: String, allEvents: List<ClaudeEvent>): String {
        val pr = PR_URL_REGEX.find(text)?.value
        if (pr != null && !text.contains(pr)) return "$text\n\n$pr"
        return text
    }

    private fun extractContentText(content: JsonArray?): String {
        if (content == null) return ""
        return content.mapNotNull { element ->
            val obj = element.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> obj["text"]?.jsonPrimitive?.content
                else -> null
            }
        }.joinToString("\n")
    }

    private fun <T> decodeOrThrow(
        body: String,
        status: Int,
        url: String,
        serializer: kotlinx.serialization.KSerializer<T>
    ): T {
        if (status !in 200..299) throw networkError(status, body, url)
        return json.decodeFromString(serializer, body)
    }

    private fun networkError(httpCode: Int, body: String, url: String) = NetworkException(
        httpCode = httpCode,
        message = "Claude Code: $body",
        url = url,
        provider = "Claude Code"
    )
}

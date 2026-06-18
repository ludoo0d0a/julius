package fr.geoking.julius.api.claude

import fr.geoking.julius.api.jules.JulesChatItem
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
 * Client for Anthropic [Claude Managed Agents](https://platform.claude.com/docs/en/managed-agents/overview)
 * (beta: `managed-agents-2026-04-01`).
 */
class ClaudeCodeClient(
    private val client: HttpClient,
    private val baseUrl: String = ClaudeHttp.BASE_URL
) {
    private val json = ClaudeHttp.json

    companion object {
        private val PR_URL_REGEX = Regex("https://github\\.com/[^/\\s]+/[^/\\s]+/pull/\\d+")
        private val BRANCH_URL_REGEX = Regex("https://github\\.com/[^/\\s]+/[^/\\s]+/tree/[^\\s]+")
    }

    /** Documented session status values. */
    object SessionStatus {
        const val RESCHEDULING = "rescheduling"
        const val RUNNING = "running"
        const val IDLE = "idle"
        const val TERMINATED = "terminated"
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
        val bodyStr = body.toString()
        val response = client.post(url) {
            applyManagedAgentsHeaders(apiKey)
            setBody(bodyStr)
        }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ClaudeAgent.serializer(),
            "Claude Code",
            requestBody = bodyStr
        )
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
        val bodyStr = body.toString()
        val response = client.post(url) {
            applyManagedAgentsHeaders(apiKey)
            setBody(bodyStr)
        }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ClaudeEnvironment.serializer(),
            "Claude Code",
            requestBody = bodyStr
        )
    }

    // --- Sessions ---

    @Serializable
    data class ClaudeSession(
        val id: String = "",
        val title: String = "",
        val status: String = "",
        val type: String = "",
        @SerialName("created_at") val createdAt: String = "",
        @SerialName("updated_at") val updatedAt: String = ""
    )

    @Serializable
    data class ListSessionsResponse(
        val data: List<ClaudeSession> = emptyList(),
        @SerialName("next_page") val nextPage: String? = null
    )

    @Serializable
    data class DeletedSession(
        val id: String = "",
        val type: String = ""
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
        val bodyStr = body.toString()
        val response = client.post(url) {
            applyManagedAgentsHeaders(apiKey)
            setBody(bodyStr)
        }
        val session = ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ClaudeSession.serializer(),
            "Claude Code",
            requestBody = bodyStr
        )
        if (prompt.isNotBlank()) {
            sendMessage(apiKey, session.id, prompt)
        }
        return session
    }

    suspend fun getSession(apiKey: String, sessionId: String): ClaudeSession {
        val url = "$baseUrl/v1/sessions/$sessionId"
        val response = client.get(url) { applyManagedAgentsHeaders(apiKey) }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ClaudeSession.serializer(),
            "Claude Code"
        )
    }

    suspend fun listSessions(
        apiKey: String,
        limit: Int? = 50,
        page: String? = null
    ): ListSessionsResponse {
        val url = buildString {
            append("$baseUrl/v1/sessions")
            val params = buildList {
                if (limit != null) add("limit=$limit")
                if (page != null) add("page=$page")
            }
            if (params.isNotEmpty()) append("?${params.joinToString("&")}")
        }
        val response = client.get(url) { applyManagedAgentsHeaders(apiKey) }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ListSessionsResponse.serializer(),
            "Claude Code"
        )
    }

    suspend fun deleteSession(apiKey: String, sessionId: String): DeletedSession {
        val url = "$baseUrl/v1/sessions/$sessionId"
        val response = client.delete(url) { applyManagedAgentsHeaders(apiKey) }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            DeletedSession.serializer(),
            "Claude Code"
        )
    }

    suspend fun archiveSession(apiKey: String, sessionId: String): ClaudeSession {
        val url = "$baseUrl/v1/sessions/$sessionId/archive"
        val response = client.post(url) { applyManagedAgentsHeaders(apiKey) }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ClaudeSession.serializer(),
            "Claude Code"
        )
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
        @SerialName("next_page") val nextPage: String? = null
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
        val bodyStr = body.toString()
        val response = client.post(url) {
            applyManagedAgentsHeaders(apiKey)
            setBody(bodyStr)
        }
        if (response.status.value !in 200..299) {
            throw ClaudeHttp.networkError(
                response.status.value,
                response.bodyAsText(),
                url,
                "Claude Code",
                requestBody = bodyStr
            )
        }
    }

    suspend fun listEvents(
        apiKey: String,
        sessionId: String,
        limit: Int? = 1000,
        page: String? = null
    ): ListEventsResponse {
        val url = buildString {
            append("$baseUrl/v1/sessions/$sessionId/events")
            val params = buildList {
                if (limit != null) add("limit=$limit")
                if (page != null) add("page=$page")
            }
            if (params.isNotEmpty()) append("?${params.joinToString("&")}")
        }
        val response = client.get(url) { applyManagedAgentsHeaders(apiKey) }
        return ClaudeHttp.decodeOrThrow(
            response.bodyAsText(),
            response.status.value,
            url,
            ListEventsResponse.serializer(),
            "Claude Code"
        )
    }

    fun mapSessionState(status: String?): String = when (status?.lowercase()) {
        SessionStatus.RUNNING -> "IN_PROGRESS"
        SessionStatus.IDLE -> "IDLE"
        SessionStatus.TERMINATED -> "COMPLETED"
        SessionStatus.RESCHEDULING -> "QUEUED"
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
}

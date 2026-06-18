package fr.geoking.julius.api.claude

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ClaudeApiTest {

    private val codeClient = ClaudeCodeClient(io.ktor.client.HttpClient())

    @Test
    fun messageExtractText_joinsTextBlocks() {
        val message = ClaudeClient.Message(
            content = listOf(
                ClaudeClient.TextContentBlock(type = "text", text = "Hello"),
                ClaudeClient.TextContentBlock(type = "text", text = "World")
            )
        )
        assertEquals("Hello\nWorld", message.extractText())
    }

    @Test
    fun mapSessionState_matchesDocumentedStatuses() {
        assertEquals("IN_PROGRESS", codeClient.mapSessionState(ClaudeCodeClient.SessionStatus.RUNNING))
        assertEquals("IDLE", codeClient.mapSessionState(ClaudeCodeClient.SessionStatus.IDLE))
        assertEquals("COMPLETED", codeClient.mapSessionState(ClaudeCodeClient.SessionStatus.TERMINATED))
        assertEquals("QUEUED", codeClient.mapSessionState(ClaudeCodeClient.SessionStatus.RESCHEDULING))
    }

    @Test
    fun extractText_fromUserAndAgentMessages() {
        val content = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", "Fix the bug") })
        }
        val user = ClaudeCodeClient.ClaudeEvent(
            id = "1",
            type = "user.message",
            processedAt = "2026-03-15T10:00:00Z",
            content = content
        )
        val agent = ClaudeCodeClient.ClaudeEvent(
            id = "2",
            type = "agent.message",
            processedAt = "2026-03-15T10:01:00Z",
            content = buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", "On it.") })
            }
        )
        assertEquals("Fix the bug", codeClient.extractText(user))
        assertEquals("On it.", codeClient.extractText(agent))
    }

    @Test
    fun eventsToChatItems_groupsProgressUpdates() {
        val events = listOf(
            ClaudeCodeClient.ClaudeEvent(
                id = "1",
                type = "user.message",
                processedAt = "2026-03-15T10:00:00Z",
                content = buildJsonArray {
                    add(buildJsonObject { put("type", "text"); put("text", "Add feature") })
                }
            ),
            ClaudeCodeClient.ClaudeEvent(
                id = "2",
                type = "agent.tool_use",
                processedAt = "2026-03-15T10:01:00Z",
                name = "bash"
            ),
            ClaudeCodeClient.ClaudeEvent(
                id = "3",
                type = "agent.mcp_tool_use",
                processedAt = "2026-03-15T10:02:00Z",
                name = "create_pull_request"
            ),
            ClaudeCodeClient.ClaudeEvent(
                id = "4",
                type = "agent.message",
                processedAt = "2026-03-15T10:03:00Z",
                content = buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "Done: https://github.com/o/r/pull/42")
                    })
                }
            )
        )
        val items = codeClient.eventsToChatItems(events)
        assertEquals(3, items.size)
        assertTrue(items[1] is fr.geoking.julius.api.jules.JulesChatItem.AgentMessage)
        val progress = items[1] as fr.geoking.julius.api.jules.JulesChatItem.AgentMessage
        assertEquals(2, progress.subItems.size)
    }

    @Test
    fun findPullRequestUrl_fromAgentMessage() {
        val events = listOf(
            ClaudeCodeClient.ClaudeEvent(
                id = "1",
                type = "agent.message",
                processedAt = "2026-03-15T10:00:00Z",
                content = buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "PR: https://github.com/acme/app/pull/99")
                    })
                }
            )
        )
        assertEquals("https://github.com/acme/app/pull/99", codeClient.findPullRequestUrl(events))
    }
}

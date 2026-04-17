package fr.geoking.julius.api.jules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject

class JulesGroupingTest {

    private val client = JulesClient(io.ktor.client.HttpClient())

    @Test
    fun testGroupingConsecutiveProgressUpdates() {
        val activities = listOf(
            JulesClient.JulesActivity(
                id = "1",
                createTime = "2024-01-01T10:00:00Z",
                originator = "user",
                messageSent = JulesClient.MessageSent(prompt = "Initial prompt")
            ),
            JulesClient.JulesActivity(
                id = "2",
                createTime = "2024-01-01T10:01:00Z",
                originator = "agent",
                progressUpdated = JulesClient.ProgressUpdated(title = "Thinking")
            ),
            JulesClient.JulesActivity(
                id = "3",
                createTime = "2024-01-01T10:02:00Z",
                originator = "agent",
                progressUpdated = JulesClient.ProgressUpdated(title = "Searching")
            ),
            JulesClient.JulesActivity(
                id = "4",
                createTime = "2024-01-01T10:03:00Z",
                originator = "agent",
                sessionCompleted = JsonObject(emptyMap())
            )
        )

        val chatItems = client.activitiesToChatItems(activities)

        assertEquals(3, chatItems.size)
        assertTrue(chatItems[0] is JulesChatItem.UserMessage)
        assertTrue(chatItems[1] is JulesChatItem.AgentMessage)
        assertTrue(chatItems[2] is JulesChatItem.AgentMessage)

        val grouped = chatItems[1] as JulesChatItem.AgentMessage
        assertEquals("Progress", grouped.title)
        assertEquals(2, grouped.subItems.size)
        assertEquals("Thinking", grouped.subItems[0].text)
        assertEquals("Searching", grouped.subItems[1].text)
        assertEquals("Thinking\nSearching", grouped.text)

        val completed = chatItems[2] as JulesChatItem.AgentMessage
        assertEquals("Session completed.", completed.title)
        assertEquals(1, completed.subItems.size)
        assertEquals("Session completed.", completed.text)
    }

    @Test
    fun testUpdatedActionFilenames() {
        val activities = listOf(
            JulesClient.JulesActivity(
                id = "1",
                createTime = "2024-01-01T10:00:00Z",
                originator = "agent",
                progressUpdated = JulesClient.ProgressUpdated(title = "Updated"),
                artifacts = listOf(
                    JulesClient.JulesArtifact(changeSet = JulesClient.ChangeSet(source = "file1.kt")),
                    JulesClient.JulesArtifact(changeSet = JulesClient.ChangeSet(source = "file2.kt"))
                )
            )
        )

        val chatItems = client.activitiesToChatItems(activities)
        assertEquals(1, chatItems.size)
        val msg = chatItems[0] as JulesChatItem.AgentMessage
        assertEquals("Updated file1.kt, file2.kt", msg.title)
    }

    @Test
    fun testNewActivityTypes() {
        val activities = listOf(
            JulesClient.JulesActivity(
                id = "1",
                createTime = "2024-01-01T10:00:00Z",
                originator = "user",
                userMessaged = JulesClient.UserMessaged(userMessage = "Hello Jules")
            ),
            JulesClient.JulesActivity(
                id = "2",
                createTime = "2024-01-01T10:01:00Z",
                originator = "agent",
                agentMessaged = JulesClient.AgentMessaged(agentMessage = "Hello user")
            ),
            JulesClient.JulesActivity(
                id = "3",
                createTime = "2024-01-01T10:02:00Z",
                originator = "system",
                sessionFailed = JulesClient.SessionFailed(reason = "Too many tokens")
            ),
            JulesClient.JulesActivity(
                id = "4",
                createTime = "2024-01-01T10:03:00Z",
                originator = "system",
                description = "Custom system message"
            )
        )

        val chatItems = client.activitiesToChatItems(activities)

        assertEquals(4, chatItems.size)
        assertEquals("Hello Jules", (chatItems[0] as JulesChatItem.UserMessage).text)
        assertEquals("Hello user", (chatItems[1] as JulesChatItem.AgentMessage).text)
        assertEquals("Session failed: Too many tokens", (chatItems[2] as JulesChatItem.AgentMessage).text)
        assertEquals("Custom system message", (chatItems[3] as JulesChatItem.AgentMessage).text)
    }
}

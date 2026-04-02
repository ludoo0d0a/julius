package fr.geoking.julius.shared.conversation

/**
 * Platform-agnostic state for the conversation history screen.
 * Shared so UI logic (what to show) lives in one place; each platform renders it.
 */
data class HistoryScreenState(
    val title: String = DEFAULT_TITLE,
    val emptyMessage: String = DEFAULT_EMPTY_MESSAGE,
    val items: List<HistoryItem> = emptyList()
) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        const val DEFAULT_TITLE = "Conversation History"
        const val DEFAULT_EMPTY_MESSAGE = "No conversation history"
    }
}

/**
 * Single row to display in history: message text and whether it was sent by the user.
 */
data class HistoryItem(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)

/**
 * Builds the history screen state from current conversation state.
 */
fun ConversationState.toHistoryScreenState(
    title: String = HistoryScreenState.DEFAULT_TITLE,
    emptyMessage: String = HistoryScreenState.DEFAULT_EMPTY_MESSAGE
): HistoryScreenState = HistoryScreenState(
    title = title,
    emptyMessage = emptyMessage,
    items = messages.map { msg ->
        HistoryItem(
            text = msg.text,
            isUser = msg.sender == Role.User,
            timestamp = msg.timestamp
        )
    }
)

package fr.geoking.julius.shared

interface MessagePersistence {
    suspend fun saveMessage(msg: ChatMessage)
    suspend fun loadMessages(limit: Int? = null): List<ChatMessage>
    suspend fun clearMessages()
    suspend fun cleanupOldMessages(threshold: Long)
}

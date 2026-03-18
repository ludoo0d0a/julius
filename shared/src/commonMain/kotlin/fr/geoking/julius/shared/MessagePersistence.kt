package fr.geoking.julius.shared

interface MessagePersistence {
    suspend fun saveMessage(msg: ChatMessage)
    suspend fun loadMessages(): List<ChatMessage>
    suspend fun clearMessages()
}

package fr.geoking.julius.persistence

import fr.geoking.julius.shared.conversation.ChatMessage
import fr.geoking.julius.shared.conversation.MessagePersistence
import fr.geoking.julius.shared.conversation.Role

class RoomMessagePersistence(
    private val dao: ChatMessageDao
) : MessagePersistence {

    override suspend fun saveMessage(msg: ChatMessage) {
        try {
            dao.insert(
                ChatMessageEntity(
                    id = msg.id,
                    sender = msg.sender.name,
                    text = msg.text,
                    timestamp = msg.timestamp
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("RoomMessagePersistence", "Failed to save message", e)
        }
    }

    override suspend fun loadMessages(limit: Int?): List<ChatMessage> {
        val entities = try {
            if (limit != null) {
                dao.getLastMessages(limit).reversed()
            } else {
                dao.getAllMessages()
            }
        } catch (e: Exception) {
            android.util.Log.e("RoomMessagePersistence", "Failed to load messages", e)
            emptyList()
        }
        return entities.mapNotNull { entity ->
            try {
                ChatMessage(
                    id = entity.id,
                    sender = Role.valueOf(entity.sender),
                    text = entity.text,
                    timestamp = entity.timestamp
                )
            } catch (e: IllegalArgumentException) {
                // Skip malformed messages with unknown roles
                android.util.Log.e("RoomMessagePersistence", "Unknown role in database: ${entity.sender}", e)
                null
            }
        }
    }

    override suspend fun clearMessages() {
        try {
            dao.clearAll()
        } catch (e: Exception) {
            android.util.Log.e("RoomMessagePersistence", "Failed to clear messages", e)
        }
    }

    override suspend fun cleanupOldMessages(threshold: Long) {
        try {
            dao.deleteOlderThan(threshold)
        } catch (e: Exception) {
            android.util.Log.e("RoomMessagePersistence", "Failed to cleanup old messages", e)
        }
    }
}

package fr.geoking.julius.persistence

import fr.geoking.julius.shared.ChatMessage
import fr.geoking.julius.shared.MessagePersistence
import fr.geoking.julius.shared.Role

class RoomMessagePersistence(
    private val dao: ChatMessageDao
) : MessagePersistence {

    override suspend fun saveMessage(msg: ChatMessage) {
        dao.insert(
            ChatMessageEntity(
                id = msg.id,
                sender = msg.sender.name,
                text = msg.text,
                timestamp = msg.timestamp
            )
        )
    }

    override suspend fun loadMessages(limit: Int?): List<ChatMessage> {
        val entities = if (limit != null) {
            dao.getLastMessages(limit).reversed()
        } else {
            dao.getAllMessages()
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
        dao.clearAll()
    }

    override suspend fun cleanupOldMessages(threshold: Long) {
        dao.deleteOlderThan(threshold)
    }
}

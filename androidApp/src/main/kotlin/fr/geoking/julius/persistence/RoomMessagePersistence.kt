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

    override suspend fun loadMessages(): List<ChatMessage> {
        return dao.getAllMessages().map { entity ->
            ChatMessage(
                id = entity.id,
                sender = Role.valueOf(entity.sender),
                text = entity.text,
                timestamp = entity.timestamp
            )
        }
    }

    override suspend fun clearMessages() {
        dao.clearAll()
    }
}

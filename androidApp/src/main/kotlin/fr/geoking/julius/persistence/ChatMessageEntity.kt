package fr.geoking.julius.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.julius.shared.conversation.Role

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sender: String, // "User" or "Assistant"
    val text: String,
    val timestamp: Long
)

package fr.geoking.julius.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(limit: Int): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE timestamp < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

package fr.geoking.julius.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "features")
data class FeatureEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priority: Int = 0,
    val position: Int = 0,
    val sourceName: String,
    val sessionId: String? = null,
    val status: String = "PENDING", // PENDING, QUEUED, IN_PROGRESS, COMPLETED, FAILED
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

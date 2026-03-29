package fr.geoking.julius.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jules_activities")
data class JulesActivityEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val originator: String,
    val text: String,
    val timestamp: String,
    val sortTimestamp: Long
)

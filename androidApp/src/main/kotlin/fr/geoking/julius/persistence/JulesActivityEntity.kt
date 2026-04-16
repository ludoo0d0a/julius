package fr.geoking.julius.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jules_activities")
data class JulesActivityEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val originator: String,
    val text: String,
    val timestamp: String,
    val sortTimestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val isPendingOffline: Boolean = false,
    val type: String? = null,
    val artifactsJson: String? = null,
    val activityJson: String? = null
)

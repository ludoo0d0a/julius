package fr.geoking.julius.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jules_sessions")
data class JulesSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val sourceName: String,
    val prUrl: String?,
    val prTitle: String?,
    val prState: String?, // open, closed, merged
    val isArchived: Boolean = false,
    val lastUpdated: Long
)

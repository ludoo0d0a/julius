package fr.geoking.julius.persistence

import androidx.room.ColumnInfo
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
    val prMergeable: Boolean?, // true if no conflicts, false if conflicts, null if unknown
    val sessionState: String?, // QUEUED, PLANNING, COMPLETED, etc.
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
    val lastUpdated: Long,
    @ColumnInfo(defaultValue = "0")
    val isPendingOffline: Boolean = false,
    val queuedAt: Long? = null,
    val apiKey: String? = null
)

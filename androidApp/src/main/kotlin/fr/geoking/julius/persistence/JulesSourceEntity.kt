package fr.geoking.julius.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jules_sources")
data class JulesSourceEntity(
    @PrimaryKey val name: String,
    val id: String,
    val owner: String?,
    val repo: String?,
    val lastUpdated: Long
)

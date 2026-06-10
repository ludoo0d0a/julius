package fr.geoking.julius.persistence

import androidx.room.Entity

@Entity(tableName = "account_daily_usage", primaryKeys = ["accountId", "dayEpoch"])
data class AccountDailyUsageEntity(
    val accountId: String,
    val dayEpoch: Long,
    val startedCount: Int,
)

package fr.geoking.julius.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AccountDailyUsageDao {
    @Query("SELECT * FROM account_daily_usage WHERE accountId = :accountId AND dayEpoch = :dayEpoch")
    suspend fun getUsage(accountId: String, dayEpoch: Long): AccountDailyUsageEntity?

    @Query("SELECT * FROM account_daily_usage WHERE dayEpoch = :dayEpoch")
    suspend fun getAllForDay(dayEpoch: Long): List<AccountDailyUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountDailyUsageEntity)
}

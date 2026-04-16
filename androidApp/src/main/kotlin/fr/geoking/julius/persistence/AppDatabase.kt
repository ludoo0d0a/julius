package fr.geoking.julius.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        JulesSessionEntity::class,
        JulesActivityEntity::class,
        StationPriceSampleEntity::class,
        MarketDailyQuoteEntity::class,
        LocalFuelAvgDailyEntity::class,
        FuelPricePredictionEntity::class,
        FuelPricePredictionScoreEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun julesDao(): JulesDao
    abstract fun stationPriceSampleDao(): StationPriceSampleDao
    abstract fun marketDailyQuoteDao(): MarketDailyQuoteDao
    abstract fun localFuelAvgDailyDao(): LocalFuelAvgDailyDao
    abstract fun fuelPricePredictionDao(): FuelPricePredictionDao
    abstract fun fuelPricePredictionScoreDao(): FuelPricePredictionScoreDao

    companion object {
        // v1 -> v2: no schema change; we keep data across upgrades.
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op (schema unchanged)
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `jules_sessions` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `prUrl` TEXT,
                        `prTitle` TEXT,
                        `prState` TEXT,
                        `isArchived` INTEGER NOT NULL DEFAULT 0,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `jules_activities` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `originator` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `timestamp` TEXT NOT NULL,
                        `sortTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jules_sessions` ADD COLUMN `prMergeable` INTEGER")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jules_sessions` ADD COLUMN `sessionState` TEXT")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `station_price_samples` (
                        `id` TEXT NOT NULL,
                        `stationId` TEXT NOT NULL,
                        `fuelId` TEXT NOT NULL,
                        `fuelName` TEXT NOT NULL,
                        `price` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `outOfStock` INTEGER NOT NULL,
                        `observedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_station_price_samples_stationId_fuelId_observedAtMs` ON `station_price_samples` (`stationId`, `fuelId`, `observedAtMs`)"
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `market_daily_quotes` (
                        `symbol` TEXT NOT NULL,
                        `day` TEXT NOT NULL,
                        `close` REAL NOT NULL,
                        `fetchedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`symbol`, `day`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_fuel_avg_daily` (
                        `day` TEXT NOT NULL,
                        `fuelId` TEXT NOT NULL,
                        `locationKey` TEXT NOT NULL,
                        `avgPrice` REAL NOT NULL,
                        `stationCount` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`day`, `fuelId`, `locationKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_fuel_avg_daily_locationKey_fuelId_day` ON `local_fuel_avg_daily` (`locationKey`, `fuelId`, `day`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fuel_price_predictions` (
                        `id` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `createdDay` TEXT NOT NULL,
                        `targetDay` TEXT NOT NULL,
                        `horizonDays` INTEGER NOT NULL,
                        `fuelId` TEXT NOT NULL,
                        `locationKey` TEXT NOT NULL,
                        `predictedUp` INTEGER NOT NULL,
                        `predictedPrice` REAL NOT NULL,
                        `baselinePrice` REAL NOT NULL,
                        `marketScore` REAL NOT NULL,
                        `inputsJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_fuel_price_predictions_createdDay_fuelId_locationKey_horizonDays` ON `fuel_price_predictions` (`createdDay`, `fuelId`, `locationKey`, `horizonDays`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fuel_price_predictions_targetDay_fuelId_locationKey` ON `fuel_price_predictions` (`targetDay`, `fuelId`, `locationKey`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fuel_price_prediction_scores` (
                        `id` TEXT NOT NULL,
                        `predictionId` TEXT NOT NULL,
                        `scoredAtMs` INTEGER NOT NULL,
                        `actualPrice` REAL NOT NULL,
                        `baselinePrice` REAL NOT NULL,
                        `directionCorrect` INTEGER NOT NULL,
                        `absError` REAL NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_fuel_price_prediction_scores_predictionId` ON `fuel_price_prediction_scores` (`predictionId`)"
                )
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jules_sessions` ADD COLUMN `isPendingOffline` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `jules_sessions` ADD COLUMN `queuedAt` INTEGER")
                db.execSQL("ALTER TABLE `jules_activities` ADD COLUMN `isPendingOffline` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jules_activities` ADD COLUMN `type` TEXT")
                db.execSQL("ALTER TABLE `jules_activities` ADD COLUMN `artifactsJson` TEXT")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jules_sessions` ADD COLUMN `apiKey` TEXT")
                db.execSQL("ALTER TABLE `jules_activities` ADD COLUMN `activityJson` TEXT")
            }
        }
    }
}

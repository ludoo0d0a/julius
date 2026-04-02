package fr.geoking.julius.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        JulesSessionEntity::class,
        JulesActivityEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun julesDao(): JulesDao

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
    }
}

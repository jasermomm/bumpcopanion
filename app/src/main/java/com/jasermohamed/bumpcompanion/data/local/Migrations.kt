package com.jasermohamed.bumpcompanion.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `location_track_points` (
                    `driveId` TEXT NOT NULL,
                    `elapsedRealtimeNanos` INTEGER NOT NULL,
                    `epochMillis` INTEGER NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `accuracyMetres` REAL NOT NULL,
                    `bearingDegrees` REAL,
                    `speedMetresPerSecond` REAL,
                    PRIMARY KEY(`driveId`, `elapsedRealtimeNanos`)
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_location_track_points_driveId` ON `location_track_points` (`driveId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_location_track_points_epochMillis` ON `location_track_points` (`epochMillis`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `diagnostic_files` (
                    `id` TEXT NOT NULL,
                    `driveId` TEXT,
                    `fileName` TEXT NOT NULL,
                    `loggingMode` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `expiresAt` INTEGER,
                    `checksumSha256` TEXT,
                    `corrupted` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_files_driveId` ON `diagnostic_files` (`driveId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_files_createdAt` ON `diagnostic_files` (`createdAt`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_files_expiresAt` ON `diagnostic_files` (`expiresAt`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}

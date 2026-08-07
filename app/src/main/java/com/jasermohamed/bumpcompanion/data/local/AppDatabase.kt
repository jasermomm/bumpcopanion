package com.jasermohamed.bumpcompanion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SpeedBumpEntity::class,
        CandidateEventEntity::class,
        DriveSessionEntity::class,
        EncounterEntity::class,
        LocationTrackPointEntity::class,
        CalibrationProfileEntity::class,
        ImportBatchEntity::class,
        ImportBatchItemEntity::class,
        DiagnosticFileEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun speedBumpDao(): SpeedBumpDao
    abstract fun candidateDao(): CandidateDao
    abstract fun driveSessionDao(): DriveSessionDao
    abstract fun encounterDao(): EncounterDao
    abstract fun locationTrackPointDao(): LocationTrackPointDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun diagnosticFileDao(): DiagnosticFileDao
}

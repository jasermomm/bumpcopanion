package com.jasermohamed.bumpcompanion.data.local

import androidx.room.*
import com.jasermohamed.bumpcompanion.domain.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedBumpDao {
    @Query("SELECT * FROM speed_bumps ORDER BY lastDetectedAt DESC")
    fun observeAll(): Flow<List<SpeedBumpEntity>>

    @Query("SELECT * FROM speed_bumps WHERE status = :status ORDER BY lastDetectedAt DESC")
    fun observeByStatus(status: BumpStatus): Flow<List<SpeedBumpEntity>>

    @Query("SELECT COUNT(*) FROM speed_bumps WHERE status = 'CONFIRMED' AND archived = 0 AND markedRemoved = 0")
    fun observeConfirmedCount(): Flow<Int>

    @Query("SELECT * FROM speed_bumps WHERE id = :id")
    suspend fun getById(id: String): SpeedBumpEntity?

    @Query("SELECT * FROM speed_bumps WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<SpeedBumpEntity>

    @Query("SELECT * FROM speed_bumps WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon AND warningEnabled = 1 AND archived = 0 AND markedRemoved = 0 AND status IN ('CONFIRMED','IMPORTED')")
    suspend fun queryWarningCandidates(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<SpeedBumpEntity>

    @Query("SELECT * FROM speed_bumps WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon AND archived = 0 AND markedRemoved = 0")
    suspend fun queryNearby(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<SpeedBumpEntity>

    @Query("SELECT * FROM speed_bumps WHERE status IN ('CONFIRMED','IMPORTED') AND archived = 0 AND markedRemoved = 0 ORDER BY lastDetectedAt DESC")
    suspend fun getExportable(): List<SpeedBumpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SpeedBumpEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SpeedBumpEntity>)

    @Query("UPDATE speed_bumps SET warningEnabled = :enabled WHERE id = :id")
    suspend fun setWarningEnabled(id: String, enabled: Boolean)

    @Query("UPDATE speed_bumps SET archived = :archived, status = CASE WHEN :archived THEN 'ARCHIVED' ELSE 'CONFIRMED' END WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE speed_bumps SET markedRemoved = 1, status = 'REMOVED', warningEnabled = 0 WHERE id = :id")
    suspend fun markRemoved(id: String)

    @Query("UPDATE speed_bumps SET lastWarnedAt = :timestamp WHERE id = :id")
    suspend fun updateLastWarned(id: String, timestamp: Long)

    @Delete
    suspend fun delete(item: SpeedBumpEntity)

    @Query("DELETE FROM speed_bumps")
    suspend fun deleteAll()
}

@Dao
interface CandidateDao {
    @Query("SELECT * FROM candidate_events WHERE decision = 'PENDING' ORDER BY detectedAt DESC")
    fun observePending(): Flow<List<CandidateEventEntity>>

    @Query("SELECT COUNT(*) FROM candidate_events WHERE decision = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM candidate_events WHERE id = :id")
    suspend fun getById(id: String): CandidateEventEntity?

    @Query("SELECT * FROM candidate_events WHERE driveId = :driveId ORDER BY detectedAt ASC")
    suspend fun getForDrive(driveId: String): List<CandidateEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CandidateEventEntity)

    @Query("UPDATE candidate_events SET decision = :decision, note = :note WHERE id = :id")
    suspend fun setDecision(id: String, decision: CandidateDecision, note: String = "")

    @Query("DELETE FROM candidate_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM candidate_events")
    suspend fun deleteAll()
}

@Dao
interface DriveSessionDao {
    @Query("SELECT * FROM drive_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<DriveSessionEntity>>

    @Query("SELECT * FROM drive_sessions ORDER BY startedAt DESC LIMIT 1")
    fun observeLatest(): Flow<DriveSessionEntity?>

    @Query("SELECT * FROM drive_sessions WHERE id = :id")
    suspend fun getById(id: String): DriveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DriveSessionEntity)

    @Query("SELECT * FROM drive_sessions WHERE incomplete = 1 ORDER BY startedAt DESC")
    suspend fun getIncomplete(): List<DriveSessionEntity>

    @Query("DELETE FROM drive_sessions")
    suspend fun deleteAll()
}

@Dao
interface EncounterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: EncounterEntity)

    @Query("SELECT * FROM encounters WHERE bumpId = :bumpId ORDER BY encounteredAt DESC")
    fun observeForBump(bumpId: String): Flow<List<EncounterEntity>>

    @Query("DELETE FROM encounters WHERE bumpId = :bumpId")
    suspend fun deleteForBump(bumpId: String)

    @Query("DELETE FROM encounters")
    suspend fun deleteAll()
}


@Dao
interface LocationTrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocationTrackPointEntity>)

    @Query("SELECT * FROM location_track_points WHERE driveId = :driveId ORDER BY elapsedRealtimeNanos ASC")
    suspend fun getForDrive(driveId: String): List<LocationTrackPointEntity>

    @Query("DELETE FROM location_track_points WHERE driveId = :driveId")
    suspend fun deleteForDrive(driveId: String)

    @Query("DELETE FROM location_track_points")
    suspend fun deleteAll()
}

@Dao
interface CalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CalibrationProfileEntity)

    @Query("SELECT * FROM calibration_profiles ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<CalibrationProfileEntity?>

    @Query("DELETE FROM calibration_profiles")
    suspend fun deleteAll()
}

@Dao
interface ImportBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: ImportBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ImportBatchItemEntity>)

    @Query("SELECT * FROM import_batch_items WHERE batchId = :batchId")
    suspend fun getItems(batchId: String): List<ImportBatchItemEntity>

    @Query("UPDATE import_batches SET undone = 1 WHERE id = :batchId")
    suspend fun markUndone(batchId: String)

    @Query("DELETE FROM import_batch_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM import_batches")
    suspend fun deleteAllBatches()
}

@Dao
interface DiagnosticFileDao {
    @Query("SELECT * FROM diagnostic_files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DiagnosticFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DiagnosticFileEntity)

    @Query("DELETE FROM diagnostic_files WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM diagnostic_files")
    suspend fun deleteAll()
}

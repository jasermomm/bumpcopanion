package com.jasermohamed.bumpcompanion.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jasermohamed.bumpcompanion.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun bumpCrudAndBoundingQuery() = runTest {
        val dao = database.speedBumpDao()
        val entity = SpeedBumpEntity(
            id = "bump-1",
            latitude = 30.0444,
            longitude = 31.2357,
            rawLatitude = 30.0444,
            rawLongitude = 31.2357,
            horizontalAccuracyMetres = 7f,
            coordinateConfidence = 0.9f,
            confidence = 0.92f,
            status = BumpStatus.CONFIRMED,
            source = BumpSource.MANUAL,
            directionality = Directionality.BIDIRECTIONAL,
            primaryBearing = null,
            oppositeBearing = null,
            bearingTolerance = 45f,
            firstDetectedAt = 1L,
            lastDetectedAt = 1L,
            lastWarnedAt = null,
            encounterCount = 1,
            confirmationCount = 1,
            rejectionCount = 0,
            missingReports = 0,
            importedSource = null,
            notes = "test",
            warningEnabled = true,
            customWarningDistanceMetres = null,
            algorithmVersion = 1,
            archived = false,
            markedRemoved = false,
            regionLabel = "Cairo",
            roadName = null,
        )
        dao.upsert(entity)

        assertEquals(entity, dao.getById("bump-1"))
        assertEquals(1, dao.observeConfirmedCount().first())
        assertEquals(
            listOf(entity),
            dao.queryWarningCandidates(30.0, 30.1, 31.2, 31.3),
        )

        dao.setWarningEnabled("bump-1", false)
        assertEquals(0, dao.queryWarningCandidates(30.0, 30.1, 31.2, 31.3).size)
    }

    @Test
    fun pendingCandidateCountTracksDecision() = runTest {
        val dao = database.candidateDao()
        val candidate = CandidateEventEntity(
            id = "candidate-1",
            driveId = null,
            detectedAt = 1L,
            eventElapsedRealtimeNanos = 1L,
            latitude = 30.0,
            longitude = 31.0,
            horizontalAccuracyMetres = 10f,
            coordinateConfidence = 0.8f,
            speedMetresPerSecond = 5f,
            bearingDegrees = 90f,
            confidence = 0.75f,
            eventType = RoadEventType.POSSIBLE_SPEED_BUMP,
            decision = CandidateDecision.PENDING,
            confidenceReasons = listOf(ConfidenceReason.OPPOSING_VERTICAL_PEAKS),
            positiveVerticalPeak = 4f,
            negativeVerticalPeak = -3f,
            peakToPeak = 7f,
            peakGapMillis = 300L,
            verticalRms = 1f,
            jerkPeak = 20f,
            gyroscopeEnergy = 0.1f,
            orientationVariance = 0.01f,
            phoneStability = 0.9f,
            source = BumpSource.DETECTED,
            note = "",
        )
        dao.upsert(candidate)
        assertEquals(1, dao.observePendingCount().first())
        dao.setDecision(candidate.id, CandidateDecision.REJECTED)
        assertEquals(0, dao.observePendingCount().first())
    }
}

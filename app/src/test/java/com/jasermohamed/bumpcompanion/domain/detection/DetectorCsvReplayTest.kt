package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.*
import java.io.StringReader
import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorCsvReplayTest {
    @Test
    fun `recorded telemetry replays through the production detector`() {
        val lines = ArrayList<String>()
        lines += "# mode=test"
        lines += DetectorCsvFormat.headerLine()
        repeat(700) { index ->
            val time = index / 100f
            val vertical = 4.8f * gaussian(time, 3f, 0.14f) - 4.1f * gaussian(time, 3.38f, 0.17f)
            val sample = MotionSample(
                elapsedRealtimeNanos = 30_000_000_000L + index * 10_000_000L,
                epochMillis = index * 10L,
                accelerationX = 0.02f,
                accelerationY = 0f,
                accelerationZ = 9.80665f + vertical,
                gyroscopeZ = 0.02f,
                verticalAcceleration = vertical,
                lateralAcceleration = 0.02f,
                orientationReliable = true,
            )
            lines += DetectorCsvFormat.frameLine(
                DetectorTelemetryFrame(
                    sample, 7f, 0.4f, LocationQuality.GOOD, 0.96f,
                    vertical, vertical, 0f, kotlin.math.abs(vertical), 0f, 0.02f,
                    0.2f, 0.08f, RoadSurfaceState.SMOOTH, DetectorState.NORMAL,
                )
            )
        }
        val replay = DetectorCsvReplay.replay(StringReader(lines.joinToString("\n")))
        assertEquals(700, replay.samplesRead)
        assertEquals(0, replay.malformedRows)
        assertTrue(replay.events.isNotEmpty())
    }

    @Test
    fun `CSV rows match their versioned header width`() {
        val point = GeoPoint(1.0, 2.0, 4f, 90f, 6f, 10L, 20L, 0.5f)
        val sample = MotionSample(10L, 20L, 0f, 0f, 9.80665f, verticalAcceleration = 0f)
        val frame = DetectorTelemetryFrame(
            sample, 6f, 0.5f, LocationQuality.GOOD, 1f, 0f, 0f, 0f, 0f, 0f, 0f,
            0.2f, 0.08f, RoadSurfaceState.SMOOTH, DetectorState.NORMAL,
        )
        val candidate = CandidateEvaluation(
            1L, DetectorState.SETTLING, RoadEventType.UNKNOWN, DetectionDisposition.REJECTED,
            0.2f, 0.2f, null, emptyList(), "quoted, explanation",
        )
        listOf(
            DetectorCsvFormat.locationLine(point, LocationQuality.GOOD),
            DetectorCsvFormat.frameLine(frame),
            DetectorCsvFormat.candidateLine(candidate),
        ).forEach { row ->
            assertEquals(DetectorCsvFormat.header.size, DetectorCsvReplay.parseCsvLine(row).size)
        }
    }

    private fun gaussian(time: Float, centre: Float, width: Float): Float =
        exp((-((time - centre) * (time - centre)) / (2f * width * width)).toDouble()).toFloat()
}

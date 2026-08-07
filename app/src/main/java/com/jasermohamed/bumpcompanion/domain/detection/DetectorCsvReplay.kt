package com.jasermohamed.bumpcompanion.domain.detection

import com.jasermohamed.bumpcompanion.domain.model.*
import java.io.Reader

/** Versioned CSV used by optional live diagnostics and exact offline detector replay. */
object DetectorCsvFormat {
    const val SCHEMA_VERSION = 2
    val header: List<String> = listOf(
        "schema_version", "record_type", "elapsed_nanos", "epoch_millis",
        "ax", "ay", "az", "linear_x", "linear_y", "linear_z",
        "gravity_x", "gravity_y", "gravity_z", "rotation_x", "rotation_y", "rotation_z", "rotation_w",
        "gyro_x", "gyro_y", "gyro_z", "vertical", "longitudinal", "lateral",
        "world_east", "world_north", "orientation_change", "orientation_reliable",
        "speed", "speed_accuracy", "location_quality", "phone_stability",
        "event_vertical", "broad_vertical", "high_frequency", "envelope", "jerk", "horizontal",
        "baseline_vertical_rms", "baseline_high_frequency_rms", "road_surface", "detector_state",
        "latitude", "longitude", "horizontal_accuracy", "bearing",
        "event_type", "profile", "confidence", "database_confidence", "disposition", "explanation",
    )

    fun headerLine(): String = header.joinToString(",")

    fun frameLine(frame: DetectorTelemetryFrame): String {
        val sample = frame.sample
        val values = blankRow("sample", sample.elapsedRealtimeNanos, sample.epochMillis)
        values.put("ax", sample.accelerationX); values.put("ay", sample.accelerationY); values.put("az", sample.accelerationZ)
        values.put("linear_x", sample.linearAccelerationX); values.put("linear_y", sample.linearAccelerationY); values.put("linear_z", sample.linearAccelerationZ)
        values.put("gravity_x", sample.gravityX); values.put("gravity_y", sample.gravityY); values.put("gravity_z", sample.gravityZ)
        values.put("rotation_x", sample.rotationVectorX); values.put("rotation_y", sample.rotationVectorY)
        values.put("rotation_z", sample.rotationVectorZ); values.put("rotation_w", sample.rotationVectorW)
        values.put("gyro_x", sample.gyroscopeX); values.put("gyro_y", sample.gyroscopeY); values.put("gyro_z", sample.gyroscopeZ)
        values.put("vertical", sample.verticalAcceleration); values.put("longitudinal", sample.longitudinalAcceleration)
        values.put("lateral", sample.lateralAcceleration); values.put("world_east", sample.worldAccelerationEast)
        values.put("world_north", sample.worldAccelerationNorth); values.put("orientation_change", sample.orientationChangeRadians)
        values.put("orientation_reliable", sample.orientationReliable); values.put("speed", frame.speedMetresPerSecond)
        values.put("speed_accuracy", frame.speedAccuracyMetresPerSecond); values.put("location_quality", frame.locationQuality)
        values.put("phone_stability", frame.phoneStabilityScore); values.put("event_vertical", frame.eventVerticalAcceleration)
        values.put("broad_vertical", frame.lowFrequencyVerticalAcceleration); values.put("high_frequency", frame.highFrequencyVerticalAcceleration)
        values.put("envelope", frame.verticalEnvelope); values.put("jerk", frame.verticalJerk)
        values.put("horizontal", frame.horizontalAcceleration); values.put("baseline_vertical_rms", frame.baselineVerticalRms)
        values.put("baseline_high_frequency_rms", frame.baselineHighFrequencyRms); values.put("road_surface", frame.roadSurfaceState)
        values.put("detector_state", frame.detectorState)
        return row(values)
    }

    fun locationLine(point: GeoPoint, quality: LocationQuality): String {
        val values = blankRow("location", point.elapsedRealtimeNanos, point.epochMillis)
        values.put("speed", point.speedMetresPerSecond); values.put("speed_accuracy", point.speedAccuracyMetresPerSecond)
        values.put("location_quality", quality); values.put("latitude", point.latitude); values.put("longitude", point.longitude)
        values.put("horizontal_accuracy", point.accuracyMetres); values.put("bearing", point.bearingDegrees)
        return row(values)
    }

    fun candidateLine(evaluation: CandidateEvaluation): String {
        val values = blankRow("candidate", evaluation.features?.eventElapsedRealtimeNanos ?: 0L, null)
        values.put("road_surface", evaluation.features?.roadSurfaceState); values.put("detector_state", evaluation.detectorState)
        values.put("event_type", evaluation.eventType); values.put("profile", evaluation.features?.profile)
        values.put("confidence", evaluation.confidence); values.put("database_confidence", evaluation.databaseConfidence)
        values.put("disposition", evaluation.disposition); values.put("explanation", evaluation.explanation.replace('\n', '|'))
        return row(values)
    }

    private val headerIndices by lazy { header.withIndex().associate { it.value to it.index } }

    private fun blankRow(type: String, elapsedNanos: Long, epochMillis: Long?): MutableList<Any?> =
        MutableList<Any?>(header.size) { "" }.also { values ->
            values.put("schema_version", SCHEMA_VERSION)
            values.put("record_type", type)
            values.put("elapsed_nanos", elapsedNanos)
            values.put("epoch_millis", epochMillis)
        }

    private fun MutableList<Any?>.put(name: String, value: Any?) {
        this[requireNotNull(headerIndices[name])] = value
    }

    private fun row(values: List<Any?>): String = values.joinToString(",") { escape(it) }

    private fun escape(value: Any?): String {
        val raw = when (value) {
            null -> ""
            is Float -> if (value.isFinite()) value.toString() else ""
            is Double -> if (value.isFinite()) value.toString() else ""
            else -> value.toString()
        }
        return if (raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${raw.replace("\"", "\"\"")}\""
        } else raw
    }
}

data class DetectorReplayResult(
    val samplesRead: Int,
    val malformedRows: Int,
    val events: List<DetectedRoadEvent>,
)

/** No Android dependency: JVM tests and analysis tools can replay a recorded drive byte-for-byte. */
object DetectorCsvReplay {
    fun replay(
        reader: Reader,
        detector: RoadEventDetector = HeuristicRoadEventDetector(),
        resetDetector: Boolean = true,
    ): DetectorReplayResult {
        if (resetDetector) detector.reset()
        var samplesRead = 0
        var malformed = 0
        val events = ArrayList<DetectedRoadEvent>()
        reader.buffered().use { source ->
            var firstLine = source.readLine() ?: return DetectorReplayResult(0, 0, emptyList())
            while (firstLine.startsWith("#")) {
                firstLine = source.readLine() ?: return DetectorReplayResult(0, 0, emptyList())
            }
            val columns = parseCsvLine(firstLine)
            val indices = columns.withIndex().associate { it.value to it.index }
            source.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val values = parseCsvLine(line)
                fun text(name: String): String = indices[name]?.let { values.getOrNull(it) }.orEmpty()
                if (text("record_type") != "sample") return@forEach
                try {
                    val sample = MotionSample(
                        elapsedRealtimeNanos = text("elapsed_nanos").toLong(),
                        epochMillis = text("epoch_millis").toLongOrNull() ?: 0L,
                        accelerationX = text("ax").floatOrZero(),
                        accelerationY = text("ay").floatOrZero(),
                        accelerationZ = text("az").floatOrZero(),
                        linearAccelerationX = text("linear_x").floatOrNaN(),
                        linearAccelerationY = text("linear_y").floatOrNaN(),
                        linearAccelerationZ = text("linear_z").floatOrNaN(),
                        linearAccelerationAvailable = text("linear_x").toFloatOrNull()?.isFinite() == true,
                        gravityX = text("gravity_x").floatOrNaN(),
                        gravityY = text("gravity_y").floatOrNaN(),
                        gravityZ = text("gravity_z").floatOrNaN(),
                        rotationVectorX = text("rotation_x").floatOrNaN(),
                        rotationVectorY = text("rotation_y").floatOrNaN(),
                        rotationVectorZ = text("rotation_z").floatOrNaN(),
                        rotationVectorW = text("rotation_w").floatOrNaN(),
                        gyroscopeX = text("gyro_x").floatOrZero(),
                        gyroscopeY = text("gyro_y").floatOrZero(),
                        gyroscopeZ = text("gyro_z").floatOrZero(),
                        verticalAcceleration = text("vertical").floatOrZero(),
                        longitudinalAcceleration = text("longitudinal").floatOrZero(),
                        lateralAcceleration = text("lateral").floatOrZero(),
                        worldAccelerationEast = text("world_east").floatOrNaN(),
                        worldAccelerationNorth = text("world_north").floatOrNaN(),
                        orientationChangeRadians = text("orientation_change").floatOrZero(),
                        orientationReliable = text("orientation_reliable").toBooleanStrictOrNull() ?: false,
                    )
                    val event = detector.addSample(
                        sample = sample,
                        speedMetresPerSecond = text("speed").floatOrNaN(),
                        speedAccuracyMetresPerSecond = text("speed_accuracy").floatOrNaN(),
                        locationQuality = runCatching { LocationQuality.valueOf(text("location_quality")) }
                            .getOrDefault(LocationQuality.UNAVAILABLE),
                        phoneStabilityScore = text("phone_stability").toFloatOrNull() ?: 1f,
                    )
                    samplesRead++
                    if (event != null) events += event
                } catch (_: IllegalArgumentException) {
                    malformed++
                }
            }
        }
        return DetectorReplayResult(samplesRead, malformed, events)
    }

    internal fun parseCsvLine(line: String): List<String> {
        val result = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    result += field.toString()
                    field.setLength(0)
                }
                else -> field.append(character)
            }
            index++
        }
        result += field.toString()
        return result
    }

    private fun String.floatOrZero(): Float = toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
    private fun String.floatOrNaN(): Float = toFloatOrNull()?.takeIf { it.isFinite() } ?: Float.NaN
}

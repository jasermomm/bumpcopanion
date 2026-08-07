package com.jasermohamed.bumpcompanion.data.local

import com.jasermohamed.bumpcompanion.domain.model.*

fun SpeedBumpEntity.toDomain() = SpeedBump(
    id, latitude, longitude, rawLatitude, rawLongitude, horizontalAccuracyMetres,
    coordinateConfidence, confidence, status, source, directionality, primaryBearing,
    oppositeBearing, bearingTolerance, firstDetectedAt, lastDetectedAt, lastWarnedAt,
    encounterCount, confirmationCount, rejectionCount, missingReports, importedSource,
    notes, warningEnabled, customWarningDistanceMetres, algorithmVersion, archived,
    markedRemoved, regionLabel, roadName,
)

fun SpeedBump.toEntity() = SpeedBumpEntity(
    id, latitude, longitude, rawLatitude, rawLongitude, horizontalAccuracyMetres,
    coordinateConfidence, confidence, status, source, directionality, primaryBearing,
    oppositeBearing, bearingTolerance, firstDetectedAt, lastDetectedAt, lastWarnedAt,
    encounterCount, confirmationCount, rejectionCount, missingReports, importedSource,
    notes, warningEnabled, customWarningDistanceMetres, algorithmVersion, archived,
    markedRemoved, regionLabel, roadName,
)

fun CandidateEventEntity.toDomain() = CandidateEvent(
    id, driveId, detectedAt, eventElapsedRealtimeNanos, latitude, longitude,
    horizontalAccuracyMetres, coordinateConfidence, speedMetresPerSecond, bearingDegrees,
    confidence, eventType, decision, confidenceReasons, positiveVerticalPeak,
    negativeVerticalPeak, peakToPeak, peakGapMillis, verticalRms, jerkPeak,
    gyroscopeEnergy, orientationVariance, phoneStability, source, note,
)

fun CandidateEvent.toEntity() = CandidateEventEntity(
    id, driveId, detectedAt, eventElapsedRealtimeNanos, latitude, longitude,
    horizontalAccuracyMetres, coordinateConfidence, speedMetresPerSecond, bearingDegrees,
    confidence, eventType, decision, confidenceReasons, positiveVerticalPeak,
    negativeVerticalPeak, peakToPeak, peakGapMillis, verticalRms, jerkPeak,
    gyroscopeEnergy, orientationVariance, phoneStability, source, note,
)

fun DriveSessionEntity.toDomain() = DriveSession(
    id, startedAt, endedAt, durationMillis, distanceMetres, maximumSpeedMetresPerSecond,
    averageSpeedMetresPerSecond, candidateCount, confirmedCount, rejectedCount,
    knownBumpPasses, warningCount, startLatitude, startLongitude, endLatitude,
    endLongitude, incomplete, placementProfile, vehicleProfile, algorithmVersion,
    serviceInterruptions, detectionQuality,
)

fun DriveSession.toEntity() = DriveSessionEntity(
    id, startedAt, endedAt, durationMillis, distanceMetres, maximumSpeedMetresPerSecond,
    averageSpeedMetresPerSecond, candidateCount, confirmedCount, rejectedCount,
    knownBumpPasses, warningCount, startLatitude, startLongitude, endLatitude,
    endLongitude, incomplete, placementProfile, vehicleProfile, algorithmVersion,
    serviceInterruptions, detectionQuality,
)

package com.jasermohamed.bumpcompanion.data.local

import androidx.room.TypeConverter
import com.jasermohamed.bumpcompanion.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun bumpStatusToString(value: BumpStatus): String = value.name
    @TypeConverter fun stringToBumpStatus(value: String): BumpStatus = BumpStatus.valueOf(value)
    @TypeConverter fun bumpSourceToString(value: BumpSource): String = value.name
    @TypeConverter fun stringToBumpSource(value: String): BumpSource = BumpSource.valueOf(value)
    @TypeConverter fun directionalityToString(value: Directionality): String = value.name
    @TypeConverter fun stringToDirectionality(value: String): Directionality = Directionality.valueOf(value)
    @TypeConverter fun eventTypeToString(value: RoadEventType): String = value.name
    @TypeConverter fun stringToEventType(value: String): RoadEventType = RoadEventType.valueOf(value)
    @TypeConverter fun decisionToString(value: CandidateDecision): String = value.name
    @TypeConverter fun stringToDecision(value: String): CandidateDecision = CandidateDecision.valueOf(value)
    @TypeConverter fun qualityToString(value: DetectionQuality): String = value.name
    @TypeConverter fun stringToQuality(value: String): DetectionQuality = DetectionQuality.valueOf(value)
    @TypeConverter fun reasonListToJson(value: List<ConfidenceReason>): String = json.encodeToString(value)
    @TypeConverter fun jsonToReasonList(value: String): List<ConfidenceReason> = runCatching {
        json.decodeFromString<List<ConfidenceReason>>(value)
    }.getOrDefault(emptyList())
}

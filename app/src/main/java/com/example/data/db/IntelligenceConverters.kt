package com.example.data.db

import androidx.room.TypeConverter
import com.example.data.*

class IntelligenceConverters {
    @TypeConverter
    fun fromLifecycleState(value: IntelligenceLifecycleState): String = value.name

    @TypeConverter
    fun toLifecycleState(value: String): IntelligenceLifecycleState = IntelligenceLifecycleState.valueOf(value)

    @TypeConverter
    fun fromClassification(value: FindingClassification): String = value.name

    @TypeConverter
    fun toClassification(value: String): FindingClassification = FindingClassification.valueOf(value)

    @TypeConverter
    fun fromConfidence(value: ConfidenceLevel): String = value.name

    @TypeConverter
    fun toConfidence(value: String): ConfidenceLevel = ConfidenceLevel.valueOf(value)

    @TypeConverter
    fun fromActionType(value: IntelligenceActionType): String = value.name

    @TypeConverter
    fun toActionType(value: String): IntelligenceActionType = IntelligenceActionType.valueOf(value)

    @TypeConverter
    fun fromActionStatus(value: IntelligenceActionStatus): String = value.name

    @TypeConverter
    fun toActionStatus(value: String): IntelligenceActionStatus = IntelligenceActionStatus.valueOf(value)
}

package com.example.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["isDeleted", "compatibilityStatus"]),
        Index(value = ["dateAdded"]),
        Index(value = ["contentHash"])
    ]
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mediaType: String, // "PHOTO" or "VIDEO"
    val year: Int = 2024,
    val duration: String = "",
    val genre: String = "Media",
    val imageUrl: String = "",
    val gradientColorsJson: String = "",
    val rating: Float = 0f,
    val isFavorite: Boolean = false,
    val progress: Float = 0f,
    val progressText: String = "",
    val category: String = "For You",
    val aiSummary: String = "",
    val moodTagsJson: String = "",
    val itemCount: Int? = null,
    val uriPath: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = 0L,
    val playCount: Int = 0,
    val exposureCount: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val lastExposedTimestamp: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val contentHash: String? = null,
    @ColumnInfo(defaultValue = "NULL") val parentContentId: String? = null,
    val eloRating: Double = 1500.0,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val lastViewedTimestamp: Long? = null,
    val compatibilityStatus: String = "PLAYABLE",
    val containerFormat: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val compatibilityReason: String = "",
    val conversionStatus: String = "NONE",
    val convertedUri: String = "",
    val lastCompatibilityCheckTimestamp: Long? = null,
    val selectionReason: String? = null,
    val creatorId: String? = null,
    val creatorName: String? = null,
    val sourcePlatform: String? = "LOCAL",
    val replacedByMediaId: String? = null
)

@Entity(tableName = "pairwise_outcomes")
data class PairwiseOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val optionAId: String,
    val optionBId: String,
    val chosenId: String,
    val roundNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val outcomeType: String = "VOTE", // VOTE, SKIP, DELETE_LEFT, DELETE_RIGHT
    val preRatingA: Double = 1500.0,
    val preRatingB: Double = 1500.0,
    val postRatingA: Double = 1500.0,
    val postRatingB: Double = 1500.0,
    val expectedScoreA: Double = 0.5,
    val kFactor: Double = 32.0
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "collection_items", primaryKeys = ["collectionId", "mediaId"])
data class CollectionItemEntity(
    val collectionId: String,
    val mediaId: String
)

@Entity(tableName = "micro_moments")
data class MicroMomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val tapCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "clip_interactions")
data class ClipInteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val clipTitle: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val previewCount: Int = 0,
    val selectCount: Int = 0,
    val exportCount: Int = 0,
    val lastInteractionTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_skip_events")
data class AISkipEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val eventType: String, // "SKIP_FORWARD", "SKIP_BACK", "SKIP_REVERSAL", "REPEATED_SKIP", "WATCHED_DESTINATION"
    val fromPosMs: Long,
    val toPosMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "evidence_records")
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val tier: String, // "PRODUCTION", "EXPERIMENTAL", "SIMULATION"
    val sampleCount: Int,
    val score: Double,
    val quality: Double,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val associatedManifestId: String? = null
)

@Entity(tableName = "tuning_audits")
data class TuningAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preferenceKey: String,
    val previousEffectiveValue: Double,
    val newEffectiveValue: Double,
    val userBaselineAtTime: Double,
    val aiAdjustmentAtTime: Double,
    val evidenceCategory: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isUserGenerated: Boolean = false
)

@Entity(tableName = "rejected_media")
data class RejectedMediaEntity(
    @PrimaryKey val id: String,
    val uriPath: String,
    val title: String,
    val mediaType: String,
    val reason: String,
    val compatibilityStatus: String,
    val containerFormat: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    @ColumnInfo(defaultValue = "NULL") val contentHash: String? = null,
    val timestampRejected: Long = System.currentTimeMillis()
)

@Entity(tableName = "creator_profiles")
data class CreatorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val platform: String,
    val affinityScore: Double = 0.0,
    val interactionCount: Int = 0,
    val lastInteractionTimestamp: Long = 0L,
    val topMoodTagsJson: String = ""
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playback_error_logs")
data class PlaybackErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaItemId: String? = null,
    val mediaUri: String? = null,
    val mediaTitle: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val playbackPositionMs: Long? = null,
    val playbackState: String? = null,
    val playWhenReady: Boolean? = null,
    val errorCode: Int? = null,
    val errorCodeName: String? = null,
    val errorMessage: String? = null,
    val exceptionClass: String? = null,
    val causeChain: String? = null,
    val stackTrace: String? = null,
    val rendererName: String? = null,
    val rendererIndex: Int? = null,
    val codecName: String? = null,
    val codecMimeType: String? = null,
    val deviceManufacturer: String? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val sdkInt: Int? = null,
    val appVersion: String? = null,
    val media3Version: String? = null,
    val networkState: String? = null,
    val isLocalFile: Boolean = true,
    val sessionId: String? = null,
    val occurrenceCount: Int = 1,
    val lastOccurrenceTimestamp: Long = System.currentTimeMillis(),
    val recoveryAttempted: Boolean = false,
    val recoverySuccessful: Boolean? = null,
    val diagnosticSummary: String? = null
)

@Entity(
    tableName = "conversion_jobs",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["status"])
    ]
)
data class ConversionJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val sourceUri: String,
    val fileName: String,
    val mediaTitle: String? = null,
    val sourceSize: Long = 0,
    val sourceDurationMs: Long = 0,
    val sourceVideoCodec: String? = null,
    val sourceAudioCodec: String? = null,
    val targetContainer: String = "MP4",
    val targetVideoCodec: String = "H.264/AVC",
    val targetAudioCodec: String = "AAC",
    val status: String = "QUEUED", // ConversionJobStatus name
    val progress: Int = 0,
    val attemptCount: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val startedTimestamp: Long? = null,
    val completedTimestamp: Long? = null,
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val outputPath: String? = null,
    val failureStage: String? = null, // ConversionStage name
    val errorMessage: String? = null,
    val realTimeFactor: Double = 0.0,
    val compressionRatio: Double = 0.0,
    val workRequestId: String? = null, // For WorkManager cancellation
    val finalMediaUri: String? = null,
    val finalMediaId: String? = null,
    val replacementStage: String? = null, // ReplacementStage name
    val cleanupStatus: String = "NOT_ELIGIBLE", // OriginalCleanupStatus name
    val cleanupEligibilityTimestamp: Long? = null,
    val cleanupStartedTimestamp: Long? = null,
    val cleanupCompletedTimestamp: Long? = null,
    val cleanupAttemptCount: Int = 0,
    val lastCleanupError: String? = null
)



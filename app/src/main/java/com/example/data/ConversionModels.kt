package com.example.data

import android.net.Uri

/**
 * Eligibility status for media conversion.
 */
enum class ConversionEligibility {
    CONVERTIBLE,    // Failure appears potentially solvable through transcoding
    NOT_RECOMMENDED, // Error does not appear appropriate for transcoding
    UNAVAILABLE     // Source URI cannot be accessed/analyzed
}

/**
 * Stages of the single-file conversion workflow.
 */
enum class ConversionStage {
    IDLE,
    PREPARING,
    CONVERTING,
    VALIDATING,
    TESTING_PLAYBACK,
    COMPLETE,
    FAILED
}

/**
 * Structured result of a conversion attempt.
 */
data class SingleFileConversionResult(
    val status: ConversionStatus,
    val sourceUri: Uri,
    val outputPath: String? = null,
    val sourceVideoCodec: String? = null,
    val sourceAudioCodec: String? = null,
    val targetVideoCodec: String? = "video/avc",
    val targetAudioCodec: String? = "audio/mp4a-latm",
    val sourceSize: Long = 0,
    val outputSize: Long = 0,
    val sourceDurationMs: Long = 0,
    val outputDurationMs: Long = 0,
    val elapsedMs: Long = 0,
    val realTimeFactor: Double = 0.0,
    val compressionRatio: Double = 0.0,
    val failureStage: ConversionStage = ConversionStage.IDLE,
    val errorMessage: String? = null,
    val exception: Throwable? = null
)

/**
 * Status types for structured conversion results.
 */
enum class ConversionResultStatus {
    SUCCESS,
    CONVERSION_FAILED,
    STRUCTURAL_VALIDATION_FAILED,
    PLAYBACK_VALIDATION_FAILED,
    SOURCE_UNAVAILABLE,
    NOT_RECOMMENDED,
    ENCODER_UNAVAILABLE
}

/**
 * Status of a persistent conversion job.
 */
enum class ConversionJobStatus {
    QUEUED,
    PREPARING,
    CONVERTING,
    VALIDATING,
    TESTING_PLAYBACK,
    COMPLETED,
    REPLACING,
    READY_FOR_ORIGINAL_CLEANUP,
    CLEANUP_IN_PROGRESS,
    CLEANUP_COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Status of the physical original file cleanup.
 */
enum class OriginalCleanupStatus {
    NOT_ELIGIBLE,
    WAITING_FOR_STABILITY,
    CLEANUP_ELIGIBLE,
    CLEANUP_IN_PROGRESS,
    VERIFYING_DELETION,
    CLEANUP_COMPLETED,
    CLEANUP_FAILED,
    CLEANUP_BLOCKED
}

object ConversionConstants {
    const val DEFAULT_CLEANUP_STABILITY_DAYS = 7
    const val PREF_AUTO_CLEANUP_ENABLED = "pref_auto_cleanup_enabled"
}

/**
 * Structured recommendation for media conversion.
 */
data class ConversionRecommendation(
    val eligibility: ConversionEligibility,
    val reason: String,
    val sourceContainer: String? = null,
    val sourceVideoCodec: String? = null,
    val sourceAudioCodec: String? = null,
    val targetContainer: String = "MP4",
    val targetVideoCodec: String = "H.264/AVC",
    val targetAudioCodec: String = "AAC",
    val explanation: String
)

/**
 * Represents a unique media file that is a candidate for conversion.
 */
data class ConversionCandidate(
    val mediaId: String,
    val sourceUri: Uri,
    val fileName: String,
    val mediaTitle: String?,
    val recommendation: ConversionRecommendation,
    val failureCount: Int,
    val lastFailureTimestamp: Long
)

/**
 * Summary of conversion eligibility across multiple errors.
 */
data class ConversionEligibilitySummary(
    val totalErrors: Int,
    val uniqueFiles: Int,
    val convertibleCount: Int,
    val notRecommendedCount: Int,
    val unavailableCount: Int,
    val candidates: List<ConversionCandidate>
)

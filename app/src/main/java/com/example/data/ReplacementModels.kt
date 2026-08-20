package com.example.data

/**
 * Stages of the media replacement workflow.
 */
enum class ReplacementStage {
    NOT_STARTED,
    PREPARING,
    HANDOFF_IN_PROGRESS,
    VERIFYING,
    RECONCILING_LIBRARY,
    READY_FOR_ORIGINAL_CLEANUP,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    NOT_SUPPORTED
}

/**
 * Result of a replacement attempt.
 */
data class ReplacementResult(
    val stage: ReplacementStage,
    val finalMediaUri: String? = null,
    val finalMediaId: String? = null,
    val errorMessage: String? = null,
    val cleanupEligibilityTimestamp: Long? = null
)

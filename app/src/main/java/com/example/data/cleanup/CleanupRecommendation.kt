package com.example.data.cleanup

/**
 * A recommendation for media review based on behavioral intelligence.
 */
data class CleanupRecommendation(
    val mediaId: String,
    val keepScore: Float,
    val confidenceScore: Float,
    val category: CleanupCategory,
    val reasons: List<CleanupReason>,
    val storageSize: Long,
    val exposureCount: Int = 0,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val explanation: String
)

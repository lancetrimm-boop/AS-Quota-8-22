package com.example.data.cleanup

import kotlin.math.min

/**
 * Transforms raw intelligence scores into actionable cleanup recommendations.
 */
object CleanupRecommendationEngine {

    /**
     * Generates a list of recommendations for a set of items.
     * Handles single-item categorization and cross-item duplicate detection.
     */
    fun generateRecommendations(
        results: List<KeepScoreResult>,
        itemMetadata: Map<String, CleanupItemMetadata>
    ): List<CleanupRecommendation> {
        val recommendations = mutableListOf<CleanupRecommendation>()
        
        // 1. Group by content hash to find redundancy
        val hashGroups = itemMetadata.values.filter { it.contentHash != null }.groupBy { it.contentHash!! }
        val duplicateIds = hashGroups.filter { it.value.size > 1 }.flatMap { group ->
            // Keep the one with the highest interaction or oldest date? 
            // For now, mark all as potential redundant candidates.
            group.value.map { it.mediaId }
        }.toSet()

        results.forEach { result ->
            val metadata = itemMetadata[result.mediaId] ?: return@forEach
            
            // Determine the primary recommendation category
            var category = result.category
            val reasons = result.reasons.toMutableList()
            
            // Cross-item logic for redundancy
            if (duplicateIds.contains(result.mediaId)) {
                category = CleanupCategory.REDUNDANT
                reasons.add(CleanupReason.DUPLICATE_CONTENT)
            }

            if (category != CleanupCategory.NONE) {
                recommendations.add(
                    CleanupRecommendation(
                        mediaId = result.mediaId,
                        keepScore = result.keepScore,
                        confidenceScore = calculateConfidence(category, result, metadata),
                        category = category,
                        reasons = reasons.distinct(),
                        storageSize = metadata.sizeBytes,
                        exposureCount = metadata.exposureCount,
                        explanation = generateExplanation(category, result, metadata)
                    )
                )
            }
        }
        
        return recommendations
    }

    private fun calculateConfidence(
        category: CleanupCategory,
        result: KeepScoreResult,
        metadata: CleanupItemMetadata
    ): Float {
        return when (category) {
            CleanupCategory.FORGOTTEN -> {
                // High confidence if seen many times and never touched
                val exposureFactor = min(1.0f, metadata.exposureCount / 50.0f)
                (0.7f + (exposureFactor * 0.3f)).coerceIn(0f, 1f)
            }
            CleanupCategory.SPACE_HOGS -> {
                // High confidence if very large and very low keep score
                val sizeFactor = min(1.0f, metadata.sizeBytes / (500 * 1024 * 1024f)) // Max at 500MB
                val valueFactor = 1.0f - result.keepScore
                ((sizeFactor + valueFactor) / 2.0f).coerceIn(0.6f, 1.0f)
            }
            CleanupCategory.REDUNDANT -> 1.0f // Exact hash match is certain
            CleanupCategory.NEVER_CONNECTED -> 0.65f // Subjective, so lower baseline confidence
            CleanupCategory.NONE -> 0f
        }
    }

    private fun generateExplanation(
        category: CleanupCategory,
        result: KeepScoreResult,
        metadata: CleanupItemMetadata
    ): String {
        return when (category) {
            CleanupCategory.FORGOTTEN -> {
                "Seen ${metadata.exposureCount} times but never opened"
            }
            CleanupCategory.NEVER_CONNECTED -> {
                if (result.reasons.contains(CleanupReason.LOW_TASTE_ALIGNMENT)) {
                    "Low alignment with your preferences and no engagement"
                } else {
                    "Minimal interest shown over multiple browsing sessions"
                }
            }
            CleanupCategory.SPACE_HOGS -> {
                val sizeMb = metadata.sizeBytes / (1024 * 1024)
                if (metadata.mediaType == "VIDEO") {
                    "${sizeMb}MB video with low interaction"
                } else {
                    "Large ${sizeMb}MB file taking up significant space"
                }
            }
            CleanupCategory.REDUNDANT -> "Exact duplicate file detected"
            CleanupCategory.NONE -> ""
        }
    }

    /**
     * Calculates the total potential storage recovery for a list of recommendations.
     */
    fun calculatePotentialRecovery(recommendations: List<CleanupRecommendation>): Long {
        return recommendations.sumOf { it.storageSize }
    }
}

/**
 * Minimal metadata required for recommendation logic.
 */
data class CleanupItemMetadata(
    val mediaId: String,
    val sizeBytes: Long,
    val exposureCount: Int,
    val viewCount: Int,
    val mediaType: String,
    val contentHash: String?,
    val isFavorite: Boolean
)

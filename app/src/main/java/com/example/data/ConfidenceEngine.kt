package com.example.data

import kotlin.math.min

/**
 * Engine for calculating system-level confidence and library coverage signals.
 */
object ConfidenceEngine {

    /**
     * Aggregates interaction data into a SystemDiscoveryState.
     */
    fun calculateDiscoveryState(
        allMedia: List<MediaItem>,
        stats: IntelligenceStats
    ): SystemDiscoveryState {
        if (allMedia.isEmpty()) return SystemDiscoveryState()

        val totalCount = allMedia.size
        val viewedCount = allMedia.count { it.viewCount > 0 }
        val ratedCount = allMedia.count { it.rating > 0 }
        
        val libraryCoverage = viewedCount.toFloat() / totalCount
        val ratingCoverage = ratedCount.toFloat() / totalCount

        // Global confidence is a blend of explicit ratings, pairwise comparisons, and personalization score
        // We use a logarithmic scale for comparisons to avoid linear bias
        val comparisonFactor = min(1.0f, (stats.totalComparisons.toFloat() / 100f))
        val globalConfidence = (stats.personalizationScore.toFloat() / 100f * 0.4f) + 
                               (ratingCoverage * 0.4f) + 
                               (comparisonFactor * 0.2f)

        // Repetition rate: percentage of recently exposed items that have high exposure counts
        // (Simplified for this layer: average exposure of items seen more than 5 times)
        val highExposureCount = allMedia.count { it.exposureCount > 5 }
        val repetitionRate = (highExposureCount.toFloat() / totalCount).coerceIn(0f, 1f)

        return SystemDiscoveryState(
            globalConfidence = globalConfidence.coerceIn(0f, 1f),
            libraryCoverage = libraryCoverage,
            ratingCoverage = ratingCoverage,
            repetitionRate = repetitionRate
        )
    }
}

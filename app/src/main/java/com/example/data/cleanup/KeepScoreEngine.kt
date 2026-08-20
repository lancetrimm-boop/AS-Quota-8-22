package com.example.data.cleanup

import kotlin.math.max
import kotlin.math.min

data class KeepScoreInput(
    val mediaId: String,
    val fileSize: Long,
    val dateAdded: Long,
    val exposureCount: Int,
    val lastExposedTimestamp: Long?,
    val viewCount: Int,
    val playCount: Int,
    val averageWatchDuration: Float, // in seconds
    val completionPercentage: Float, // 0.0 to 1.0
    val skipCount: Int,
    val rating: Float, // 0.0 to 5.0
    val isFavorite: Boolean,
    val tasteAlignmentScore: Float, // 0.0 to 1.0
    val contentHash: String?
)

data class KeepScoreResult(
    val mediaId: String,
    val keepScore: Float,
    val confidenceScore: Float,
    val reasons: List<CleanupReason>,
    val category: CleanupCategory
)

object KeepScoreEngine {

    fun calculateScore(input: KeepScoreInput): KeepScoreResult {
        val reasons = mutableListOf<CleanupReason>()
        
        // --- 1. Base Score Calculation (Weighted) ---
        
        // Taste Alignment: 30%
        val tasteWeight = 0.30f
        val tasteComponent = input.tasteAlignmentScore * tasteWeight
        
        // Engagement: 25% (Views, Plays, Duration)
        val engagementWeight = 0.25f
        val viewScore = min(1.0f, input.viewCount / 5.0f)
        val completionScore = input.completionPercentage
        val engagementComponent = ((viewScore + completionScore) / 2.0f) * engagementWeight
        
        // Explicit Preference: 25% (Rating, Favorite)
        val preferenceWeight = 0.25f
        val ratingScore = input.rating / 5.0f
        val favoriteScore = if (input.isFavorite) 1.0f else 0.0f
        val preferenceComponent = ((ratingScore + favoriteScore) / 2.0f) * preferenceWeight
        
        // Recency: 10%
        val recencyWeight = 0.10f
        val ageMs = System.currentTimeMillis() - input.dateAdded
        val sixMonthsMs = 180L * 24 * 60 * 60 * 1000L
        val recencyScore = max(0.0f, 1.0f - (ageMs.toFloat() / sixMonthsMs))
        val recencyComponent = recencyScore * recencyWeight
        
        // Rarity: 10% (Content unique)
        val rarityWeight = 0.10f
        val rarityScore = 1.0f // Logic for duplicates would reduce this later
        val rarityComponent = rarityScore * rarityWeight
        
        var baseScore = tasteComponent + engagementComponent + preferenceComponent + recencyComponent + rarityComponent
        
        // --- 2. Positive Bonuses ---
        
        if (input.tasteAlignmentScore > 0.80f) {
            baseScore += 0.15f
            reasons.add(CleanupReason.HIGH_TASTE_MATCH)
        }
        
        if (input.completionPercentage > 0.90f || (input.playCount > 0 && input.averageWatchDuration > 30f)) {
            baseScore += 0.10f
            reasons.add(CleanupReason.HIGH_RETENTION)
        }
        
        // --- 3. Negative Penalties ---
        
        // Ghosting
        if (input.exposureCount > 20 && input.viewCount == 0) {
            baseScore -= 0.40f
            reasons.add(CleanupReason.HIGH_EXPOSURE_NO_ENGAGEMENT)
        }
        
        // Instant Skip
        if (input.averageWatchDuration < 2.0f && input.skipCount >= 3) {
            baseScore -= 0.30f
            reasons.add(CleanupReason.REPEATED_SKIP)
        }
        
        // Low Rating
        if (input.rating > 0 && input.rating <= 2.0f) {
            baseScore -= 0.25f
            reasons.add(CleanupReason.LOW_USER_RATING)
        }
        
        // Staleness
        val oneYearMs = 365L * 24 * 60 * 60 * 1000L
        val lastInteraction = max(input.dateAdded, input.lastExposedTimestamp ?: 0L)
        val idleTimeMs = System.currentTimeMillis() - lastInteraction
        if (ageMs > oneYearMs && idleTimeMs > (sixMonthsMs)) {
            baseScore -= 0.20f
            reasons.add(CleanupReason.STALE_MEDIA)
        }
        
        // Low Taste Match penalty
        if (input.tasteAlignmentScore < 0.20f) {
            baseScore -= 0.15f
            reasons.add(CleanupReason.LOW_TASTE_ALIGNMENT)
        }

        // --- 4. Constraints & Clamping ---
        
        var finalScore = baseScore.coerceIn(0.0f, 1.0f)
        
        // Favorite Protection
        if (input.isFavorite) {
            finalScore = max(0.85f, finalScore)
            reasons.add(CleanupReason.FAVORITE_PROTECTED)
        }
        
        // --- 5. Categorization ---
        
        val category = when {
            input.isFavorite -> CleanupCategory.NONE
            reasons.contains(CleanupReason.HIGH_EXPOSURE_NO_ENGAGEMENT) -> CleanupCategory.FORGOTTEN
            reasons.contains(CleanupReason.REPEATED_SKIP) || reasons.contains(CleanupReason.LOW_TASTE_ALIGNMENT) -> CleanupCategory.NEVER_CONNECTED
            input.fileSize > 100 * 1024 * 1024 && finalScore < 0.40f -> {
                reasons.add(CleanupReason.LARGE_FILE_SIZE)
                CleanupCategory.SPACE_HOGS
            }
            else -> CleanupCategory.NONE
        }
        
        // Confidence Score: Higher if we have more interaction history
        val interactionConfidence = min(1.0f, (input.exposureCount + input.viewCount * 2) / 30.0f)
        
        return KeepScoreResult(
            mediaId = input.mediaId,
            keepScore = finalScore,
            confidenceScore = interactionConfidence,
            reasons = reasons.distinct(),
            category = category
        )
    }
}

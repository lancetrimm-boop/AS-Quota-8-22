package com.example.data.intelligence

/**
 * Immutable snapshot of the user's current aesthetic and behavioral preferences.
 */
data class TasteProfileSnapshot(
    val schemaVersion: Int = 1,
    val dimensions: Map<String, Double>,
    val dimensionConfidence: Map<String, Double>,
    val topTraits: List<String>,
    val explorationPropensity: Double,
    val description: String = "",
    val lastUpdated: Long,
    val tasteClusters: List<TasteClusterEvidence> = emptyList()
)

/**
 * Immutable evidence mapping a taste category to a representative local media item.
 */
data class TasteClusterEvidence(
    val categoryId: String,
    val title: String,
    val description: String,
    val strengthScore: Double,
    val strengthLabel: String,
    val confidenceScore: Double,
    val confidenceLabel: String,
    val contributingTraits: List<String>,
    val representativeMediaId: String? = null,
    val representativeMediaThumbnailUrl: String? = null,
    val isVideo: Boolean = false
)

/**
 * Immutable snapshot of user interaction patterns and engagement quality.
 */
data class EngagementSnapshot(
    val schemaVersion: Int = 1,
    val completionRate: Double,
    val favoriteDensity: Double,
    val averageSkipVelocity: Double, // Skips per minute of viewing
    val microMomentIntensity: Double,
    val sessionDurationAverageMinutes: Double,
    val mostActiveHour: Int // 0-23
)

/**
 * Immutable snapshot explaining why a specific piece of media was prioritized.
 */
data class RecommendationInsightSnapshot(
    val schemaVersion: Int = 1,
    val mediaId: String,
    val overallMatchScore: Double,
    val contributingFactors: List<InsightFactor>,
    val provenance: String // Description of source data used
)

/**
 * Supporting model for recommendation insights.
 */
data class InsightFactor(
    val label: String, // e.g., "Visual Vibrancy"
    val weight: Double, // 0.0 to 1.0
    val impactDirection: Int // 1 for positive, -1 for negative, 0 for neutral
)

/**
 * Immutable snapshot of the model's calibration status and data richness.
 * Distinguishes between signal quality (Confidence) and signal quantity (Coverage).
 */
data class AuraMaturitySnapshot(
    val schemaVersion: Int = 1,
    val personalizationConfidence: Double, // Signal quality: How well Aura understands the user (0.0 to 1.0)
    val dataCoverage: Double,             // Signal quantity: How much of the library has been learned (0.0 to 1.0)
    val totalInteractionsAnalyzed: Int,
    val pairwiseComparisonsCompleted: Int,
    val itemsInLearningPool: Int,
    val calibrationStatus: CalibrationStatus
)

/**
 * High-level calibration states for user feedback.
 */
enum class CalibrationStatus {
    INITIALIZING,    // Insufficient data (< 10 comparisons)
    LEARNING,        // Active learning phase
    STABILIZING,     // High confidence reached
    MATURE           // Deep personalization achieved
}

/**
 * Master container for all local user-facing intelligence snapshots.
 * Framed as the "Intelligence Dashboard Report".
 */
data class IntelligenceSnapshotReport(
    val schemaVersion: Int = 1,
    val tasteProfile: TasteProfileSnapshot,
    val engagement: EngagementSnapshot,
    val maturity: AuraMaturitySnapshot,
    val generatedAt: Long = System.currentTimeMillis()
)

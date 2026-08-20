package com.example.data

import com.squareup.moshi.JsonClass

/**
 * High-level mode for global discovery balance.
 */
enum class DiscoveryMode {
    PERSONALIZED, // Exploitation-heavy
    BALANCED,     // Mixed
    EXPLORATORY   // Exploration-heavy
}

/**
 * Temporary user intent that can override global policy for a session.
 */
enum class IntentFocus {
    DEFAULT,
    SIMILAR_TO_FAVORITES, // Force high exploitation
    SURPRISE_ME,          // Force high novelty
    DEEP_DISCOVERY,       // Force high information gain / exploration
    UNSEEN_ONLY,          // Force extreme penalty on viewed content
    COMPLETELY_DIFFERENT, // High novelty + High exploration
    HIDDEN_COMPATIBILITY, // High uncertainty + High predicted enjoyment
    TASTE_EXPANSION       // High information gain + learning value
}

/**
 * Persistent global discovery preference.
 */
@JsonClass(generateAdapter = true)
data class DiscoveryPolicy(
    val mode: DiscoveryMode = DiscoveryMode.BALANCED
)

/**
 * Local session state for user intent.
 */
data class UserIntent(
    val modeOverride: DiscoveryMode? = null,
    val focus: IntentFocus = IntentFocus.DEFAULT
)

/**
 * Surface-specific recommendation goals.
 */
enum class RecommendationObjective {
    GENERAL_DISCOVERY,
    RANKING_REFINEMENT, // e.g., Pairwise
    CHILL_EXPLOITATION, // e.g., Favorites rail
    NOVELTY_INJECTION,  // e.g., Fresh for You rail
    LIBRARY_INTELLIGENT_DISCOVERY, // Optimized for a high-volume, list-based surface
    WILDCARD_DISCOVERY, // Intentional surprise
    DEEP_DISCOVERY // High information gain / Learning focus
}

/**
 * The final resolved strategy consumed by RecommendationEngine.
 */
data class RecommendationStrategy(
    val exploitationWeight: Float,
    val explorationWeight: Float,
    val noveltyWeight: Float,
    val diversityWeight: Float,
    val familiarityPenalty: Float
)

/**
 * System-level assessment of knowledge depth and library coverage.
 */
data class SystemDiscoveryState(
    val globalConfidence: Float = 0f,    // 0.0 to 1.0
    val libraryCoverage: Float = 0f,     // % of library viewed
    val ratingCoverage: Float = 0f,      // % of library rated
    val dimensionCoverage: Map<String, Float> = emptyMap(), // Coverage per DNA dimension
    val repetitionRate: Float = 0f,      // Recent repetition detection
    val explorationSuccessRate: Float = 0.5f // Success of recent exploratory items
)

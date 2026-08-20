package com.example.data.contribution

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Global Aura Intelligence — Data Contribution Contracts (Phase 1)
 *
 * Strongly typed, Moshi-serializable, versioned payload data models for anonymized
 * population-level intelligence aggregation.
 */

/**
 * Complete TasteDNA vector snapshot.
 * Restored to include all 24 aesthetic dimensions identified in the audit + 4 behavioral preferences.
 */
@JsonClass(generateAdapter = true)
data class ElemTasteVectorSnapshotV1(
    @Json(name = "schema_version") val schemaVersion: String = "1.0",
    @Json(name = "event_type") val eventType: String = "ELEM_TASTE_VECTOR_SNAPSHOT_V1",
    @Json(name = "time_window_hour") val timeWindowHour: Long,

    // 24 Visual & Aesthetic Dimensions
    @Json(name = "vibrancy") val vibrancy: Double,
    @Json(name = "contrast") val contrast: Double,
    @Json(name = "sharpness") val sharpness: Double,
    @Json(name = "symmetry") val symmetry: Double,
    @Json(name = "complexity") val complexity: Double,
    @Json(name = "naturalism") val naturalism: Double,
    @Json(name = "novelty") val novelty: Double,
    @Json(name = "lighting") val lighting: Double,
    @Json(name = "color_temperature") val colorTemperature: Double,
    @Json(name = "texture") val texture: Double,
    @Json(name = "motion") val motion: Double,
    @Json(name = "dynamic_range") val dynamicRange: Double,
    @Json(name = "framing") val framing: Double,
    @Json(name = "depth") val depth: Double,
    @Json(name = "warmth") val warmth: Double,
    @Json(name = "saturation") val saturation: Double,
    @Json(name = "elegance") val elegance: Double,
    @Json(name = "minimalism") val minimalism: Double,
    @Json(name = "grain") val grain: Double,
    @Json(name = "focus") val focus: Double,
    @Json(name = "density") val density: Double,
    @Json(name = "rhythm") val rhythm: Double,
    @Json(name = "mood") val mood: Double,
    @Json(name = "harmony") val harmony: Double,

    // Behavioral Preference Dimensions (4)
    @Json(name = "skip_sensitivity") val skipSensitivity: Double,
    @Json(name = "exploration_propensity") val explorationPropensity: Double,
    @Json(name = "retention_focus") val retentionFocus: Double,
    @Json(name = "favorite_significance") val favoriteSignificance: Double
)

/**
 * Pairwise Elo calibration delta event.
 */
@JsonClass(generateAdapter = true)
data class ElemPairwiseDeltaV1(
    @Json(name = "schema_version") val schemaVersion: String = "1.0",
    @Json(name = "event_type") val eventType: String = "ELEM_PAIRWISE_DELTA_V1",
    @Json(name = "time_window_hour") val timeWindowHour: Long,
    @Json(name = "expected_score") val expectedScoreQuantized: Double,
    @Json(name = "actual_outcome") val actualOutcome: Double,
    @Json(name = "elo_delta") val eloDeltaQuantized: Double,
    @Json(name = "k_factor") val kFactorQuantized: Double,
    @Json(name = "outcome_category") val outcomeCategory: String
)

/**
 * AI Skip behavior and calibration event.
 */
@JsonClass(generateAdapter = true)
data class ElemSkipCalibrationV1(
    @Json(name = "schema_version") val schemaVersion: String = "1.0",
    @Json(name = "event_type") val eventType: String = "ELEM_SKIP_CALIBRATION_V1",
    @Json(name = "time_window_hour") val timeWindowHour: Long,
    @Json(name = "skip_type") val skipType: String,
    @Json(name = "direction") val direction: String,
    @Json(name = "relative_position") val relativePositionQuantized: Double,
    @Json(name = "relative_jump_distance") val relativeJumpDistanceQuantized: Double,
    @Json(name = "watched_destination") val watchedDestination: Boolean,
    @Json(name = "repeated_skip") val repeatedSkip: Boolean
)

/**
 * Recommendation and discovery feedback event.
 */
@JsonClass(generateAdapter = true)
data class ElemRecommendationFeedbackV1(
    @Json(name = "schema_version") val schemaVersion: String = "1.0",
    @Json(name = "event_type") val eventType: String = "ELEM_RECOMMENDATION_FEEDBACK_V1",
    @Json(name = "time_window_hour") val timeWindowHour: Long,
    @Json(name = "interaction_type") val interactionType: String,
    @Json(name = "feedback_category") val feedbackCategory: String,
    @Json(name = "score") val scoreQuantized: Double?
)

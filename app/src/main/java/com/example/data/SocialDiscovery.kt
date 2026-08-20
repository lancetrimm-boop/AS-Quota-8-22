package com.example.data

import com.squareup.moshi.JsonClass

/**
 * Represents a creator's presence and the user's affinity towards them.
 */
@JsonClass(generateAdapter = true)
data class CreatorProfile(
    val id: String,
    val name: String,
    val platform: String,
    val affinityScore: Double = 0.0, // 0.0 to 1.0
    val interactionCount: Int = 0,
    val lastInteractionTimestamp: Long = 0L,
    val topMoodTags: List<String> = emptyList()
)

/**
 * Captures an emerging preference event when Aura notices patterns outside established DNA.
 */
data class EmergingPreferenceEvent(
    val type: String, // "CREATOR", "STYLE", "SEMANTIC"
    val identifier: String,
    val evidenceCount: Int,
    val avgPredictedMatch: Double,
    val avgActualEngagement: Double,
    val status: String = "EMERGING" // "EMERGING", "ESTABLISHED", "DISCARDED"
)

/**
 * System-level state for social discovery integration.
 */
data class SocialDiscoveryState(
    val knownCreatorsCount: Int = 0,
    val emergingPreferences: List<EmergingPreferenceEvent> = emptyList(),
    val averageCreatorAffinity: Double = 0.0,
    val recentExplorationSuccess: Double = 0.5
)

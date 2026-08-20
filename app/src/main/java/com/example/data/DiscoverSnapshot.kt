package com.example.data

import java.util.UUID

/**
 * Immutable snapshot of the Discover experience for a specific session.
 * Ensures the UI remains stable while background learning continues.
 */
data class DiscoverSnapshot(
    val sessionId: String = UUID.randomUUID().toString(),
    val generationId: Long = System.currentTimeMillis(),
    val obsessions: List<ObsessionRecommendation>,
    val systemState: SystemDiscoveryState,
    val seenIds: Set<String>
)

package com.example.data.social

import com.example.data.MediaItem

/**
 * Data transfer object representing raw external video metadata returned by platform search (e.g., YouTube).
 * Strictly ephemeral; NEVER persisted to Room, SQLCipher, or MediaStore.
 */
data class SocialCandidateDto(
    val id: String,
    val title: String,
    val description: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val externalUrl: String = "",
    val originalRank: Int = 1,
    val publishedAt: String = "",
    val rawTags: List<String> = emptyList()
)

/**
 * Detailed diagnostic model for an external candidate evaluated through Aura's on-device Taste Intelligence.
 */
data class AuraSocialCandidate(
    val candidate: SocialCandidateDto,
    val ephemeralMediaItem: MediaItem,
    val normalizedTokens: List<String>,
    val traitAdjustments: Map<String, Double>,
    val alignmentScore: Double, // 0.0 to 1.0 (True trait alignment percentage / 100)
    val tasteAlignmentPercent: Int, // 0 to 100%
    val exploitationScore: Float, // ExplorationEngine raw exploitation score (0.0 to 1.0)
    val policyScore: Float, // ExplorationEngine final strategy policy score
    val matchedTraits: List<MatchedTraitDetail>,
    val originalRank: Int,
    val auraRank: Int,
    val rankDelta: Int // originalRank - auraRank (e.g., +13 = elevated from #14 to #1)
)

/**
 * Explanation detail for an individual Taste DNA dimension matched by this candidate.
 */
data class MatchedTraitDetail(
    val dimension: String,
    val displayName: String,
    val itemPresence: Double, // -1.0 to +1.0
    val userPreference: Double, // 0.0 to 1.0
    val alignment: Double // 0.0 to 1.0
)

/**
 * Summary metrics of a complete Taste Discovery benchmark run comparing platform order vs Aura order.
 */
data class SocialDiscoveryBenchmarkResult(
    val query: String,
    val totalFetched: Int,
    val totalScored: Int,
    val rankChangedCount: Int,
    val rankChangedPercentage: Double,
    val avgAbsoluteRankMovement: Double,
    val top3OverlapCount: Int,
    val largestPositiveDelta: Int,
    val largestPositiveCandidate: AuraSocialCandidate?,
    val largestNegativeDelta: Int,
    val largestNegativeCandidate: AuraSocialCandidate?,
    val rankCorrelation: Double?, // Spearman's rank correlation coefficient (-1.0 to 1.0)
    val originalPlatformCandidates: List<AuraSocialCandidate>,
    val auraRankedCandidates: List<AuraSocialCandidate>,
    val executionTimestamp: Long = System.currentTimeMillis()
)

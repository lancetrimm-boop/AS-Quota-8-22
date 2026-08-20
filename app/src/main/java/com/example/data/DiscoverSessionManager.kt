package com.example.data

import com.example.compatibility.AuraMediaCompatibilityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Domain-level orchestrator for the redesigned Discover session.
 * Manages Layer 1 (Cluster generation) and Layer 2 (Batch realization).
 */
class DiscoverSessionManager {
    private val sessionSeenIds = mutableSetOf<String>()
    private val sessionSeenContentIds = mutableSetOf<String>()
    private var currentSessionId: String = java.util.UUID.randomUUID().toString()

    /**
     * LAYER 1: Generates the vertically scrolling obsession clusters and wraps them in a snapshot.
     */
    suspend fun generateSnapshot(
        allMedia: List<MediaItem>,
        tasteDNA: TasteDNA,
        profile: TasteDNA.PreferenceProfile,
        policy: DiscoveryPolicy,
        stats: IntelligenceStats,
        creatorProfiles: Map<String, CreatorProfile>,
        forceNewSession: Boolean = false
    ): DiscoverSnapshot = withContext(Dispatchers.Default) {
        if (forceNewSession) {
            currentSessionId = java.util.UUID.randomUUID().toString()
            sessionSeenIds.clear()
            sessionSeenContentIds.clear()
        }

        val obsessions = RecommendationEngine.computeObsessions(
            allMedia, tasteDNA, profile, policy, stats, creatorProfiles
        )

        val systemState = ConfidenceEngine.calculateDiscoveryState(allMedia, stats)

        // Attach explanations and pre-mark previews as seen
        val explainedObsessions = obsessions.map { obsession ->
            val mainItem = obsession.previewItems.firstOrNull()
            val strategy = resolveStrategy(obsession.strategy, policy, systemState, tasteDNA, profile)
            val explanation = if (mainItem != null) {
                RecommendationExplanationGenerator.generate(mainItem, tasteDNA, stats, creatorProfiles, strategy)
            } else null
            
            obsession.copy(explanation = explanation)
        }

        explainedObsessions.forEach { obsession ->
            obsession.previewItems.forEach { markUsed(it) }
        }

        DiscoverSnapshot(
            sessionId = currentSessionId,
            obsessions = explainedObsessions,
            systemState = systemState,
            seenIds = sessionSeenIds.toSet()
        )
    }

    /**
     * LAYER 2: Realizes a finite batch of items for a specific obsession.
     */
    suspend fun realizeBatch(
        obsession: ObsessionRecommendation,
        allMedia: List<MediaItem>,
        tasteDNA: TasteDNA,
        profile: TasteDNA.PreferenceProfile,
        policy: DiscoveryPolicy,
        stats: IntelligenceStats,
        creatorProfiles: Map<String, CreatorProfile>,
        existingItems: List<MediaItem> = emptyList()
    ): ObsessionContentBatch = withContext(Dispatchers.Default) {
        val playableMedia = allMedia.filter {
            it.itemCount == null && com.example.compatibility.AuraMediaCompatibilityEngine.isEligibleForImport(it.compatibilityStatus)
        }

        val systemState = ConfidenceEngine.calculateDiscoveryState(allMedia, stats)
        val resolvedStrategy = resolveStrategy(obsession.strategy, policy, systemState, tasteDNA, profile)

        // Ensure preview items from the feed are included at the start of the first batch
        // to prevent the "skipping first item" / "starts on next item" bug.
        val isFirstBatch = existingItems.isEmpty()
        val baseItems = if (isFirstBatch) {
            obsession.previewItems.map { item ->
                val reason = item.selectionReason
                if (reason == null || !reason.contains("% Match")) {
                    val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles)
                    val matchPercent = (evidence.exploitationScore * 100).toInt().coerceIn(10, 99)
                    item.copy(selectionReason = "$matchPercent% Match")
                } else item
            }
        } else existingItems

        val items = playableMedia
            .filter { it.id !in sessionSeenIds && !isContentSeen(it) && it.id !in baseItems.map { it.id } }
            .map { item ->
                val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles)
                val score = ExplorationEngine.calculatePolicyScore(evidence, resolvedStrategy)
                
                // Annotate with match percentage from exploitation score
                val matchPercent = (evidence.exploitationScore * 100).toInt().coerceIn(10, 99)
                item.copy(selectionReason = "$matchPercent% Match") to score
            }
            .sortedByDescending { it.second }
            .take(12) // Smaller curated initial detail batch (Layer 2)
            .map { it.first }

        items.forEach { markUsed(it) }

        val combinedItems = baseItems + items
        
        // Generate explanations for the new items in the batch using the resolved strategy
        val batchExplanations = mutableMapOf<String, RecommendationExplanation>()
        
        // If first batch, add the hero explanation if available
        if (isFirstBatch && obsession.explanation != null && obsession.previewItems.isNotEmpty()) {
            batchExplanations[obsession.previewItems.first().id] = obsession.explanation
        }

        items.forEach { item ->
            val exp = RecommendationExplanationGenerator.generate(item, tasteDNA, stats, creatorProfiles, resolvedStrategy)
            if (exp != null) batchExplanations[item.id] = exp
        }

        ObsessionContentBatch(
            obsessionId = obsession.id,
            items = combinedItems,
            canExpand = items.size >= 12,
            batchIndex = if (isFirstBatch) 0 else combinedItems.size / 12,
            explanations = batchExplanations
        )
    }

    private fun resolveStrategy(
        strategy: ObsessionStrategy,
        policy: DiscoveryPolicy,
        systemState: SystemDiscoveryState,
        tasteDNA: TasteDNA,
        profile: TasteDNA.PreferenceProfile
    ): RecommendationStrategy {
        return when (strategy) {
            is ObsessionStrategy.Hero -> DiscoveryPolicyManager.resolveStrategy(policy, UserIntent(), RecommendationObjective.GENERAL_DISCOVERY, systemState, tasteDNA, profile)
            is ObsessionStrategy.FreshArrivals -> DiscoveryPolicyManager.resolveStrategy(policy, UserIntent(), RecommendationObjective.NOVELTY_INJECTION, systemState, tasteDNA, profile)
            is ObsessionStrategy.FavoriteRemix -> DiscoveryPolicyManager.resolveStrategy(policy, UserIntent(), RecommendationObjective.CHILL_EXPLOITATION, systemState, tasteDNA, profile)
            is ObsessionStrategy.HiddenGems -> DiscoveryPolicyManager.resolveStrategy(policy, UserIntent(), RecommendationObjective.GENERAL_DISCOVERY, systemState, tasteDNA, profile)
            is ObsessionStrategy.NoveltyPulse -> DiscoveryPolicyManager.resolveStrategy(policy, UserIntent(focus = IntentFocus.SURPRISE_ME), RecommendationObjective.GENERAL_DISCOVERY, systemState, tasteDNA, profile)
            else -> DiscoveryPolicyManager.resolveStrategy(policy, UserIntent(), RecommendationObjective.GENERAL_DISCOVERY, systemState, tasteDNA, profile)
        }
    }

    private fun markUsed(item: MediaItem) {
        sessionSeenIds.add(item.id)
        val contentId = item.parentContentId ?: item.contentHash ?: item.id
        sessionSeenContentIds.add(contentId)
    }

    private fun isContentSeen(item: MediaItem): Boolean {
        val contentId = item.parentContentId ?: item.contentHash ?: item.id
        return sessionSeenContentIds.contains(contentId)
    }
}

data class ObsessionContentBatch(
    val obsessionId: String,
    val items: List<MediaItem>,
    val canExpand: Boolean,
    val batchIndex: Int = 0,
    val explanations: Map<String, RecommendationExplanation> = emptyMap()
)


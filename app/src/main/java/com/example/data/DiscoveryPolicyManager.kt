package com.example.data

/**
 * Logic engine for resolving global policies, temporary intents, and surface objectives
 * into concrete recommendation weights.
 */
object DiscoveryPolicyManager {

    /**
     * Resolves the current recommendation strategy based on the multi-layer hierarchy.
     */
    fun resolveStrategy(
        policy: DiscoveryPolicy,
        intent: UserIntent = UserIntent(),
        objective: RecommendationObjective = RecommendationObjective.GENERAL_DISCOVERY,
        systemState: SystemDiscoveryState = SystemDiscoveryState(),
        tasteDNA: TasteDNA = TasteDNA(),
        profile: TasteDNA.PreferenceProfile = TasteDNA.PreferenceProfile()
    ): RecommendationStrategy {
        
        // 1. Determine Effective Mode
        val activeMode = intent.modeOverride ?: policy.mode

        // 2. Base weights from Mode
        var exploit: Float
        var explore: Float
        var novelty: Float
        var diversity: Float
        var penalty: Float

        when (activeMode) {
            DiscoveryMode.PERSONALIZED -> {
                exploit = 0.8f; explore = 0.1f; novelty = 0.1f; diversity = 0.2f; penalty = 0.1f
            }
            DiscoveryMode.BALANCED -> {
                exploit = 0.5f; explore = 0.3f; novelty = 0.2f; diversity = 0.4f; penalty = 0.3f
            }
            DiscoveryMode.EXPLORATORY -> {
                exploit = 0.2f; explore = 0.6f; novelty = 0.4f; diversity = 0.6f; penalty = 0.5f
            }
        }

        // Apply Manual Propensities from Taste DNA
        // 0.5 is neutral baseline.
        
        // 1. Exploration Propensity
        val exploreMultiplier = (tasteDNA.effectiveExploration.toFloat() * 2f).coerceIn(0.1f, 3.0f)
        explore *= exploreMultiplier

        // 2. Retention Focus (affects familiarity penalty)
        val retentionMultiplier = (tasteDNA.effectiveRetention.toFloat() * 2f).coerceIn(0.1f, 3.0f)
        penalty *= retentionMultiplier

        // 3. Favorite Significance (affects exploitation weight)
        val favoriteMultiplier = (tasteDNA.effectiveFavSignificance.toFloat() * 2f).coerceIn(0.5f, 2.5f)
        exploit *= favoriteMultiplier

        // 4. Modulate weights using user's Preference Profile (Fine-tuning the mode)
        // Default profile values: All 0.50 (Normalized sum = 2.0)
        // We shift multipliers so that 0.5 baseline = ~1.0x multiplier
        
        val exploitMult = (profile.contentSimilarityWeight.toFloat() / 0.5f).coerceIn(0.5f, 2.0f)
        val exploreMult = (profile.diversityWeight.toFloat() / 0.5f).coerceIn(0.5f, 2.0f)
        val noveltyMult = (profile.noveltyWeight.toFloat() / 0.5f).coerceIn(0.5f, 2.0f)

        exploit *= exploitMult
        explore *= exploreMult
        novelty *= noveltyMult

        // 5. Apply Intent Overrides (Temporary session focus)
        when (intent.focus) {
            IntentFocus.SIMILAR_TO_FAVORITES -> { exploit += 0.4f; explore -= 0.2f; novelty -= 0.1f }
            IntentFocus.SURPRISE_ME -> { novelty += 0.5f; exploit -= 0.2f }
            IntentFocus.DEEP_DISCOVERY -> { explore += 0.5f; exploit -= 0.3f }
            IntentFocus.UNSEEN_ONLY -> { penalty += 1.0f; novelty += 0.3f }
            IntentFocus.COMPLETELY_DIFFERENT -> {
                novelty += 0.6f
                explore += 0.4f
                exploit -= 0.4f
            }
            IntentFocus.HIDDEN_COMPATIBILITY -> {
                explore += 0.6f // High uncertainty
                exploit += 0.4f // But high predicted alignment
                novelty -= 0.2f
            }
            IntentFocus.TASTE_EXPANSION -> {
                explore += 0.4f
                diversity += 0.6f
                exploit -= 0.2f
            }
            IntentFocus.DEFAULT -> {}
        }

        // 4. Adaptive System Logic (Bounded adjustments based on system state)
        // Adjust explore weight based on global confidence (max adjustment 0.3)
        if (systemState.globalConfidence < 0.4) {
            val confidenceGap = (0.4f - systemState.globalConfidence).coerceAtMost(0.3f)
            explore += confidenceGap
            exploit -= (confidenceGap / 2f)
        }

        // Adjust novelty based on library coverage
        if (systemState.libraryCoverage < 0.2) {
            novelty += 0.2f // Boost novelty if user hasn't seen much of their library
        }

        // Adjust diversity/penalty based on repetition rate
        if (systemState.repetitionRate > 0.5) {
            diversity += 0.3f
            penalty += 0.4f
        }

        // 5. Apply Surface Objective (e.g., Pairwise vs Discover rail)
        when (objective) {
            RecommendationObjective.RANKING_REFINEMENT -> { explore += 0.3f }
            RecommendationObjective.CHILL_EXPLOITATION -> { exploit += 0.2f; explore -= 0.2f }
            RecommendationObjective.NOVELTY_INJECTION -> { novelty += 0.3f }
            RecommendationObjective.LIBRARY_INTELLIGENT_DISCOVERY -> {
                exploit += 0.1f
                explore += 0.1f
            }
            RecommendationObjective.WILDCARD_DISCOVERY -> {
                novelty += 0.8f
                diversity += 0.5f
                penalty += 0.4f // Stronger penalty for recent exposure
                exploit -= 0.2f
            }
            RecommendationObjective.DEEP_DISCOVERY -> {
                explore += 0.8f
                penalty += 0.2f
                exploit -= 0.3f
            }
            RecommendationObjective.GENERAL_DISCOVERY -> {}
        }

        // Final normalization / clamping
        return RecommendationStrategy(
            exploitationWeight = exploit.coerceIn(0f, 2f),
            explorationWeight = explore.coerceIn(0f, 2f),
            noveltyWeight = novelty.coerceIn(0f, 2f),
            diversityWeight = diversity.coerceIn(0f, 2f),
            familiarityPenalty = penalty.coerceIn(0f, 2f)
        )
    }
}

package com.example.data

/**
 * Data-backed explanation for a recommendation.
 */
data class RecommendationExplanation(
    val primaryReason: String,
    val detailPoints: List<String> = emptyList(),
    val confidenceLabel: String? = null,
    val isExploratory: Boolean = false
)

object RecommendationExplanationGenerator {

    /**
     * Generates an explanation for why a specific item was recommended.
     */
    fun generate(
        item: MediaItem,
        tasteDNA: TasteDNA,
        stats: IntelligenceStats,
        creatorProfiles: Map<String, CreatorProfile>,
        strategy: RecommendationStrategy? = null
    ): RecommendationExplanation? {
        val evidence = ExplorationEngine.calculateEvidence(item, tasteDNA, stats, creatorProfiles)
        
        val details = mutableListOf<String>()
        var primary = ""
        var isExploratory = false

        // 0. Contextual Mode Signal
        if (strategy != null) {
            if (strategy.explorationWeight > 1.2f) {
                details.add("Priority: Discovering new potential favorites")
            } else if (strategy.exploitationWeight > 1.2f) {
                details.add("Priority: Content you are highly likely to enjoy")
            }
        }

        // 1. Explicit signals (Highest priority for explanation)
        if (item.isFavorite) {
            primary = "Similar to your favorites"
            details.add("You previously favorited this item")
        } else if (item.rating >= 4.0f) {
            primary = "Based on your high rating"
            details.add("You gave this item ${item.rating.toInt()} stars")
        }

        // 2. High Match logic (DNA alignment)
        if (primary.isEmpty() && evidence.exploitationScore > 0.75) {
            primary = "High predicted match"
            val highDNA = getHighPreferenceDimensions(item, tasteDNA)
            if (highDNA.isNotEmpty()) {
                details.add("Matches your preference for: ${highDNA.joinToString(", ")}")
            }
        }

        // 3. Novelty / Exploration
        if (primary.isEmpty() && evidence.noveltyScore > 0.7) {
            primary = "Expand your taste"
            isExploratory = true
            if (item.viewCount == 0 && item.exposureCount == 0) {
                details.add("A new discovery you haven't seen yet")
            }
            if (!stats.topGenres.contains(item.genre)) {
                details.add("A style outside your usual ${stats.topGenres.firstOrNull() ?: "genres"}")
            }
        }

        // 4. Uncertainty / Aura learning (Only if match is low/moderate)
        if (primary.isEmpty() && evidence.uncertaintyScore > 0.7 && evidence.exploitationScore < 0.6) {
            primary = "Aura is learning your style"
            details.add("Recommended to refine your future predictions")
        }

        // 5. Creator Affinity (Secondary signal)
        val creator = item.creatorId?.let { creatorProfiles[it] }
        if (creator != null && creator.affinityScore > 0.6) {
            details.add("From ${creator.name}, a creator you enjoy")
        }

        // 6. Emotional Role Overrides (Phase 11)
        if (strategy != null) {
            // We can detect roles from weights if we didn't pass it explicitly
            when {
                strategy.exploitationWeight > 1.5f -> {
                    primary = "High Confidence"
                    details.add("Aura thinks this is strongly aligned with your taste.")
                }
                strategy.noveltyWeight > 1.5f -> {
                    primary = "Wildcard"
                    details.add("This is outside your usual pattern.")
                }
                strategy.explorationWeight > 1.5f -> {
                    primary = "Exploration"
                    details.add("Aura is testing something new.")
                }
            }
        }

        // Fallback
        if (primary.isEmpty()) {
            if (evidence.exploitationScore > 0.5) {
                primary = "Aura matched this to your taste"
            } else if (evidence.uncertaintyScore > 0.7) {
                primary = "Aura is learning your style"
                details.add("Recommended to refine your future predictions")
            } else {
                return null
            }
        }

        return RecommendationExplanation(
            primaryReason = primary,
            detailPoints = details,
            confidenceLabel = if (evidence.exploitationScore > 0.6) "${(evidence.exploitationScore * 100).toInt()}% Match" else null,
            isExploratory = isExploratory
        )
    }

    private fun getHighPreferenceDimensions(item: MediaItem, tasteDNA: TasteDNA): List<String> {
        val traits = PersonalizationTraitMapper.getTraitAdjustments(item.moodTags)
        val matches = mutableListOf<String>()
        
        traits.forEach { (dim, presence) ->
            val userPref = when(dim) {
                "vibrancy" -> tasteDNA.effectiveVibrancy
                "contrast" -> tasteDNA.effectiveContrast
                "sharpness" -> tasteDNA.effectiveSharpness
                "symmetry" -> tasteDNA.effectiveSymmetry
                "complexity" -> tasteDNA.effectiveComplexity
                "naturalism" -> tasteDNA.effectiveNaturalism
                "novelty" -> tasteDNA.effectiveNovelty
                "lighting" -> tasteDNA.effectiveLighting
                "colorTemperature" -> tasteDNA.effectiveColorTemp
                "texture" -> tasteDNA.effectiveTexture
                "motion" -> tasteDNA.effectiveMotion
                "dynamicRange" -> tasteDNA.effectiveDynamicRange
                "framing" -> tasteDNA.effectiveFraming
                "depth" -> tasteDNA.effectiveDepth
                "warmth" -> tasteDNA.effectiveWarmth
                "saturation" -> tasteDNA.effectiveSaturation
                "elegance" -> tasteDNA.effectiveElegance
                "minimalism" -> tasteDNA.effectiveMinimalism
                "grain" -> tasteDNA.effectiveGrain
                "focus" -> tasteDNA.effectiveFocus
                "density" -> tasteDNA.effectiveDensity
                "rhythm" -> tasteDNA.effectiveRhythm
                "mood" -> tasteDNA.effectiveMood
                "harmony" -> tasteDNA.effectiveHarmony
                else -> 0.5
            }
            
            // If item has the trait and user likes the trait
            if (presence > 0.5 && userPref > 0.7) {
                matches.add(dim.replaceFirstChar { it.uppercase() })
            } else if (presence < -0.5 && userPref < 0.3) {
                matches.add("Muted/Subtle $dim".replaceFirstChar { it.uppercase() })
            }
        }
        return matches.take(3)
    }
}

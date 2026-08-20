package com.example.data

/**
 * Core engine for estimating the exploration value and learning potential of candidates.
 * Distinguishes between exploitation (enjoyment) and exploration (information gain).
 */
object ExplorationEngine {

    data class CandidateEvidence(
        val exploitationScore: Float = 0f,
        val explorationScore: Float = 0f,
        val noveltyScore: Float = 0f,
        val informationGainScore: Float = 0f,
        val uncertaintyScore: Float = 0f,
        val familiarityScore: Float = 0f,
        val redundancyScore: Float = 0f,
        val predictedEnjoymentScore: Float = 0f,
        val creatorAffinityScore: Float = 0f,
        val creatorNoveltyScore: Float = 0f
    )

    /**
     * Estimates exploration and exploitation values for a candidate item.
     */
    fun calculateEvidence(
        item: MediaItem,
        tasteDNA: TasteDNA,
        stats: IntelligenceStats = IntelligenceStats(),
        creatorProfiles: Map<String, CreatorProfile> = emptyMap(),
        now: Long = System.currentTimeMillis()
    ): CandidateEvidence {
        // 1. Predicted Enjoyment (Exploitation component)
        val traits = PersonalizationTraitMapper.getTraitAdjustments(item.moodTags)
        var alignmentScore = 0.5 // Neutral baseline
        
        if (traits.isNotEmpty()) {
            var sumAlignment = 0.0
            traits.forEach { (dim, presence) ->
                val itemTraitValue = (presence + 1.0) / 2.0
                val userPref = getDimensionValue(tasteDNA, dim)
                val alignment = 1.0 - Math.abs(userPref - itemTraitValue)
                sumAlignment += alignment
            }
            alignmentScore = sumAlignment / traits.size
        }

        // Creator Affinity Signal
        val creatorAffinity = item.creatorId?.let { creatorProfiles[it]?.affinityScore?.toFloat() } ?: 0f

        // Incorporate explicit signals - Stronger weighting for explicit feedback
        val explicitSignal = (item.rating / 5.0f * 0.5f) + (if (item.isFavorite) 0.3f else 0f)
        
        // Incorporate ELO rating as a relative enjoyment signal
        val eloSignal = ((item.eloRating.toFloat() - 1500f) / 500f).coerceIn(-0.5f, 0.5f)
        
        val enjoyment = (alignmentScore.toFloat() * 0.2f) + explicitSignal + (creatorAffinity * 0.5f) + eloSignal

        // 2. Familiarity (Inverse of exploration)
        // Strong familiarity comes from rating or multiple views
        val interactionWeight = if (item.rating > 0) 0.6f else Math.min(item.viewCount, 4) * 0.15f
        val familiarity = (interactionWeight + Math.min(item.exposureCount, 10) * 0.02f).coerceIn(0f, 1f)

        // 3. Uncertainty
        val uncertainty = (1.0f - familiarity).coerceIn(0f, 1f)

        // 4. Novelty
        val isEstablishedGenre = stats.topGenres.any { it.equals(item.genre, ignoreCase = true) }
        val genreNovelty = if (isEstablishedGenre) 0.0f else 0.5f
        
        // Creator Novelty: High if creator is unknown
        val creatorNovelty = if (item.creatorId == null || !creatorProfiles.containsKey(item.creatorId)) 0.6f else 0.0f
        
        val novelty = (genreNovelty + (if (item.viewCount == 0) 0.5f else 0f) + creatorNovelty * 0.3f).coerceIn(0f, 1f)

        // 5. Information Gain
        val traitDensity = if (traits.size >= 3) 0.4f else traits.size * 0.15f
        val infoGain = (uncertainty * 0.5f + traitDensity).coerceIn(0f, 1f)

        // 6. Redundancy (Penalty source)
        val recencyPenalty = calculateRecencyPenalty(item.lastExposedTimestamp, now)
        val redundancy = (familiarity * 0.4f + recencyPenalty).coerceIn(0f, 1f)

        return CandidateEvidence(
            exploitationScore = enjoyment.coerceIn(0f, 1f),
            explorationScore = uncertainty,
            noveltyScore = novelty,
            informationGainScore = infoGain,
            uncertaintyScore = uncertainty,
            familiarityScore = familiarity,
            redundancyScore = redundancy,
            predictedEnjoymentScore = enjoyment,
            creatorAffinityScore = creatorAffinity,
            creatorNoveltyScore = creatorNovelty
        )
    }

    /**
     * Computes a policy-aware final recommendation score using the strategy weights.
     */
    fun calculatePolicyScore(
        evidence: CandidateEvidence,
        strategy: RecommendationStrategy
    ): Float {
        // finalScore = exploitWeight * enjoy + exploreWeight * explore + noveltyWeight * novelty + ...
        return (evidence.exploitationScore * strategy.exploitationWeight) +
               (evidence.explorationScore * strategy.explorationWeight) +
               (evidence.noveltyScore * strategy.noveltyWeight) +
               (evidence.informationGainScore * strategy.explorationWeight * 0.4f) - // Minor boost for learning
               (evidence.redundancyScore * strategy.familiarityPenalty)
    }

    private fun getDimensionValue(tasteDNA: TasteDNA, dimension: String): Double {
        return when(dimension) {
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
    }

    private fun calculateRecencyPenalty(lastExposed: Long?, now: Long): Float {
        if (lastExposed == null) return 0f
        val elapsed = now - lastExposed
        val hourMs = 3600000L
        return if (elapsed < hourMs) {
            1.0f - (elapsed.toFloat() / hourMs)
        } else {
            0f
        }
    }

    /**
     * Calculates the information gain potential of comparing two items.
     * High gain when items have contrasting traits on dimensions Aura is unsure about.
     */
    fun calculatePairInformationGain(
        itemA: MediaItem,
        itemB: MediaItem,
        tasteDNA: TasteDNA
    ): Float {
        val traitsA = PersonalizationTraitMapper.getTraitAdjustments(itemA.moodTags)
        val traitsB = PersonalizationTraitMapper.getTraitAdjustments(itemB.moodTags)
        
        if (traitsA.isEmpty() && traitsB.isEmpty()) return 0.1f
        
        val allDimensions = traitsA.keys + traitsB.keys
        var totalGain = 0.0
        
        allDimensions.forEach { dim ->
            val valA = traitsA[dim] ?: 0.0 // Default to neutral/unknown if trait not present
            val valB = traitsB[dim] ?: 0.0
            
            // Contrast is the absolute difference in trait presence
            val contrast = Math.abs(valA - valB)
            
            // Information gain is higher when items contrast on this dimension
            totalGain += contrast
        }
        
        // Normalize by total dimensions involved
        return if (allDimensions.isNotEmpty()) {
            (totalGain / allDimensions.size).toFloat()
        } else {
            0.1f
        }
    }
}

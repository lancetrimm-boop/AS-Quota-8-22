package com.example.data.social

import com.example.data.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * On-device intelligence engine that evaluates external video candidates against the user's active Taste DNA.
 * Completely local, air-gapped, and zero-persistence.
 */
object SocialDiscoveryEngine {

    /**
     * Evaluates a single candidate DTO against the given [TasteDNA] and [DiscoveryPolicy].
     */
    fun evaluateCandidate(
        candidate: SocialCandidateDto,
        tasteDNA: TasteDNA,
        policy: DiscoveryPolicy = DiscoveryPolicy()
    ): AuraSocialCandidate {
        // 1. Normalize metadata tokens safely
        val normalizedTokens = SocialMetadataNormalizer.normalize(
            title = candidate.title,
            description = candidate.description,
            rawTags = candidate.rawTags
        )

        // 2. Build ephemeral MediaItem for Aura intelligence compatibility
        val ephemeralItem = MediaItem(
            id = candidate.id,
            title = candidate.title,
            mediaType = "VIDEO",
            moodTags = normalizedTokens,
            category = "Social",
            imageUrl = candidate.thumbnailUrl,
            uriPath = candidate.externalUrl,
            sourcePlatform = "YOUTUBE",
            rating = 0f,
            isFavorite = false,
            eloRating = 1500.0,
            viewCount = 0,
            exposureCount = 0,
            creatorId = candidate.channelId.ifBlank { null },
            creatorName = candidate.channelName.ifBlank { null }
        )

        // 3. Map traits via existing PersonalizationTraitMapper
        val traitAdjustments = PersonalizationTraitMapper.getEffectiveTraitAdjustments(ephemeralItem)

        // 4. Calculate true normalized Taste Alignment (0.0 to 1.0)
        val matchedDetails = mutableListOf<MatchedTraitDetail>()
        val alignmentScore: Double

        if (traitAdjustments.isNotEmpty()) {
            var sumAlignment = 0.0
            traitAdjustments.forEach { (dim, presence) ->
                val itemTraitValue = (presence + 1.0) / 2.0 // Map [-1, 1] to [0, 1]
                val userPref = getEffectiveDimension(tasteDNA, dim)
                val traitAlignment = (1.0 - abs(userPref - itemTraitValue)).coerceIn(0.0, 1.0)
                sumAlignment += traitAlignment

                matchedDetails.add(
                    MatchedTraitDetail(
                        dimension = dim,
                        displayName = formatDimensionName(dim),
                        itemPresence = presence,
                        userPreference = userPref,
                        alignment = traitAlignment
                    )
                )
            }
            alignmentScore = (sumAlignment / traitAdjustments.size).coerceIn(0.0, 1.0)
        } else {
            alignmentScore = 0.50 // Neutral baseline
        }

        val tasteAlignmentPercent = (alignmentScore * 100.0).roundToInt().coerceIn(0, 100)

        // 5. Calculate diagnostic evidence via ExplorationEngine
        val evidence = ExplorationEngine.calculateEvidence(ephemeralItem, tasteDNA)
        val strategy = DiscoveryPolicyManager.resolveStrategy(
            policy = policy,
            intent = UserIntent(),
            objective = RecommendationObjective.GENERAL_DISCOVERY
        )
        val policyScore = ExplorationEngine.calculatePolicyScore(evidence, strategy)

        // Sort matched traits by strongest alignment first
        matchedDetails.sortByDescending { it.alignment }

        return AuraSocialCandidate(
            candidate = candidate,
            ephemeralMediaItem = ephemeralItem,
            normalizedTokens = normalizedTokens,
            traitAdjustments = traitAdjustments,
            alignmentScore = alignmentScore,
            tasteAlignmentPercent = tasteAlignmentPercent,
            exploitationScore = evidence.exploitationScore,
            policyScore = policyScore,
            matchedTraits = matchedDetails,
            originalRank = candidate.originalRank,
            auraRank = candidate.originalRank, // Initialized, re-ranked in benchmark
            rankDelta = 0
        )
    }

    /**
     * Executes the complete benchmark pipeline on a candidate list:
     * scores each candidate, sorts by Taste Alignment, computes rank deltas and aggregate metrics.
     */
    fun runBenchmark(
        candidates: List<SocialCandidateDto>,
        tasteDNA: TasteDNA,
        policy: DiscoveryPolicy = DiscoveryPolicy(),
        query: String = ""
    ): SocialDiscoveryBenchmarkResult {
        if (candidates.isEmpty()) {
            return SocialDiscoveryBenchmarkResult(
                query = query,
                totalFetched = 0,
                totalScored = 0,
                rankChangedCount = 0,
                rankChangedPercentage = 0.0,
                avgAbsoluteRankMovement = 0.0,
                top3OverlapCount = 0,
                largestPositiveDelta = 0,
                largestPositiveCandidate = null,
                largestNegativeDelta = 0,
                largestNegativeCandidate = null,
                rankCorrelation = null,
                originalPlatformCandidates = emptyList(),
                auraRankedCandidates = emptyList()
            )
        }

        // 1. Evaluate each candidate locally in memory
        val scoredList = candidates.mapIndexed { index, dto ->
            evaluateCandidate(dto.copy(originalRank = index + 1), tasteDNA, policy)
        }

        // 2. Sort by Taste Alignment (Primary), Policy Score (Secondary), then original rank (Tie-break)
        val sortedByTaste = scoredList.sortedWith(
            compareByDescending<AuraSocialCandidate> { it.alignmentScore }
                .thenByDescending { it.policyScore }
                .thenBy { it.originalRank }
        )

        // 3. Assign Aura ranks (1..N) and calculate rankDelta = originalRank - auraRank
        val finalAuraRanked = sortedByTaste.mapIndexed { index, item ->
            val auraRank = index + 1
            val delta = item.originalRank - auraRank
            item.copy(auraRank = auraRank, rankDelta = delta)
        }

        // 4. Map back to original list with updated Aura rank / deltas
        val idToRankedMap = finalAuraRanked.associateBy { it.candidate.id }
        val finalOriginalList = scoredList.map { unranked ->
            idToRankedMap[unranked.candidate.id] ?: unranked
        }

        // 5. Calculate benchmark diagnostics
        val totalScored = finalAuraRanked.size
        var rankChangedCount = 0
        var totalAbsDelta = 0

        var largestPosDelta = 0
        var largestPosCandidate: AuraSocialCandidate? = null
        var largestNegDelta = 0
        var largestNegCandidate: AuraSocialCandidate? = null

        finalAuraRanked.forEach { candidate ->
            val delta = candidate.rankDelta
            val absDelta = abs(delta)
            if (delta != 0) {
                rankChangedCount++
                totalAbsDelta += absDelta
            }
            if (delta > largestPosDelta) {
                largestPosDelta = delta
                largestPosCandidate = candidate
            }
            if (delta < largestNegDelta) {
                largestNegDelta = delta
                largestNegCandidate = candidate
            }
        }

        val rankChangedPct = if (totalScored > 0) (rankChangedCount.toDouble() / totalScored) * 100.0 else 0.0
        val avgAbsMovement = if (totalScored > 0) totalAbsDelta.toDouble() / totalScored else 0.0

        // Calculate Top-3 Overlap
        val originalTop3Ids = finalOriginalList.take(3).map { it.candidate.id }.toSet()
        val auraTop3Ids = finalAuraRanked.take(3).map { it.candidate.id }.toSet()
        val top3Overlap = originalTop3Ids.intersect(auraTop3Ids).size

        // Calculate Spearman's Rank Correlation
        val spearman = calculateSpearmanCorrelation(finalAuraRanked)

        return SocialDiscoveryBenchmarkResult(
            query = query,
            totalFetched = candidates.size,
            totalScored = totalScored,
            rankChangedCount = rankChangedCount,
            rankChangedPercentage = rankChangedPct,
            avgAbsoluteRankMovement = avgAbsMovement,
            top3OverlapCount = top3Overlap,
            largestPositiveDelta = largestPosDelta,
            largestPositiveCandidate = largestPosCandidate,
            largestNegativeDelta = largestNegDelta,
            largestNegativeCandidate = largestNegCandidate,
            rankCorrelation = spearman,
            originalPlatformCandidates = finalOriginalList,
            auraRankedCandidates = finalAuraRanked
        )
    }

    /**
     * Retrieves the active user preference for a given 24-D dimension.
     */
    fun getEffectiveDimension(tasteDNA: TasteDNA, dimension: String): Double {
        return when (dimension) {
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

    private fun formatDimensionName(dimension: String): String {
        return when (dimension) {
            "colorTemperature" -> "Color Temp"
            "dynamicRange" -> "Dynamic Range"
            else -> dimension.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Computes Spearman's rank correlation coefficient:
     * r = 1 - (6 * sum(d_i^2)) / (n * (n^2 - 1))
     */
    private fun calculateSpearmanCorrelation(candidates: List<AuraSocialCandidate>): Double? {
        val n = candidates.size
        if (n < 2) return null
        val sumD2 = candidates.sumOf { (it.originalRank - it.auraRank).toLong() * (it.originalRank - it.auraRank) }
        val denominator = n.toDouble() * (n.toDouble() * n.toDouble() - 1.0)
        if (denominator == 0.0) return null
        val r = 1.0 - ((6.0 * sumD2) / denominator)
        return r.coerceIn(-1.0, 1.0)
    }
}

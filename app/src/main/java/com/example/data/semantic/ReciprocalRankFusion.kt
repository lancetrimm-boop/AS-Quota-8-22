package com.example.data.semantic

/**
 * Pure, deterministic implementation of Reciprocal Rank Fusion (RRF).
 *
 * RRF is a rank-aggregation algorithm that combines ranked candidate lists from multiple
 * independent retrieval mechanisms (e.g. keyword match, semantic vector retrieval) without
 * requiring calibration across incompatible raw score distributions.
 *
 * Mathematical formula for item $d$:
 * $$RRF(d) = \sum_{c \in C} \frac{w_c}{K + \text{rank}_c(d)}$$
 *
 * where:
 * - $C$ is the set of retrieval channels.
 * - $w_c$ is the normalized weight for channel $c$.
 * - $K$ is the smoothing constant (e.g. 60).
 * - $\text{rank}_c(d)$ is the 1-based rank position of item $d$ in channel $c$.
 */
object ReciprocalRankFusion {

    /**
     * Fuses multi-channel ranked candidate lists into a single ranked list of [HybridCandidate]s.
     *
     * @param channelResults Map of search channels to their respective ordered candidate lists.
     * @param config RRF parameters including smoothing constant $K$, channel weights, and topK limit.
     * @return Deterministically ordered list of fused candidates (sorted descending by RRF score, tie-break by mediaId ascending).
     */
    fun fuse(
        channelResults: Map<SearchChannel, List<RankedChannelItem>>,
        config: HybridSearchConfig = HybridSearchConfig()
    ): List<HybridCandidate> {
        if (channelResults.isEmpty()) return emptyList()

        val k = config.rrfConstantK
        val channelWeights = config.channelWeights

        // Accumulators keyed by mediaId
        class CandidateAccumulator(val mediaId: String) {
            var totalRrfScore: Double = 0.0
            val channelRanks = mutableMapOf<SearchChannel, Int>()
            val channelScores = mutableMapOf<SearchChannel, Float>()
        }

        val accumulators = mutableMapOf<String, CandidateAccumulator>()

        for ((channel, items) in channelResults) {
            val weight = channelWeights[channel] ?: 0.0
            if (weight <= 0.0) continue // Skip unweighted or zero-weighted channels

            for (item in items) {
                val acc = accumulators.getOrPut(item.mediaId) { CandidateAccumulator(item.mediaId) }
                
                // RRF incremental component
                val contribution = weight / (k.toDouble() + item.rank.toDouble())
                acc.totalRrfScore += contribution
                acc.channelRanks[channel] = item.rank
                acc.channelScores[channel] = item.rawScore
            }
        }

        if (accumulators.isEmpty()) return emptyList()

        // Deterministic sort: RRF score descending, then mediaId ascending (stable tie-breaking)
        val sortedCandidates = accumulators.values.sortedWith(
            compareByDescending<CandidateAccumulator> { it.totalRrfScore }
                .thenBy { it.mediaId }
        )

        // Trim to topK and build immutable HybridCandidate models
        return sortedCandidates
            .take(config.topK)
            .map { acc ->
                val explanation = buildExplanation(acc.channelRanks, acc.channelScores, acc.totalRrfScore)
                HybridCandidate(
                    mediaId = acc.mediaId,
                    rrfScore = acc.totalRrfScore,
                    channelRanks = acc.channelRanks.toMap(),
                    channelScores = acc.channelScores.toMap(),
                    matchExplanation = explanation
                )
            }
    }

    private fun buildExplanation(
        ranks: Map<SearchChannel, Int>,
        scores: Map<SearchChannel, Float>,
        rrfScore: Double
    ): String {
        val parts = ranks.entries.sortedBy { it.key.name }.map { (channel, rank) ->
            val score = scores[channel] ?: 0f
            "${channel.name}[Rank #$rank, Score ${"%.3f".format(score)}]"
        }
        return "RRF ${"%.5f".format(rrfScore)} via " + parts.joinToString(" + ")
    }
}

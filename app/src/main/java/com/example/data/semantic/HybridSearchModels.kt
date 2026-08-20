package com.example.data.semantic

/**
 * Categorical search/retrieval ranking channels participating in hybrid fusion.
 */
enum class SearchChannel {
    /**
     * Lexical token matching, BM25, exact substring, and metadata attribute matching.
     */
    KEYWORD,

    /**
     * Dense neural text semantic vector retrieval (e.g. MiniLM CONTENT representations).
     */
    SEMANTIC_CONTENT,

    /**
     * Dense neural visual embedding retrieval (e.g. MobileCLIP VISUAL representations).
     */
    SEMANTIC_VISUAL,

    /**
     * Personalized user preference or taste affinity trait ranking.
     */
    PERSONALIZED
}

/**
 * Ranked candidate item emitted by an individual retrieval channel.
 *
 * @property mediaId Unique media item identifier.
 * @property rawScore Uncalibrated channel-specific score (e.g. cosine similarity, BM25 score, hit count).
 * @property rank 1-based ordinal rank within the channel's candidate list.
 * @property metadata Optional channel-specific debugging or explainability metadata.
 */
data class RankedChannelItem(
    val mediaId: String,
    val rawScore: Float,
    val rank: Int,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(mediaId.isNotBlank()) { "mediaId cannot be blank" }
        require(rank >= 1) { "rank must be a 1-based positive integer (got $rank)" }
    }
}

/**
 * Configuration parameters for Reciprocal Rank Fusion (RRF) and hybrid search execution.
 *
 * @property rrfConstantK Smoothing constant in RRF denominator (standard literature default = 60).
 * @property channelWeights Relative importance multipliers for each search channel (must be non-negative).
 * @property topK Maximum number of fused candidates to return.
 * @property minSemanticSimilarity Optional cosine similarity filter for the semantic channel before fusion.
 */
data class HybridSearchConfig(
    val rrfConstantK: Int = 60,
    val channelWeights: Map<SearchChannel, Double> = mapOf(
        SearchChannel.KEYWORD to 0.4,
        SearchChannel.SEMANTIC_CONTENT to 0.4,
        SearchChannel.SEMANTIC_VISUAL to 0.2,
        SearchChannel.PERSONALIZED to 0.1
    ),
    val topK: Int = 20,
    val minSemanticSimilarity: Float = -1.0f
) {
    init {
        require(rrfConstantK > 0) { "rrfConstantK must be positive (got $rrfConstantK)" }
        require(topK > 0) { "topK must be positive (got $topK)" }
        for ((channel, weight) in channelWeights) {
            require(weight >= 0.0) { "Weight for channel $channel must be non-negative (got $weight)" }
        }
        val totalWeight = channelWeights.values.sum()
        require(totalWeight > 0.0) { "Sum of channel weights must be strictly positive (got $totalWeight)" }
    }

    fun getNormalizedWeight(channel: SearchChannel): Double {
        val total = channelWeights.values.sum()
        val raw = channelWeights[channel] ?: 0.0
        return if (total > 0.0) raw / total else 0.0
    }
}

/**
 * Fused candidate item produced by Reciprocal Rank Fusion.
 *
 * @property mediaId Unique media item identifier.
 * @property rrfScore Aggregated Reciprocal Rank Fusion score.
 * @property channelRanks Map of 1-based rank positions per contributing channel.
 * @property channelScores Map of raw channel scores per contributing channel.
 * @property matchExplanation Human-readable provenance explanation of the hybrid ranking.
 */
data class HybridCandidate(
    val mediaId: String,
    val rrfScore: Double,
    val channelRanks: Map<SearchChannel, Int>,
    val channelScores: Map<SearchChannel, Float>,
    val matchExplanation: String = ""
) {
    init {
        require(mediaId.isNotBlank()) { "mediaId cannot be blank" }
        require(rrfScore >= 0.0) { "rrfScore must be non-negative (got $rrfScore)" }
    }

    val isMultiChannelMatch: Boolean get() = channelRanks.size > 1
}

/**
 * Encapsulates the complete result of a hybrid search query execution.
 *
 * @property query Raw search query text.
 * @property candidates Ordered list of fused candidates (sorted descending by RRF score).
 * @property latencyMs Execution latency in milliseconds.
 * @property totalCandidatesConsidered Total unique media items evaluated across all channels before top-K trimming.
 * @property channelCandidateCounts Item count contributed by each individual search channel.
 * @property isSuccess Indicates whether execution succeeded.
 * @property errorMessage Error description if execution failed.
 */
data class HybridSearchResult(
    val query: String,
    val candidates: List<HybridCandidate>,
    val latencyMs: Long,
    val totalCandidatesConsidered: Int,
    val channelCandidateCounts: Map<SearchChannel, Int>,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
) {
    val topMatch: HybridCandidate? get() = candidates.firstOrNull()
    val hasMatches: Boolean get() = candidates.isNotEmpty()
    val candidateCount: Int get() = candidates.size
}

package com.example.data.semantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Adapter interface providing lexical / keyword search candidate items for hybrid fusion.
 */
interface LexicalCandidateRetriever {
    /**
     * Retrieves ranked keyword candidates for the given search query.
     *
     * @param query Search query string.
     * @param topK Maximum number of keyword candidates to retrieve.
     * @return List of [RankedChannelItem]s with 1-based ranks.
     */
    suspend fun retrieveKeywordCandidates(query: String, topK: Int = 50): List<RankedChannelItem>
}

/**
 * Interface for providing personalization scores for media items during hybrid search.
 */
interface PersonalizationScorer {
    /**
     * Scores a media item based on user preferences.
     * @return A personalization score (higher is better).
     */
    fun score(mediaId: String): Float
}

/**
 * High-level search engine interface providing blended Hybrid Search via Reciprocal Rank Fusion.
 */
interface HybridSearchEngine {
    /**
     * Executes a hybrid search combining keyword and semantic retrieval channels.
     *
     * @param query Search query string.
     * @param config Hybrid configuration (RRF constant K, channel weights, topK, semantic threshold).
     * @return [HybridSearchResult] containing fused candidates and channel metadata.
     */
    suspend fun search(
        query: String,
        config: HybridSearchConfig = HybridSearchConfig()
    ): HybridSearchResult

    /**
     * Checks if the semantic retrieval subsystem is initialized and ready for inference.
     */
    fun isSemanticReady(): Boolean
}

/**
 * Production implementation of [HybridSearchEngine].
 *
 * Coordinates concurrent candidate retrieval from the [LexicalCandidateRetriever] and [SemanticSearchService],
 * transforms channel results into ranked items, and executes deterministic Reciprocal Rank Fusion.
 *
 * Features:
 * - Robust fallback: If semantic retrieval is unready or fails, smoothly returns keyword ranking without failing the query.
 * - Concurrency: Fetches lexical and semantic candidates in parallel using structured coroutines.
 * - Modality & Descriptor Safety: Passes through typed constraints to the semantic service.
 * - Personalization: Integrates user preferences as a separate ranking channel in the fusion process.
 * - Deterministic Sorting: Primary sort by RRF score descending, tie-break by mediaId ascending.
 */
class DefaultHybridSearchEngine(
    private val semanticService: SemanticSearchService,
    private val lexicalRetriever: LexicalCandidateRetriever,
    private val visualRetriever: MobileCLIPVisualRetriever? = null,
    private val personalizationScorer: PersonalizationScorer? = null,
    private val rrf: ReciprocalRankFusion = ReciprocalRankFusion
) : HybridSearchEngine {

    override fun isSemanticReady(): Boolean = semanticService.isReady() || visualRetriever?.isReady() == true

    override suspend fun search(
        query: String,
        config: HybridSearchConfig
    ): HybridSearchResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            return@withContext HybridSearchResult(
                query = query,
                candidates = emptyList(),
                latencyMs = 0L,
                totalCandidatesConsidered = 0,
                channelCandidateCounts = emptyMap(),
                isSuccess = false,
                errorMessage = "Search query cannot be blank."
            )
        }

        val channelResults = mutableMapOf<SearchChannel, List<RankedChannelItem>>()
        val channelCounts = mutableMapOf<SearchChannel, Int>()
        var semanticError: String? = null

        coroutineScope {
            val lexicalDeferred = async {
                try {
                    lexicalRetriever.retrieveKeywordCandidates(trimmedQuery, topK = config.topK * 2)
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val semanticDeferred = async {
                try {
                    semanticService.search(
                        query = trimmedQuery,
                        topK = config.topK * 2,
                        minSimilarity = config.minSemanticSimilarity,
                        targetType = SemanticRepresentationType.CONTENT
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val visualDeferred = async {
                if (visualRetriever != null && visualRetriever.isReady()) {
                    try {
                        visualRetriever.retrieveVisualCandidates(
                            query = trimmedQuery,
                            topK = config.topK * 2,
                            minSimilarity = config.minSemanticSimilarity
                        )
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

            // Await lexical candidates
            val keywordCandidates = lexicalDeferred.await()
            if (keywordCandidates.isNotEmpty()) {
                channelResults[SearchChannel.KEYWORD] = keywordCandidates
                channelCounts[SearchChannel.KEYWORD] = keywordCandidates.size
            }

            // Await semantic candidates
            val semanticResult = semanticDeferred.await()
            if (semanticResult != null && semanticResult.isSuccess) {
                val semanticRankedItems = semanticResult.candidates.mapIndexed { index, candidate ->
                    RankedChannelItem(
                        mediaId = candidate.mediaId,
                        rawScore = candidate.similarityScore,
                        rank = index + 1,
                        metadata = mapOf(
                            "representationType" to candidate.type.name,
                            "modelId" to candidate.modelDescriptor.modelId
                        )
                    )
                }
                if (semanticRankedItems.isNotEmpty()) {
                    channelResults[SearchChannel.SEMANTIC_CONTENT] = semanticRankedItems
                    channelCounts[SearchChannel.SEMANTIC_CONTENT] = semanticRankedItems.size
                }
            } else if (semanticResult != null && !semanticResult.isSuccess) {
                semanticError = semanticResult.errorMessage
            }

            // Await visual candidates
            val visualCandidates = visualDeferred.await()
            if (visualCandidates.isNotEmpty()) {
                channelResults[SearchChannel.SEMANTIC_VISUAL] = visualCandidates
                channelCounts[SearchChannel.SEMANTIC_VISUAL] = visualCandidates.size
            }
        }

        // --- INTEGRATE PERSONALIZATION (Phase 7 Step 10) ---
        // If a personalization scorer is available and the channel has weight, 
        // rank the unique candidates by user preference.
        if (personalizationScorer != null && config.channelWeights.getOrDefault(SearchChannel.PERSONALIZED, 0.0) > 0.0) {
            val candidateIds = channelResults.values.flatten().map { it.mediaId }.distinct()
            if (candidateIds.isNotEmpty()) {
                val personalizedItems = candidateIds.map { mediaId ->
                    RankedChannelItem(
                        mediaId = mediaId,
                        rawScore = personalizationScorer.score(mediaId),
                        rank = 0 // To be filled after sorting
                    )
                }.sortedByDescending { it.rawScore }
                 .mapIndexed { index, item -> item.copy(rank = index + 1) }

                channelResults[SearchChannel.PERSONALIZED] = personalizedItems
                channelCounts[SearchChannel.PERSONALIZED] = personalizedItems.size
            }
        }

        // Total unique candidates evaluated
        val totalUniqueCandidates = channelResults.values.flatten().map { it.mediaId }.distinct().size

        // Execute Reciprocal Rank Fusion
        val fusedCandidates = rrf.fuse(channelResults, config)

        val elapsed = System.currentTimeMillis() - startTime

        HybridSearchResult(
            query = query,
            candidates = fusedCandidates,
            latencyMs = elapsed,
            totalCandidatesConsidered = totalUniqueCandidates,
            channelCandidateCounts = channelCounts.toMap(),
            isSuccess = true,
            errorMessage = if (channelResults.isEmpty() && semanticError != null) semanticError else null
        )
    }
}

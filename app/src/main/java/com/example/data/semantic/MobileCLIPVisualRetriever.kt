package com.example.data.semantic

/**
 * Specialized retriever for MobileCLIP visual embeddings.
 *
 * Bridges the gap between a text query and the VISUAL modality index using the
 * MobileCLIP shared embedding space.
 */
interface MobileCLIPVisualRetriever {
    /**
     * True if the underlying inference engine and index are ready.
     */
    fun isReady(): Boolean

    /**
     * Retrieves ranked media candidates based on visual similarity to the text query.
     */
    suspend fun retrieveVisualCandidates(
        query: String,
        topK: Int,
        minSimilarity: Float
    ): List<RankedChannelItem>
}

/**
 * Production implementation of [MobileCLIPVisualRetriever] using [SemanticSearchService].
 */
class DefaultMobileCLIPVisualRetriever(
    private val visualSearchService: SemanticSearchService
) : MobileCLIPVisualRetriever {

    override fun isReady(): Boolean = visualSearchService.isReady()

    override suspend fun retrieveVisualCandidates(
        query: String,
        topK: Int,
        minSimilarity: Float
    ): List<RankedChannelItem> {
        val result = try {
            visualSearchService.search(
                query = query,
                topK = topK,
                minSimilarity = minSimilarity,
                targetType = SemanticRepresentationType.VISUAL
            )
        } catch (e: Exception) {
            return emptyList()
        }

        if (!result.isSuccess) return emptyList()

        return result.candidates.mapIndexed { index, candidate ->
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
    }
}

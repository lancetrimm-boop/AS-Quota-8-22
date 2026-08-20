package com.example.data.semantic

import java.util.concurrent.ConcurrentHashMap

/**
 * Result of a top-K semantic candidate retrieval operation.
 */
data class SemanticRetrievalCandidate(
    val mediaId: String,
    val representationId: String,
    val similarityScore: Float,
    val type: SemanticRepresentationType,
    val modelDescriptor: EmbeddingModelDescriptor,
    val confidence: Float
)

/**
 * High-level vector index contract supporting thread-safe insertion, removal, rebuild, and top-K query.
 */
interface VectorIndex {
    val descriptor: EmbeddingModelDescriptor
    val targetType: SemanticRepresentationType
    val size: Int

    fun add(representation: SemanticRepresentation)
    fun addAll(representations: List<SemanticRepresentation>)
    fun remove(representationId: String)
    fun removeForMedia(mediaId: String)
    fun clear()
    fun rebuild(representations: List<SemanticRepresentation>)
    fun query(queryVector: FloatArray, topK: Int = 20, minSimilarity: Float = -1.0f): List<SemanticRetrievalCandidate>
}

/**
 * In-memory thread-safe candidate retrieval vector index.
 *
 * Implements exact cosine similarity with deterministic tie-breaking over normalized vectors.
 * Architecture allows seamless replacement with HNSW/ScaNN without affecting downstream modules.
 */
class InMemoryVectorIndex(
    override val descriptor: EmbeddingModelDescriptor,
    override val targetType: SemanticRepresentationType = descriptor.primaryType
) : VectorIndex {

    // Map: representationId -> IndexedItem
    private val entries = ConcurrentHashMap<String, IndexedItem>()

    data class IndexedItem(
        val representation: SemanticRepresentation,
        val normalizedVector: FloatArray
    )

    override val size: Int
        get() = entries.size

    override fun add(representation: SemanticRepresentation) {
        validateCompatibility(representation)
        val normalized = VectorMath.l2Normalize(representation.vector)
        entries[representation.id] = IndexedItem(representation, normalized)
    }

    override fun addAll(representations: List<SemanticRepresentation>) {
        for (rep in representations) {
            add(rep)
        }
    }

    override fun remove(representationId: String) {
        entries.remove(representationId)
    }

    override fun removeForMedia(mediaId: String) {
        val toRemove = entries.filterValues { it.representation.mediaId == mediaId }.keys
        for (id in toRemove) {
            entries.remove(id)
        }
    }

    override fun clear() {
        entries.clear()
    }

    override fun rebuild(representations: List<SemanticRepresentation>) {
        entries.clear()
        addAll(representations)
    }

    override fun query(
        queryVector: FloatArray,
        topK: Int,
        minSimilarity: Float
    ): List<SemanticRetrievalCandidate> {
        if (queryVector.isEmpty() || entries.isEmpty() || topK <= 0) {
            return emptyList()
        }

        VectorMath.validateVector(queryVector)
        descriptor.validateVectorDimensionality(queryVector)

        val normQuery = VectorMath.l2Normalize(queryVector)

        // Track best score per mediaId to prevent duplicate media candidate records
        val bestByMedia = mutableMapOf<String, SemanticRetrievalCandidate>()

        for ((_, item) in entries) {
            val rep = item.representation
            val score = VectorMath.dotProduct(normQuery, item.normalizedVector)

            if (score >= minSimilarity) {
                val candidate = SemanticRetrievalCandidate(
                    mediaId = rep.mediaId,
                    representationId = rep.id,
                    similarityScore = score,
                    type = rep.type,
                    modelDescriptor = rep.modelDescriptor,
                    confidence = rep.confidence
                )

                val existing = bestByMedia[rep.mediaId]
                if (existing == null || candidate.similarityScore > existing.similarityScore) {
                    bestByMedia[rep.mediaId] = candidate
                }
            }
        }

        // Deterministic sorting: Descending similarity score, then tie-break by mediaId ascending
        return bestByMedia.values
            .sortedWith(
                compareByDescending<SemanticRetrievalCandidate> { it.similarityScore }
                    .thenBy { it.mediaId }
            )
            .take(topK)
    }

    private fun validateCompatibility(representation: SemanticRepresentation) {
        require(representation.type == targetType) {
            "Representation type mismatch for index. Expected $targetType, got ${representation.type}"
        }
        require(representation.modelDescriptor.isCompatibleWith(descriptor)) {
            "Model descriptor incompatible with index. Expected $descriptor, got ${representation.modelDescriptor}"
        }
    }
}

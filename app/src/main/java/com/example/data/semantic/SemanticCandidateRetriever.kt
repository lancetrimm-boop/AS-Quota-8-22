package com.example.data.semantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-level candidate retrieval coordinator for semantic search and recommendation systems.
 *
 * Coordinates in-memory typed vector indices, hydrates representations from repository on demand,
 * and executes query vectors without coupling callers to index mechanics.
 */
interface SemanticCandidateRetriever {
    suspend fun initializeIndex(
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    )

    suspend fun retrieveCandidates(
        queryVector: FloatArray,
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor,
        topK: Int = 20,
        minSimilarity: Float = -1.0f
    ): List<SemanticRetrievalCandidate>

    fun onRepresentationAdded(representation: SemanticRepresentation)
    fun onRepresentationRemoved(representationId: String)
    fun onMediaRemoved(mediaId: String)
    fun getIndexSize(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): Int
}

/**
 * Production implementation of [SemanticCandidateRetriever] with concurrent typed index registry.
 */
class DefaultSemanticCandidateRetriever(
    private val repository: SemanticRepresentationRepository
) : SemanticCandidateRetriever {

    // Index key: "$type:${descriptor.modelId}:${descriptor.modelVersion}:${descriptor.dimensionality}"
    private val indices = ConcurrentHashMap<String, VectorIndex>()

    private fun getIndexKey(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): String {
        return "${type.name}:${descriptor.modelId}:${descriptor.modelVersion}:${descriptor.dimensionality}"
    }

    override suspend fun initializeIndex(
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor
    ) = withContext(Dispatchers.IO) {
        val key = getIndexKey(type, descriptor)
        val representations = repository.getCompatibleRepresentations(type, descriptor)
        val index = InMemoryVectorIndex(descriptor, type)
        index.rebuild(representations)
        indices[key] = index
        
        // Forensics: Aggregate loading statistics
        val typeCount = representations.size
        val distinctMedia = representations.map { it.mediaId }.distinct().size
        
        android.util.Log.i("AuraSemanticTrace", "INDEX_INIT type=$type model=${descriptor.modelId} dimensionality=${descriptor.dimensionality} persistedEmbeddings=$typeCount distinctMedia=$distinctMedia indexedVectors=${index.size}")
        Unit
    }

    override suspend fun retrieveCandidates(
        queryVector: FloatArray,
        type: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor,
        topK: Int,
        minSimilarity: Float
    ): List<SemanticRetrievalCandidate> = withContext(Dispatchers.Default) {
        val key = getIndexKey(type, descriptor)
        var index = indices[key]

        if (index == null) {
            // Lazy initialize if not yet loaded
            initializeIndex(type, descriptor)
            index = indices[key] ?: return@withContext emptyList()
        }

        index.query(queryVector, topK, minSimilarity)
    }

    override fun onRepresentationAdded(representation: SemanticRepresentation) {
        val key = getIndexKey(representation.type, representation.modelDescriptor)
        val index = indices[key]
        if (index != null) {
            index.add(representation)
        } else {
            // Index not yet initialized - skip immediate update. 
            // It will be populated from DB during lazy initialization.
            android.util.Log.v("AuraSemanticTrace", "INDEX_INSERT_DEFERRED mediaId=${representation.mediaId} type=${representation.type}")
        }
    }

    override fun onRepresentationRemoved(representationId: String) {
        for (index in indices.values) {
            index.remove(representationId)
        }
    }

    override fun onMediaRemoved(mediaId: String) {
        for (index in indices.values) {
            index.removeForMedia(mediaId)
        }
    }

    override fun getIndexSize(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): Int {
        val key = getIndexKey(type, descriptor)
        return indices[key]?.size ?: 0
    }
}

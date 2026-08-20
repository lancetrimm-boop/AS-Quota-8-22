package com.example.data.semantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

/**
 * Encapsulates the outcome of a semantic search query execution.
 *
 * Contains retrieved ranked candidates, full model provenance metadata, representation modality,
 * query execution latency, and index capacity statistics.
 */
data class SemanticSearchResult(
    val query: String,
    val candidates: List<SemanticRetrievalCandidate>,
    val modelDescriptor: EmbeddingModelDescriptor,
    val representationType: SemanticRepresentationType,
    val latencyMs: Long,
    val totalIndexedCandidates: Int,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
) {
    val topMatch: SemanticRetrievalCandidate? get() = candidates.firstOrNull()
    val hasMatches: Boolean get() = candidates.isNotEmpty()
    val candidateCount: Int get() = candidates.size
}

/**
 * Coordinator contract for semantic natural-language retrieval.
 *
 * Integrates natural-language query embedding generation with vector candidate retrieval
 * without coupling search callers to low-level model inference or index management.
 */
interface SemanticSearchService {
    /**
     * Executes a semantic search query against the configured vector space.
     *
     * @param query Natural-language search text.
     * @param topK Maximum number of candidates to retrieve (must be > 0).
     * @param minSimilarity Minimum cosine similarity threshold (default -1.0f accepts all candidates).
     * @param targetType Semantic representation type/modality (defaults to [SemanticRepresentationType.CONTENT]).
     * @param expectedDescriptor Optional descriptor constraint to verify vector space compatibility.
     * @return [SemanticSearchResult] containing ranked candidates or error details.
     */
    suspend fun search(
        query: String,
        topK: Int = 20,
        minSimilarity: Float = -1.0f,
        targetType: SemanticRepresentationType = SemanticRepresentationType.CONTENT,
        expectedDescriptor: EmbeddingModelDescriptor? = null
    ): SemanticSearchResult

    /**
     * Returns true if the underlying embedding provider is initialized and ready for inference.
     */
    fun isReady(): Boolean

    /**
     * Returns the number of indexed candidates available in the specified vector space.
     */
    fun getIndexSize(
        targetType: SemanticRepresentationType = SemanticRepresentationType.CONTENT,
        descriptor: EmbeddingModelDescriptor? = null
    ): Int
}

/**
 * Production implementation of [SemanticSearchService].
 *
 * Coordinates query vector generation through an injected [EmbeddingProvider] (e.g. MiniLM),
 * enforces descriptor compatibility and modality boundaries, and queries the [SemanticCandidateRetriever].
 *
 * Guarantees:
 * - Deterministic, score-descending candidate ranking with stable tie-breaking.
 * - Strict isolation across modalities and model versions.
 * - Explicit error handling without silent mock fallback in production.
 * - Accurate latency measurement and provenance tracking.
 */
class DefaultSemanticSearchService(
    private val embeddingProvider: EmbeddingProvider,
    private val candidateRetriever: SemanticCandidateRetriever
) : SemanticSearchService {

    override fun isReady(): Boolean = embeddingProvider.isReady()

    override fun getIndexSize(
        targetType: SemanticRepresentationType,
        descriptor: EmbeddingModelDescriptor?
    ): Int {
        val targetDescriptor = descriptor ?: embeddingProvider.descriptor
        return candidateRetriever.getIndexSize(targetType, targetDescriptor)
    }

    override suspend fun search(
        query: String,
        topK: Int,
        minSimilarity: Float,
        targetType: SemanticRepresentationType,
        expectedDescriptor: EmbeddingModelDescriptor?
    ): SemanticSearchResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext SemanticSearchResult(
                query = query,
                candidates = emptyList(),
                modelDescriptor = embeddingProvider.descriptor,
                representationType = targetType,
                latencyMs = 0L,
                totalIndexedCandidates = getIndexSize(targetType, expectedDescriptor),
                isSuccess = false,
                errorMessage = "Search query cannot be blank."
            )
        }

        if (topK <= 0) {
            return@withContext SemanticSearchResult(
                query = query,
                candidates = emptyList(),
                modelDescriptor = embeddingProvider.descriptor,
                representationType = targetType,
                latencyMs = 0L,
                totalIndexedCandidates = getIndexSize(targetType, expectedDescriptor),
                isSuccess = false,
                errorMessage = "topK must be greater than 0 (got $topK)."
            )
        }

        // 1. Modality and Model Descriptor Compatibility Verification
        val providerDescriptor = embeddingProvider.descriptor

        if (!embeddingProvider.supportedTypes.contains(targetType)) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext SemanticSearchResult(
                query = query,
                candidates = emptyList(),
                modelDescriptor = providerDescriptor,
                representationType = targetType,
                latencyMs = elapsed,
                totalIndexedCandidates = 0,
                isSuccess = false,
                errorMessage = "Target modality '$targetType' is not supported by embedding provider '${providerDescriptor.modelId}'."
            )
        }

        if (expectedDescriptor != null && !expectedDescriptor.isCompatibleWith(providerDescriptor)) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext SemanticSearchResult(
                query = query,
                candidates = emptyList(),
                modelDescriptor = providerDescriptor,
                representationType = targetType,
                latencyMs = elapsed,
                totalIndexedCandidates = 0,
                isSuccess = false,
                errorMessage = "Expected model descriptor ($expectedDescriptor) is incompatible with active provider descriptor ($providerDescriptor)."
            )
        }

        // 2. Readiness Check
        if (!embeddingProvider.isReady()) {
            val elapsed = System.currentTimeMillis() - startTime
            return@withContext SemanticSearchResult(
                query = query,
                candidates = emptyList(),
                modelDescriptor = providerDescriptor,
                representationType = targetType,
                latencyMs = elapsed,
                totalIndexedCandidates = getIndexSize(targetType, providerDescriptor),
                isSuccess = false,
                errorMessage = "Embedding provider '${providerDescriptor.modelId}' is not ready for inference."
            )
        }

        // 3. Query Vector Generation
        val queryHash = computeSha256(trimmedQuery)
        val queryMediaId = "query_${UUID.randomUUID().toString().take(8)}"
        val input = SemanticInput.Text(trimmedQuery, targetType)

        val embeddingResult = embeddingProvider.generateEmbedding(
            mediaId = queryMediaId,
            input = input,
            sourceDataHash = queryHash
        )

        val queryRepresentation = when (embeddingResult) {
            is EmbeddingResult.Success -> embeddingResult.representation
            is EmbeddingResult.Failure -> {
                val elapsed = System.currentTimeMillis() - startTime
                return@withContext SemanticSearchResult(
                    query = query,
                    candidates = emptyList(),
                    modelDescriptor = providerDescriptor,
                    representationType = targetType,
                    latencyMs = elapsed,
                    totalIndexedCandidates = getIndexSize(targetType, providerDescriptor),
                    isSuccess = false,
                    errorMessage = "Embedding generation failed: [${embeddingResult.errorCode}] ${embeddingResult.message}"
                )
            }
        }

        // 4. Candidate Retrieval against typed Vector Index
        val candidates = candidateRetriever.retrieveCandidates(
            queryVector = queryRepresentation.vector,
            type = targetType,
            descriptor = providerDescriptor,
            topK = topK,
            minSimilarity = minSimilarity
        )

        val indexSize = candidateRetriever.getIndexSize(targetType, providerDescriptor)
        val elapsed = System.currentTimeMillis() - startTime

        SemanticSearchResult(
            query = query,
            candidates = candidates,
            modelDescriptor = providerDescriptor,
            representationType = targetType,
            latencyMs = elapsed,
            totalIndexedCandidates = indexSize,
            isSuccess = true,
            errorMessage = null
        )
    }

    private fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

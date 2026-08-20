package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MobileCLIPVisualRetrieverTest {

    private val descriptor = EmbeddingModelDescriptor("mobileclip", 1, 512, SemanticRepresentationType.VISUAL)

    @Test
    fun testRetrieveVisualCandidates_CallsServiceWithVisualModality() = runBlocking {
        val fakeService = object : SemanticSearchService {
            var capturedType: SemanticRepresentationType? = null
            
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                capturedType = targetType
                return SemanticSearchResult(
                    query = query,
                    candidates = listOf(
                        SemanticRetrievalCandidate("media_v1", "rep_v1", 0.9f, SemanticRepresentationType.VISUAL, descriptor, 1.0f)
                    ),
                    modelDescriptor = descriptor,
                    representationType = SemanticRepresentationType.VISUAL,
                    latencyMs = 1L,
                    totalIndexedCandidates = 1,
                    isSuccess = true
                )
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 1
        }

        val retriever = DefaultMobileCLIPVisualRetriever(fakeService)
        val items = retriever.retrieveVisualCandidates("sunset", 5, 0.5f)

        assertEquals(SemanticRepresentationType.VISUAL, fakeService.capturedType)
        assertEquals(1, items.size)
        assertEquals("media_v1", items[0].mediaId)
        assertEquals(0.9f, items[0].rawScore)
        assertEquals(1, items[0].rank)
        assertEquals("VISUAL", items[0].metadata["representationType"])
    }

    @Test
    fun testRetrieveVisualCandidates_HandlesServiceFailure() = runBlocking {
        val failingService = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                return SemanticSearchResult(query, emptyList(), descriptor, targetType, 0L, 0, false, "Inference error")
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val retriever = DefaultMobileCLIPVisualRetriever(failingService)
        val items = retriever.retrieveVisualCandidates("sunset", 5, 0.5f)

        assertTrue(items.isEmpty())
    }

    @Test
    fun testRetrieveVisualCandidates_HandlesException() = runBlocking {
        val crashingService = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                throw RuntimeException("Crash")
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val retriever = DefaultMobileCLIPVisualRetriever(crashingService)
        val items = retriever.retrieveVisualCandidates("sunset", 5, 0.5f)

        assertTrue(items.isEmpty())
    }
}

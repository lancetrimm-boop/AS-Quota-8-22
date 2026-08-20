package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MobileCLIPHybridSearchTest {

    private val minilmDescriptor = EmbeddingModelDescriptor("minilm", 1, 384, SemanticRepresentationType.CONTENT)
    private val mobileClipDescriptor = EmbeddingModelDescriptor("mobileclip", 1, 512, SemanticRepresentationType.VISUAL)

    private val fakeLexical = object : LexicalCandidateRetriever {
        override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> {
            return listOf(
                RankedChannelItem("media_1", 0.9f, 1),
                RankedChannelItem("media_shared", 0.8f, 2)
            )
        }
    }

    private val fakeSemantic = object : SemanticSearchService {
        override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
            return SemanticSearchResult(
                query = query,
                candidates = listOf(
                    SemanticRetrievalCandidate("media_2", "rep_2", 0.85f, SemanticRepresentationType.CONTENT, minilmDescriptor, 1.0f),
                    RankedChannelItem("media_shared", 0.75f, 2).let { 
                         SemanticRetrievalCandidate(it.mediaId, "rep_shared", it.rawScore, SemanticRepresentationType.CONTENT, minilmDescriptor, 1.0f)
                    }
                ),
                modelDescriptor = minilmDescriptor,
                representationType = SemanticRepresentationType.CONTENT,
                latencyMs = 5L,
                totalIndexedCandidates = 100,
                isSuccess = true
            )
        }
        override fun isReady(): Boolean = true
        override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 100
    }

    private val fakeVisual = object : MobileCLIPVisualRetriever {
        override fun isReady(): Boolean = true
        override suspend fun retrieveVisualCandidates(query: String, topK: Int, minSimilarity: Float): List<RankedChannelItem> {
            return listOf(
                RankedChannelItem("media_shared", 0.95f, 1),
                RankedChannelItem("media_3", 0.7f, 2)
            )
        }
    }

    @Test
    fun testThreeChannelFusion_CombinesAllSignals() = runBlocking {
        val engine = DefaultHybridSearchEngine(fakeSemantic, fakeLexical, fakeVisual)
        val result = engine.search("sunset query", HybridSearchConfig())

        assertTrue(result.isSuccess)
        // media_shared should be very high because it appears in ALL 3 channels
        assertEquals("media_shared", result.candidates[0].mediaId)
        assertTrue(result.candidates[0].isMultiChannelMatch)
        assertEquals(3, result.candidates[0].channelRanks.size)

        // Verify other items are present
        val mediaIds = result.candidates.map { it.mediaId }
        assertTrue(mediaIds.contains("media_1"))
        assertTrue(mediaIds.contains("media_2"))
        assertTrue(mediaIds.contains("media_3"))
        
        assertEquals(4, result.candidates.size)
        assertEquals(3, result.channelCandidateCounts.size)
    }

    @Test
    fun testVisualEmpty_GracefulFallback() = runBlocking {
        val emptyVisual = object : MobileCLIPVisualRetriever {
            override fun isReady(): Boolean = true
            override suspend fun retrieveVisualCandidates(query: String, topK: Int, minSimilarity: Float): List<RankedChannelItem> = emptyList()
        }

        val engine = DefaultHybridSearchEngine(fakeSemantic, fakeLexical, emptyVisual)
        val result = engine.search("query", HybridSearchConfig())

        assertTrue(result.isSuccess)
        assertEquals(3, result.candidates.size) // media_1, media_2, media_shared
        assertFalse(result.channelCandidateCounts.containsKey(SearchChannel.SEMANTIC_VISUAL))
    }

    @Test
    fun testVisualFailure_Isolation() = runBlocking {
        val failingVisual = object : MobileCLIPVisualRetriever {
            override fun isReady(): Boolean = true
            override suspend fun retrieveVisualCandidates(query: String, topK: Int, minSimilarity: Float): List<RankedChannelItem> {
                throw RuntimeException("Inference failure")
            }
        }

        val engine = DefaultHybridSearchEngine(fakeSemantic, fakeLexical, failingVisual)
        val result = engine.search("query", HybridSearchConfig())

        assertTrue("Search should succeed despite visual failure", result.isSuccess)
        assertTrue(result.candidates.isNotEmpty())
    }

    @Test
    fun testMobileCLIPUnready_Isolation() = runBlocking {
        val unreadyVisual = object : MobileCLIPVisualRetriever {
            override fun isReady(): Boolean = false
            override suspend fun retrieveVisualCandidates(query: String, topK: Int, minSimilarity: Float): List<RankedChannelItem> {
                return listOf(RankedChannelItem("should_not_see_this", 1.0f, 1))
            }
        }

        val engine = DefaultHybridSearchEngine(fakeSemantic, fakeLexical, unreadyVisual)
        val result = engine.search("query", HybridSearchConfig())

        val mediaIds = result.candidates.map { it.mediaId }
        assertFalse(mediaIds.contains("should_not_see_this"))
    }

    @Test
    fun testDimensionIsolation_Verification() = runBlocking {
        // This test verifies that we are conceptually treating channels separately.
        // The implementation already calls semanticService for CONTENT and visualRetriever for VISUAL.
        
        val capturingSemantic = object : SemanticSearchService {
            var lastType: SemanticRepresentationType? = null
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                lastType = targetType
                return fakeSemantic.search(query, topK, minSimilarity, targetType, expectedDescriptor)
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val engine = DefaultHybridSearchEngine(capturingSemantic, fakeLexical, fakeVisual)
        engine.search("query", HybridSearchConfig())

        assertEquals(SemanticRepresentationType.CONTENT, capturingSemantic.lastType)
    }
}

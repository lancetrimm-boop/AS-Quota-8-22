package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HybridSearchEngineTest {

    // =========================================================================
    // 1. PURE RECIPROCAL RANK FUSION MATHEMATICS & RANKING TESTS
    // =========================================================================

    @Test
    fun testReciprocalRankFusion_MultiChannelOverlap_ComputesExactRrfScore() {
        val keywordItems = listOf(
            RankedChannelItem("media_alpha", 0.95f, 1),
            RankedChannelItem("media_beta", 0.80f, 2)
        )
        val semanticItems = listOf(
            RankedChannelItem("media_gamma", 0.88f, 1),
            RankedChannelItem("media_alpha", 0.85f, 2)
        )

        val channelMap = mapOf(
            SearchChannel.KEYWORD to keywordItems,
            SearchChannel.SEMANTIC_CONTENT to semanticItems
        )

        val config = HybridSearchConfig(
            rrfConstantK = 60,
            channelWeights = mapOf(
                SearchChannel.KEYWORD to 0.5,
                SearchChannel.SEMANTIC_CONTENT to 0.5
            ),
            topK = 10
        )

        val fused = ReciprocalRankFusion.fuse(channelMap, config)

        assertEquals(3, fused.size)

        // media_alpha: 0.5 / (60 + 1) + 0.5 / (60 + 2) = 0.5/61 + 0.5/62 ~= 0.01626124
        // media_gamma: 0.5 / (60 + 1) = 0.5/61 ~= 0.00819672
        // media_beta: 0.5 / (60 + 2) = 0.5/62 ~= 0.00806451

        assertEquals("media_alpha", fused[0].mediaId)
        assertTrue("media_alpha should be multi-channel match", fused[0].isMultiChannelMatch)
        assertEquals(2, fused[0].channelRanks.size)
        assertEquals(1, fused[0].channelRanks[SearchChannel.KEYWORD])
        assertEquals(2, fused[0].channelRanks[SearchChannel.SEMANTIC_CONTENT])

        assertEquals("media_gamma", fused[1].mediaId)
        assertFalse(fused[1].isMultiChannelMatch)
        assertEquals(1, fused[1].channelRanks[SearchChannel.SEMANTIC_CONTENT])

        assertEquals("media_beta", fused[2].mediaId)
        assertFalse(fused[2].isMultiChannelMatch)
        assertEquals(2, fused[2].channelRanks[SearchChannel.KEYWORD])

        // Verify strictly descending RRF scores
        assertTrue(fused[0].rrfScore > fused[1].rrfScore)
        assertTrue(fused[1].rrfScore > fused[2].rrfScore)
    }

    @Test
    fun testReciprocalRankFusion_SingleChannelOnly_PreservesOrder() {
        val keywordItems = listOf(
            RankedChannelItem("media_1", 10.0f, 1),
            RankedChannelItem("media_2", 8.0f, 2),
            RankedChannelItem("media_3", 5.0f, 3)
        )

        val channelMap = mapOf(SearchChannel.KEYWORD to keywordItems)
        val fused = ReciprocalRankFusion.fuse(channelMap, HybridSearchConfig())

        assertEquals(3, fused.size)
        assertEquals("media_1", fused[0].mediaId)
        assertEquals("media_2", fused[1].mediaId)
        assertEquals("media_3", fused[2].mediaId)
    }

    @Test
    fun testReciprocalRankFusion_DeterministicTieBreaking_AlphabeticalByMediaId() {
        // Two distinct items with identical single-channel ranks and scores
        val itemsA = listOf(RankedChannelItem("media_zulu", 1.0f, 1))
        val itemsB = listOf(RankedChannelItem("media_bravo", 1.0f, 1))

        val channelMap = mapOf(
            SearchChannel.KEYWORD to itemsA,
            SearchChannel.SEMANTIC_CONTENT to itemsB
        )

        // Equal weight to both channels -> identical RRF score 0.5 / (60 + 1)
        val config = HybridSearchConfig(
            rrfConstantK = 60,
            channelWeights = mapOf(
                SearchChannel.KEYWORD to 0.5,
                SearchChannel.SEMANTIC_CONTENT to 0.5
            )
        )

        val fused = ReciprocalRankFusion.fuse(channelMap, config)

        assertEquals(2, fused.size)
        assertEquals(fused[0].rrfScore, fused[1].rrfScore, 1e-9)
        // Deterministic tie break: 'media_bravo' < 'media_zulu'
        assertEquals("media_bravo", fused[0].mediaId)
        assertEquals("media_zulu", fused[1].mediaId)
    }

    @Test
    fun testReciprocalRankFusion_TopKLimit_TruncatesExcessItems() {
        val items = (1..20).map { i ->
            RankedChannelItem("media_$i", (21 - i).toFloat(), i)
        }

        val channelMap = mapOf(SearchChannel.KEYWORD to items)
        val config = HybridSearchConfig(topK = 5)

        val fused = ReciprocalRankFusion.fuse(channelMap, config)

        assertEquals(5, fused.size)
        assertEquals("media_1", fused[0].mediaId)
        assertEquals("media_5", fused[4].mediaId)
    }

    @Test
    fun testReciprocalRankFusion_EmptyInput_ReturnsEmptyList() {
        val fused = ReciprocalRankFusion.fuse(emptyMap())
        assertTrue(fused.isEmpty())
    }

    // =========================================================================
    // 2. CONFIGURATION VALIDATION TESTS
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testHybridSearchConfig_ZeroK_ThrowsException() {
        HybridSearchConfig(rrfConstantK = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testHybridSearchConfig_NegativeK_ThrowsException() {
        HybridSearchConfig(rrfConstantK = -10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testHybridSearchConfig_ZeroTopK_ThrowsException() {
        HybridSearchConfig(topK = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testHybridSearchConfig_NegativeWeight_ThrowsException() {
        HybridSearchConfig(channelWeights = mapOf(SearchChannel.KEYWORD to -0.5))
    }

    // =========================================================================
    // 3. HYBRID SEARCH ENGINE COORDINATOR TESTS
    // =========================================================================

    @Test
    fun testHybridSearchEngine_EndToEndSuccessfulSearch_CombinesBothChannels() = runBlocking {
        val descriptor = EmbeddingModelDescriptor("test-model", 1, 128, SemanticRepresentationType.CONTENT)

        val fakeLexical = object : LexicalCandidateRetriever {
            override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> {
                return listOf(
                    RankedChannelItem("item_lex_1", 10.0f, 1),
                    RankedChannelItem("item_shared", 8.0f, 2)
                )
            }
        }

        val fakeSemantic = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                return SemanticSearchResult(
                    query = query,
                    candidates = listOf(
                        SemanticRetrievalCandidate("item_shared", "rep_1", 0.92f, SemanticRepresentationType.CONTENT, descriptor, 1.0f),
                        SemanticRetrievalCandidate("item_sem_2", "rep_2", 0.85f, SemanticRepresentationType.CONTENT, descriptor, 1.0f)
                    ),
                    modelDescriptor = descriptor,
                    representationType = targetType,
                    latencyMs = 12L,
                    totalIndexedCandidates = 10,
                    isSuccess = true
                )
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 10
        }

        val engine = DefaultHybridSearchEngine(fakeSemantic, fakeLexical)
        assertTrue(engine.isSemanticReady())

        val result = engine.search("synthwave retro music")

        assertTrue(result.isSuccess)
        assertNull(result.errorMessage)
        assertEquals("synthwave retro music", result.query)
        assertEquals(3, result.candidates.size)
        assertEquals(3, result.totalCandidatesConsidered)
        assertEquals(2, result.channelCandidateCounts[SearchChannel.KEYWORD])
        assertEquals(2, result.channelCandidateCounts[SearchChannel.SEMANTIC_CONTENT])

        // item_shared appeared in both keyword (rank 2) and semantic (rank 1) -> must be rank 1 in fused result
        assertEquals("item_shared", result.candidates[0].mediaId)
        assertTrue(result.candidates[0].isMultiChannelMatch)
    }

    @Test
    fun testHybridSearchEngine_BlankQuery_ReturnsFailure() = runBlocking {
        val engine = DefaultHybridSearchEngine(
            object : SemanticSearchService {
                override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult = throw UnsupportedOperationException()
                override fun isReady(): Boolean = true
                override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
            },
            object : LexicalCandidateRetriever {
                override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> = emptyList()
            }
        )

        val result = engine.search("   ")
        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun testHybridSearchEngine_SemanticFailure_GracefulFallbackToKeyword() = runBlocking {
        val descriptor = EmbeddingModelDescriptor("test-model", 1, 128, SemanticRepresentationType.CONTENT)

        val fakeLexical = object : LexicalCandidateRetriever {
            override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> {
                return listOf(RankedChannelItem("fallback_media", 5.0f, 1))
            }
        }

        val failingSemantic = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                return SemanticSearchResult(
                    query = query,
                    candidates = emptyList(),
                    modelDescriptor = descriptor,
                    representationType = targetType,
                    latencyMs = 2L,
                    totalIndexedCandidates = 0,
                    isSuccess = false,
                    errorMessage = "Embedding provider uninitialized"
                )
            }
            override fun isReady(): Boolean = false
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val engine = DefaultHybridSearchEngine(failingSemantic, fakeLexical)
        val result = engine.search("any valid query")

        assertTrue("Search should still succeed via keyword fallback", result.isSuccess)
        assertEquals(1, result.candidates.size)
        assertEquals("fallback_media", result.candidates[0].mediaId)
    }

    @Test
    fun testHybridSearchEngine_NoMatchesInAnyChannel_ReturnsEmptyResults() = runBlocking {
        val descriptor = EmbeddingModelDescriptor("test-model", 1, 128, SemanticRepresentationType.CONTENT)

        val emptyLexical = object : LexicalCandidateRetriever {
            override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> = emptyList()
        }

        val emptySemantic = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                return SemanticSearchResult(
                    query = query,
                    candidates = emptyList(),
                    modelDescriptor = descriptor,
                    representationType = targetType,
                    latencyMs = 1L,
                    totalIndexedCandidates = 0,
                    isSuccess = true
                )
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val engine = DefaultHybridSearchEngine(emptySemantic, emptyLexical)
        val result = engine.search("unmatched query string")

        assertTrue(result.isSuccess)
        assertTrue(result.candidates.isEmpty())
        assertEquals(0, result.totalCandidatesConsidered)
    }
}

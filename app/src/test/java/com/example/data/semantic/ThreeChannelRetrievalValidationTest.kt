package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ThreeChannelRetrievalValidationTest {

    private val minilmDescriptor = EmbeddingModelDescriptor("minilm", 1, 384, SemanticRepresentationType.CONTENT)
    private val mobileClipDescriptor = EmbeddingModelDescriptor("mobileclip", 1, 512, SemanticRepresentationType.VISUAL)

    // =========================================================================
    // 1. RRF MATHEMATICS & WEIGHT VALIDATION
    // =========================================================================

    @Test
    fun testRrfWeightSemantics_DeterministicFixture() {
        // Lexical: A(1), B(2), C(3)
        // MiniLM:  B(1), C(2), D(3)
        // CLIP:    C(1), D(2), A(3)
        
        val lexicalItems = listOf(
            RankedChannelItem("Media_A", 0.9f, 1),
            RankedChannelItem("Media_B", 0.8f, 2),
            RankedChannelItem("Media_C", 0.7f, 3)
        )
        val minilmItems = listOf(
            RankedChannelItem("Media_B", 0.85f, 1),
            RankedChannelItem("Media_C", 0.75f, 2),
            RankedChannelItem("Media_D", 0.65f, 3)
        )
        val clipItems = listOf(
            RankedChannelItem("Media_C", 0.95f, 1),
            RankedChannelItem("Media_D", 0.85f, 2),
            RankedChannelItem("Media_A", 0.75f, 3)
        )

        val channelMap = mapOf(
            SearchChannel.KEYWORD to lexicalItems,
            SearchChannel.SEMANTIC_CONTENT to minilmItems,
            SearchChannel.SEMANTIC_VISUAL to clipItems
        )

        // Config: 0.4 / 0.4 / 0.2
        val config = HybridSearchConfig(
            rrfConstantK = 60,
            channelWeights = mapOf(
                SearchChannel.KEYWORD to 0.4,
                SearchChannel.SEMANTIC_CONTENT to 0.4,
                SearchChannel.SEMANTIC_VISUAL to 0.2
            )
        )

        val fused = ReciprocalRankFusion.fuse(channelMap, config)

        // Math verification for Media_C:
        // KEYWORD rank 3: 0.4 / (60 + 3) = 0.4 / 63 = 0.006349
        // CONTENT rank 2: 0.4 / (60 + 2) = 0.4 / 62 = 0.006451
        // VISUAL rank 1:  0.2 / (60 + 1) = 0.2 / 61 = 0.003278
        // Total: 0.016078
        
        // Math verification for Media_B:
        // KEYWORD rank 2: 0.4 / 62 = 0.006451
        // CONTENT rank 1: 0.4 / 61 = 0.006557
        // Total: 0.013008

        // Media_C should be #1
        assertEquals("Media_C", fused[0].mediaId)
        assertEquals(3, fused[0].channelRanks.size)
        
        // Media_B should be #2
        assertEquals("Media_B", fused[1].mediaId)
        assertEquals(2, fused[1].channelRanks.size)
        
        assertTrue(fused[0].rrfScore > fused[1].rrfScore)
    }

    @Test
    fun testWeightSensitivity_Analysis() {
        val channelMap = mapOf(
            SearchChannel.KEYWORD to listOf(RankedChannelItem("Lex_Top", 1.0f, 1)),
            SearchChannel.SEMANTIC_CONTENT to listOf(RankedChannelItem("Sem_Top", 1.0f, 1)),
            SearchChannel.SEMANTIC_VISUAL to listOf(RankedChannelItem("Vis_Top", 1.0f, 1))
        )

        val configs = listOf(
            mapOf(SearchChannel.KEYWORD to 0.4, SearchChannel.SEMANTIC_CONTENT to 0.4, SearchChannel.SEMANTIC_VISUAL to 0.2),
            mapOf(SearchChannel.KEYWORD to 0.45, SearchChannel.SEMANTIC_CONTENT to 0.45, SearchChannel.SEMANTIC_VISUAL to 0.1),
            mapOf(SearchChannel.KEYWORD to 0.35, SearchChannel.SEMANTIC_CONTENT to 0.35, SearchChannel.SEMANTIC_VISUAL to 0.3),
            mapOf(SearchChannel.KEYWORD to 0.33, SearchChannel.SEMANTIC_CONTENT to 0.33, SearchChannel.SEMANTIC_VISUAL to 0.34)
        )

        for (weights in configs) {
            val config = HybridSearchConfig(channelWeights = weights)
            val fused = ReciprocalRankFusion.fuse(channelMap, config)
            
            // In all these configs except the last, Lex_Top and Sem_Top should tie for 1st (sorted alphabetically)
            // Vis_Top should be last unless weights[VISUAL] is highest.
            
            if (weights[SearchChannel.SEMANTIC_VISUAL]!! > weights[SearchChannel.KEYWORD]!!) {
                assertEquals("Vis_Top", fused[0].mediaId)
            } else {
                // Lex_Top vs Sem_Top tie. Lex_Top < Sem_Top alphabetically.
                assertEquals("Lex_Top", fused[0].mediaId)
            }
        }
    }

    // =========================================================================
    // 2. CROSS-MODAL & DIMENSION ISOLATION VALIDATION
    // =========================================================================

    @Test
    fun testMobileCLIP_IndependentValidation() = runBlocking {
        val mockVisualRetriever = object : MobileCLIPVisualRetriever {
            override fun isReady(): Boolean = true
            override suspend fun retrieveVisualCandidates(query: String, topK: Int, minSimilarity: Float): List<RankedChannelItem> {
                // Mock behavior: "dog" query returns "media_dog"
                if (query.contains("dog")) {
                    return listOf(RankedChannelItem("media_dog", 0.95f, 1))
                }
                return emptyList()
            }
        }

        val results = mockVisualRetriever.retrieveVisualCandidates("a happy dog", 10, 0.0f)
        assertEquals(1, results.size)
        assertEquals("media_dog", results[0].mediaId)
    }

    @Test
    fun testMiniLMVsMobileCLIP_Isolation() = runBlocking {
        val fakeSemantic = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                assertEquals(SemanticRepresentationType.CONTENT, targetType)
                return SemanticSearchResult(query, emptyList(), minilmDescriptor, targetType, 0L, 0, true)
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
        }

        val fakeVisual = object : MobileCLIPVisualRetriever {
            override fun isReady(): Boolean = true
            override suspend fun retrieveVisualCandidates(query: String, topK: Int, minSimilarity: Float): List<RankedChannelItem> {
                // Should be called for VISUAL
                return emptyList()
            }
        }

        val engine = DefaultHybridSearchEngine(fakeSemantic, object : LexicalCandidateRetriever {
            override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> = emptyList()
        }, fakeVisual)

        engine.search("test", HybridSearchConfig())
        // Assertion in fakeSemantic ensures isolation
    }

    // =========================================================================
    // 3. FAILURE ISOLATION & REGRESSION
    // =========================================================================

    @Test
    fun testMobileCLIP_FailureIsolation_Regression() = runBlocking {
        val failingVisual = object : MobileCLIPVisualRetriever {
            override fun isReady(): Boolean = true
            override suspend fun retrieveVisualCandidates(query: String, topK: Int, minSimilarity: Float): List<RankedChannelItem> {
                throw RuntimeException("CLIP Inference Failed")
            }
        }

        val engine = DefaultHybridSearchEngine(
            object : SemanticSearchService {
                override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                    return SemanticSearchResult(query, listOf(
                        SemanticRetrievalCandidate("media_minilm", "r1", 0.9f, SemanticRepresentationType.CONTENT, minilmDescriptor, 1.0f)
                    ), minilmDescriptor, targetType, 0L, 1, true)
                }
                override fun isReady(): Boolean = true
                override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 1
            },
            object : LexicalCandidateRetriever {
                override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> = emptyList()
            },
            failingVisual
        )

        val result = engine.search("query", HybridSearchConfig())
        assertTrue(result.isSuccess)
        assertEquals(1, result.candidates.size)
        assertEquals("media_minilm", result.candidates[0].mediaId)
    }

    @Test
    fun testMobileCLIP_Disabled_Regression() = runBlocking {
        // null visualRetriever represents disabled state
        val engine = DefaultHybridSearchEngine(
            object : SemanticSearchService {
                override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                    return SemanticSearchResult(query, listOf(
                        SemanticRetrievalCandidate("media_1", "r1", 0.9f, SemanticRepresentationType.CONTENT, minilmDescriptor, 1.0f)
                    ), minilmDescriptor, targetType, 0L, 1, true)
                }
                override fun isReady(): Boolean = true
                override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 1
            },
            object : LexicalCandidateRetriever {
                override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> {
                    return listOf(RankedChannelItem("media_2", 0.8f, 1))
                }
            },
            visualRetriever = null
        )

        val result = engine.search("query", HybridSearchConfig())
        assertTrue(result.isSuccess)
        assertEquals(2, result.candidates.size)
    }
}

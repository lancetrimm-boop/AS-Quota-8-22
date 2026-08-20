package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PersonalizationHybridSearchTest {

    private val minilmDescriptor = EmbeddingModelDescriptor("minilm", 1, 384, SemanticRepresentationType.CONTENT)
    
    private val fakeLexical = object : LexicalCandidateRetriever {
        override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> {
            return listOf(RankedChannelItem("media_lex", 0.9f, 1))
        }
    }

    private val fakeSemantic = object : SemanticSearchService {
        override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
            return SemanticSearchResult(query, emptyList(), minilmDescriptor, targetType, 0L, 0, true)
        }
        override fun isReady(): Boolean = true
        override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 0
    }

    @Test
    fun testPersonalizationInfluence() = runBlocking {
        // We have two candidates from lexical channel
        val lexicalItems = listOf(
            RankedChannelItem("media_low_p", 0.9f, 1),
            RankedChannelItem("media_high_p", 0.85f, 2)
        )
        
        val lexicalRetriever = object : LexicalCandidateRetriever {
            override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> = lexicalItems
        }
        
        // Personalization scorer: media_high_p is much more preferred
        val scorer = object : PersonalizationScorer {
            override fun score(mediaId: String): Float = if (mediaId == "media_high_p") 1.0f else 0.1f
        }
        
        val config = HybridSearchConfig(
            channelWeights = mapOf(
                SearchChannel.KEYWORD to 0.5,
                SearchChannel.PERSONALIZED to 0.5
            )
        )
        
        val engine = DefaultHybridSearchEngine(fakeSemantic, lexicalRetriever, personalizationScorer = scorer)
        val result = engine.search("query", config)
        
        // media_high_p should win because of high personalization, despite being rank 2 in lexical
        // RRF Math:
        // media_low_p:  0.5 / (60+1) [Lex] + 0.5 / (60+2) [Pers] = 0.00819 + 0.00806 = 0.01625
        // media_high_p: 0.5 / (60+2) [Lex] + 0.5 / (60+1) [Pers] = 0.00806 + 0.00819 = 0.01625
        // Wait, they tie if weights are equal and ranks are swapped. 
        // Let's make personalization stronger to see the effect.
        
        val strongConfig = HybridSearchConfig(
            channelWeights = mapOf(
                SearchChannel.KEYWORD to 0.3,
                SearchChannel.PERSONALIZED to 0.7
            )
        )
        val resultStrong = engine.search("query", strongConfig)
        assertEquals("media_high_p", resultStrong.candidates[0].mediaId)
    }

    @Test
    fun testNoPersonalizationMutation() = runBlocking {
        var mutationCount = 0
        val mutatingScorer = object : PersonalizationScorer {
            override fun score(mediaId: String): Float {
                mutationCount++
                return 0.5f
            }
        }
        
        val engine = DefaultHybridSearchEngine(fakeSemantic, fakeLexical, personalizationScorer = mutatingScorer)
        
        val config = HybridSearchConfig(
            channelWeights = mapOf(SearchChannel.KEYWORD to 1.0, SearchChannel.PERSONALIZED to 0.0)
        )
        
        engine.search("query", config)
        
        // If weight is 0.0, personalization scorer should not even be called
        assertEquals(0, mutationCount)
        
        val activeConfig = HybridSearchConfig(
            channelWeights = mapOf(SearchChannel.KEYWORD to 1.0, SearchChannel.PERSONALIZED to 1.0)
        )
        engine.search("query", activeConfig)
        assertTrue(mutationCount > 0)
    }

    @Test
    fun testMultiChannelCandidate_ScoredOnce() = runBlocking {
        var scoreCount = 0
        val countingScorer = object : PersonalizationScorer {
            override fun score(mediaId: String): Float {
                if (mediaId == "media_shared") scoreCount++
                return 0.5f
            }
        }
        
        val multiLex = object : LexicalCandidateRetriever {
            override suspend fun retrieveKeywordCandidates(query: String, topK: Int): List<RankedChannelItem> {
                return listOf(RankedChannelItem("media_shared", 0.9f, 1))
            }
        }
        
        val multiSem = object : SemanticSearchService {
            override suspend fun search(query: String, topK: Int, minSimilarity: Float, targetType: SemanticRepresentationType, expectedDescriptor: EmbeddingModelDescriptor?): SemanticSearchResult {
                return SemanticSearchResult(query, listOf(
                    SemanticRetrievalCandidate("media_shared", "r1", 0.9f, SemanticRepresentationType.CONTENT, minilmDescriptor, 1.0f)
                ), minilmDescriptor, targetType, 0L, 1, true)
            }
            override fun isReady(): Boolean = true
            override fun getIndexSize(targetType: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor?): Int = 1
        }
        
        val engine = DefaultHybridSearchEngine(multiSem, multiLex, personalizationScorer = countingScorer)
        engine.search("query", HybridSearchConfig(channelWeights = mapOf(
            SearchChannel.KEYWORD to 1.0,
            SearchChannel.SEMANTIC_CONTENT to 1.0,
            SearchChannel.PERSONALIZED to 1.0
        )))
        
        assertEquals("Should only score media_shared once", 1, scoreCount)
    }
}

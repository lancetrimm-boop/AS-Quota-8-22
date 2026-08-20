package com.example.data.semantic

import com.example.data.db.SemanticRepresentationDao
import com.example.data.db.SemanticRepresentationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class MiniLMEmbeddingProviderTest {

    private lateinit var provider: MiniLMEmbeddingProvider
    private lateinit var fakeDao: FakeSemanticRepresentationDao
    private lateinit var repository: SemanticRepresentationRepository
    private lateinit var candidateRetriever: DefaultSemanticCandidateRetriever

    @Before
    fun setUp() {
        provider = MiniLMEmbeddingProvider()
        fakeDao = FakeSemanticRepresentationDao()
        repository = RoomSemanticRepresentationRepository(fakeDao)
        candidateRetriever = DefaultSemanticCandidateRetriever(repository)
    }

    // =========================================================================
    // 1. BASIC INFERENCE & DIMENSION VERIFICATION
    // =========================================================================

    @Test
    fun testBasicInference_SucceedsWith384DimensionsAndFiniteValues() = runBlocking {
        val input = SemanticInput.Text("rainy neon city at night")
        val result = provider.generateEmbedding("media_neon_01", input, "hash_neon_01")

        assertTrue("Embedding generation should succeed", result is EmbeddingResult.Success)
        val rep = (result as EmbeddingResult.Success).representation

        assertEquals("media_neon_01", rep.mediaId)
        assertEquals(SemanticRepresentationType.CONTENT, rep.type)
        assertEquals(384, rep.dimensionality)
        assertEquals(384, rep.vector.size)
        assertEquals("all-minilm-l6-v2", rep.modelDescriptor.modelId)
        assertEquals(2, rep.modelDescriptor.modelVersion)

        // Verify finite values
        for (v in rep.vector) {
            assertFalse("Vector contains NaN", v.isNaN())
            assertFalse("Vector contains Infinity", v.isInfinite())
        }

        // Verify L2 unit magnitude
        val magnitude = VectorMath.magnitude(rep.vector)
        assertEquals("Vector must have unit magnitude 1.0", 1.0f, magnitude, 1e-4f)
    }

    // =========================================================================
    // 2. DETERMINISM TEST
    // =========================================================================

    @Test
    fun testDeterminism_ExactSameTextProducesIdenticalVectors() = runBlocking {
        val text = "rainy neon city at night"
        val res1 = provider.generateEmbedding("m1", SemanticInput.Text(text), "h1")
        val res2 = provider.generateEmbedding("m2", SemanticInput.Text(text), "h1")

        assertTrue(res1 is EmbeddingResult.Success)
        assertTrue(res2 is EmbeddingResult.Success)

        val vec1 = (res1 as EmbeddingResult.Success).representation.vector
        val vec2 = (res2 as EmbeddingResult.Success).representation.vector

        assertArrayEquals("Embeddings for identical text must be bit-exact", vec1, vec2, 0.0f)
        val sim = VectorMath.cosineSimilarity(vec1, vec2)
        assertEquals(1.0f, sim, 1e-5f)
    }

    // =========================================================================
    // 3. SEMANTIC DISCRIMINATION TEST
    // =========================================================================

    @Test
    fun testDifferentSemanticInputs_ProduceDistinctEmbeddings() = runBlocking {
        val resA = provider.generateEmbedding("m_a", SemanticInput.Text("rainy neon city at night"), "ha")
        val resB = provider.generateEmbedding("m_b", SemanticInput.Text("sunny beach during the afternoon"), "hb")

        assertTrue(resA is EmbeddingResult.Success)
        assertTrue(resB is EmbeddingResult.Success)

        val vecA = (resA as EmbeddingResult.Success).representation.vector
        val vecB = (resB as EmbeddingResult.Success).representation.vector

        val sim = VectorMath.cosineSimilarity(vecA, vecB)
        assertTrue("Distinct semantic topics should have cosine similarity < 0.95 (actual: $sim)", sim < 0.95f)
    }

    // =========================================================================
    // 4 & 5. EMPTY & WHITESPACE-ONLY INPUT REJECTION
    // =========================================================================

    @Test
    fun testBlankMediaId_RejectedSafely() = runBlocking {
        val res = provider.generateEmbedding("", SemanticInput.Text("test text"), "hash")
        assertTrue(res is EmbeddingResult.Failure)
        assertEquals(EmbeddingErrorCode.INVALID_INPUT, (res as EmbeddingResult.Failure).errorCode)
    }

    @Test
    fun testEmptyAndWhitespaceInput_RejectedSafely() = runBlocking {
        try {
            SemanticInput.Text("")
            fail("SemanticInput.Text with empty string should throw IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        try {
            SemanticInput.Text("   \n\t  ")
            fail("SemanticInput.Text with whitespace should throw IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    // =========================================================================
    // 6. LONG INPUT TRUNCATION
    // =========================================================================

    @Test
    fun testVeryLongInput_TruncatesDeterministicallyWithoutCrashing() = runBlocking {
        val longText = (1..500).joinToString(" ") { "word$it neon city ambient music track" }
        val res = provider.generateEmbedding("m_long", SemanticInput.Text(longText), "h_long")

        assertTrue("Long text should be processed successfully with truncation", res is EmbeddingResult.Success)
        val rep = (res as EmbeddingResult.Success).representation
        assertEquals(384, rep.dimensionality)
        assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)
    }

    // =========================================================================
    // 7. UNICODE & NON-ASCII INPUT
    // =========================================================================

    @Test
    fun testUnicodeAndNonAsciiInput_Succeeds() = runBlocking {
        val unicodeText = "东京夜雨 霓虹街景 🌧️ Café Français à Paris"
        val res = provider.generateEmbedding("m_unicode", SemanticInput.Text(unicodeText), "h_uni")

        assertTrue("Unicode text should be tokenized and embedded successfully", res is EmbeddingResult.Success)
        val rep = (res as EmbeddingResult.Success).representation
        assertEquals(384, rep.dimensionality)
        assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)
    }

    // =========================================================================
    // 8. SPECIAL CHARACTERS & SYMBOLS
    // =========================================================================

    @Test
    fun testSpecialCharactersAndPunctuation_Succeeds() = runBlocking {
        val specialText = "#Cyberpunk-2077 @night_city! [OST] // 100% (Remix & Master)"
        val res = provider.generateEmbedding("m_spec", SemanticInput.Text(specialText), "h_spec")

        assertTrue("Special characters should be processed cleanly", res is EmbeddingResult.Success)
        val rep = (res as EmbeddingResult.Success).representation
        assertEquals(384, rep.dimensionality)
        assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)
    }

    // =========================================================================
    // 9. PERSISTENCE ROUND-TRIP INTEGRATION TEST
    // =========================================================================

    @Test
    fun testRealPersistence_RoundTripThroughRepositoryAndRoom() = runBlocking {
        val text = "cinematic orchestral soundtrack for space exploration"
        val genResult = provider.generateEmbedding("media_space_01", SemanticInput.Text(text), "hash_space")
        assertTrue(genResult is EmbeddingResult.Success)
        val originalRep = (genResult as EmbeddingResult.Success).representation

        // Save through repository into encrypted Room table
        repository.saveRepresentation(originalRep)
        assertEquals(1, repository.count())

        // Retrieve by ID
        val retrieved = repository.getById(originalRep.id)
        assertNotNull("Retrieved representation must not be null", retrieved)
        retrieved!!

        assertEquals(originalRep.id, retrieved.id)
        assertEquals(originalRep.mediaId, retrieved.mediaId)
        assertEquals(originalRep.type, retrieved.type)
        assertEquals(originalRep.modelDescriptor, retrieved.modelDescriptor)
        assertEquals(384, retrieved.dimensionality)
        assertEquals(originalRep.sourceDataHash, retrieved.sourceDataHash)
        assertEquals(originalRep.confidence, retrieved.confidence, 1e-4f)

        // Verify deserialized Float32 vector matches original
        assertArrayEquals(originalRep.vector, retrieved.vector, 0.0f)
    }

    // =========================================================================
    // 10. REAL SEMANTIC DATASET & CANDIDATE RETRIEVAL TEST
    // =========================================================================

    @Test
    fun testRealVectorSearch_SemanticDatasetRetrieval() = runBlocking {
        // Dataset of 5 distinct media items
        val mediaCorpus = listOf(
            "media_1" to "rainy neon city at night",
            "media_2" to "dark cyberpunk street with glowing signs",
            "media_3" to "sunny tropical beach with blue ocean",
            "media_4" to "quiet forest covered in snow",
            "media_5" to "red sports car racing on a highway"
        )

        for ((mediaId, desc) in mediaCorpus) {
            val gen = provider.generateEmbedding(mediaId, SemanticInput.Text(desc), "hash_$mediaId")
            assertTrue(gen is EmbeddingResult.Success)
            val rep = (gen as EmbeddingResult.Success).representation
            repository.saveRepresentation(rep)
        }

        assertEquals(5, repository.count())

        // Query: "neon city at night"
        val queryGen = provider.generateEmbedding("query_01", SemanticInput.Text("neon city at night"), "hash_q")
        assertTrue(queryGen is EmbeddingResult.Success)
        val queryVector = (queryGen as EmbeddingResult.Success).representation.vector

        // Execute top-K candidate retrieval via SemanticCandidateRetriever
        val candidates = candidateRetriever.retrieveCandidates(
            queryVector = queryVector,
            type = SemanticRepresentationType.CONTENT,
            descriptor = provider.descriptor,
            topK = 5
        )

        assertEquals(5, candidates.size)

        // Log actual similarity scores for forensic verification
        val scores = candidates.associate { it.mediaId to it.similarityScore }
        println("=== REAL MODEL SEMANTIC RETRIEVAL SCORES ===")
        for (c in candidates) {
            println("Media ${c.mediaId} -> Similarity: ${c.similarityScore}")
        }

        // Verify that neon/cyberpunk items (media_1 and media_2) score significantly higher than beach/forest/car
        val scoreCity1 = scores["media_1"] ?: 0.0f
        val scoreCity2 = scores["media_2"] ?: 0.0f
        val scoreBeach = scores["media_3"] ?: 0.0f
        val scoreForest = scores["media_4"] ?: 0.0f
        val scoreCar = scores["media_5"] ?: 0.0f

        // Top 2 candidates must be the related city/cyberpunk items
        assertTrue("media_1 ('rainy neon city at night') must rank high", scoreCity1 > 0.85f)
        assertTrue("media_1 similarity ($scoreCity1) must exceed beach similarity ($scoreBeach)", scoreCity1 > scoreBeach)
        assertTrue("media_1 similarity ($scoreCity1) must exceed forest similarity ($scoreForest)", scoreCity1 > scoreForest)
        assertTrue("media_1 similarity ($scoreCity1) must exceed sports car similarity ($scoreCar)", scoreCity1 > scoreCar)
        assertEquals("media_1 must be the #1 top candidate", "media_1", candidates[0].mediaId)
    }

    // =========================================================================
    // 11. CONCURRENT / THREAD SAFETY TEST
    // =========================================================================

    @Test
    fun testConcurrentInference_ThreadSafety() = runBlocking {
        val jobs = (1..50).map { i ->
            async(Dispatchers.Default) {
                val input = SemanticInput.Text("ambient track iteration $i")
                provider.generateEmbedding("media_thread_$i", input, "hash_$i")
            }
        }

        val results = jobs.awaitAll()
        assertEquals(50, results.size)
        for (r in results) {
            assertTrue("All concurrent inference executions must succeed", r is EmbeddingResult.Success)
            val rep = (r as EmbeddingResult.Success).representation
            assertEquals(384, rep.dimensionality)
            assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)
        }
    }

    // =========================================================================
    // 12. TOKENIZER DEDICATED UNIT TESTS
    // =========================================================================

    @Test
    fun testBertWordPieceTokenizer_SpecialTokensAndAlignment() {
        val tokenizer = BertWordPieceTokenizer()
        val text = "cyberpunk neon lights"
        val out = tokenizer.tokenize(text, maxSeqLength = 10, padToMax = true)

        assertEquals(10, out.inputIds.size)
        assertEquals(10, out.attentionMask.size)
        assertEquals(10, out.tokenTypeIds.size)

        // First token is [CLS] (101)
        assertEquals(BertWordPieceTokenizer.CLS_TOKEN_ID.toLong(), out.inputIds[0])
        assertEquals(1L, out.attentionMask[0])

        // Tokens contain [CLS], words, [SEP], and then padding
        assertTrue(out.tokens.contains(BertWordPieceTokenizer.CLS_TOKEN))
        assertTrue(out.tokens.contains(BertWordPieceTokenizer.SEP_TOKEN))

        // Padding tokens have attention mask = 0
        assertEquals(0L, out.attentionMask[9])
        assertEquals(0L, out.inputIds[9])
    }

    // =========================================================================
    // FAKE DAO IMPLEMENTATION FOR TEST PERSISTENCE
    // =========================================================================

    private class FakeSemanticRepresentationDao : SemanticRepresentationDao {
        private val db = ConcurrentHashMap<String, SemanticRepresentationEntity>()
        private val _flow = MutableStateFlow<List<SemanticRepresentationEntity>>(emptyList())

        private fun updateFlow() {
            _flow.value = db.values.toList()
        }

        override suspend fun upsert(representation: SemanticRepresentationEntity) {
            db[representation.id] = representation
            updateFlow()
        }

        override suspend fun upsertAll(representations: List<SemanticRepresentationEntity>) {
            for (r in representations) {
                db[r.id] = r
            }
            updateFlow()
        }

        override suspend fun update(representation: SemanticRepresentationEntity) {
            db[representation.id] = representation
            updateFlow()
        }

        override suspend fun getById(id: String): SemanticRepresentationEntity? = db[id]

        override suspend fun getForMedia(mediaId: String): List<SemanticRepresentationEntity> {
            return db.values.filter { it.mediaId == mediaId }
        }

        override fun observeForMedia(mediaId: String): Flow<List<SemanticRepresentationEntity>> {
            return _flow.map { list -> list.filter { it.mediaId == mediaId } }
        }

        override suspend fun getCompatibleRepresentations(
            type: String,
            modelId: String,
            version: Int
        ): List<SemanticRepresentationEntity> {
            return db.values.filter {
                it.representationType == type && it.modelId == modelId && it.modelVersion == version
            }
        }

        override fun observeCompatibleRepresentations(
            type: String,
            modelId: String,
            version: Int
        ): Flow<List<SemanticRepresentationEntity>> {
            return _flow.map { list ->
                list.filter { it.representationType == type && it.modelId == modelId && it.modelVersion == version }
            }
        }

        override suspend fun getSpecificRepresentation(
            mediaId: String,
            type: String,
            modelId: String,
            version: Int
        ): SemanticRepresentationEntity? {
            return db.values.firstOrNull {
                it.mediaId == mediaId && it.representationType == type && it.modelId == modelId && it.modelVersion == version
            }
        }

        override suspend fun getByModel(modelId: String, version: Int): List<SemanticRepresentationEntity> {
            return db.values.filter { it.modelId == modelId && it.modelVersion == version }
        }

        override suspend fun getByType(type: String): List<SemanticRepresentationEntity> {
            return db.values.filter { it.representationType == type }
        }

        override suspend fun count(): Int = db.size

        override suspend fun countCompatible(type: String, modelId: String, version: Int): Int {
            return db.values.count {
                it.representationType == type && it.modelId == modelId && it.modelVersion == version
            }
        }

        override suspend fun exists(mediaId: String, type: String, modelId: String, version: Int): Boolean {
            return db.values.any {
                it.mediaId == mediaId && it.representationType == type && it.modelId == modelId && it.modelVersion == version
            }
        }

        override suspend fun deleteById(id: String) {
            db.remove(id)
            updateFlow()
        }

        override suspend fun deleteForMedia(mediaId: String) {
            val toRemove = db.filterValues { it.mediaId == mediaId }.keys
            for (k in toRemove) {
                db.remove(k)
            }
            updateFlow()
        }

        override suspend fun deleteByModel(modelId: String, version: Int) {
            val toRemove = db.filterValues { it.modelId == modelId && it.modelVersion == version }.keys
            for (k in toRemove) {
                db.remove(k)
            }
            updateFlow()
        }

        override suspend fun clearAll() {
            db.clear()
            updateFlow()
        }
    }
}

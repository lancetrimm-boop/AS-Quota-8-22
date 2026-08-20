package com.example.data.semantic

import com.example.data.db.SemanticRepresentationDao
import com.example.data.db.SemanticRepresentationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class SemanticSearchServiceTest {

    private lateinit var fakeDao: FakeSemanticDao
    private lateinit var repository: SemanticRepresentationRepository
    private lateinit var retriever: DefaultSemanticCandidateRetriever
    private lateinit var mockProvider: TestMockEmbeddingProvider
    private lateinit var searchService: DefaultSemanticSearchService

    private val textDescriptor128 = EmbeddingModelDescriptor(
        modelId = "aura-test-synthetic-mock",
        modelVersion = 1,
        dimensionality = 128,
        primaryType = SemanticRepresentationType.CONTENT
    )

    @Before
    fun setup() {
        fakeDao = FakeSemanticDao()
        repository = RoomSemanticRepresentationRepository(fakeDao)
        retriever = DefaultSemanticCandidateRetriever(repository)
        mockProvider = TestMockEmbeddingProvider(textDescriptor128)
        searchService = DefaultSemanticSearchService(mockProvider, retriever)
    }

    // =========================================================================
    // 1. HAPPY PATH SEARCH RETRIEVAL TESTS
    // =========================================================================

    @Test
    fun testSemanticSearch_SuccessfulRetrieval_ReturnsRankedCandidates() = runBlocking {
        // Seed 3 indexed representations
        val rep1 = mockProvider.generateEmbedding("media_1", SemanticInput.Text("sunset over ocean beach"), "h1")
        val rep2 = mockProvider.generateEmbedding("media_2", SemanticInput.Text("cyberpunk neon city night"), "h2")
        val rep3 = mockProvider.generateEmbedding("media_3", SemanticInput.Text("acoustic guitar folk music"), "h3")

        assertTrue(rep1 is EmbeddingResult.Success)
        assertTrue(rep2 is EmbeddingResult.Success)
        assertTrue(rep3 is EmbeddingResult.Success)

        repository.saveRepresentations(
            listOf(
                (rep1 as EmbeddingResult.Success).representation,
                (rep2 as EmbeddingResult.Success).representation,
                (rep3 as EmbeddingResult.Success).representation
            )
        )

        // Initialize retriever index
        retriever.initializeIndex(SemanticRepresentationType.CONTENT, textDescriptor128)
        assertEquals(3, searchService.getIndexSize(SemanticRepresentationType.CONTENT, textDescriptor128))

        // Execute query
        val result = searchService.search(
            query = "sunset ocean beach",
            topK = 5
        )

        assertTrue("Search should succeed", result.isSuccess)
        assertNull(result.errorMessage)
        assertEquals("sunset ocean beach", result.query)
        assertEquals(SemanticRepresentationType.CONTENT, result.representationType)
        assertEquals(textDescriptor128, result.modelDescriptor)
        assertEquals(3, result.totalIndexedCandidates)
        assertTrue("Latency should be non-negative", result.latencyMs >= 0)
        assertTrue("Should have retrieved matches", result.hasMatches)
        assertEquals(3, result.candidateCount)

        // Verifies candidates are ordered by similarity score descending
        val candidates = result.candidates
        for (i in 0 until candidates.size - 1) {
            assertTrue(
                "Candidates must be sorted in descending score order",
                candidates[i].similarityScore >= candidates[i + 1].similarityScore
            )
        }
    }

    @Test
    fun testSemanticSearch_EmptyIndex_ReturnsSuccessWithZeroCandidates() = runBlocking {
        val result = searchService.search("solitary wanderer in the mountains", topK = 10)

        assertTrue(result.isSuccess)
        assertNull(result.errorMessage)
        assertTrue(result.candidates.isEmpty())
        assertFalse(result.hasMatches)
        assertNull(result.topMatch)
        assertEquals(0, result.totalIndexedCandidates)
    }

    // =========================================================================
    // 2. INPUT VALIDATION & GUARD TESTS
    // =========================================================================

    @Test
    fun testSemanticSearch_BlankQuery_ReturnsFailureResult() = runBlocking {
        val blankResult1 = searchService.search("")
        assertFalse(blankResult1.isSuccess)
        assertNotNull(blankResult1.errorMessage)
        assertTrue(blankResult1.candidates.isEmpty())

        val blankResult2 = searchService.search("     ")
        assertFalse(blankResult2.isSuccess)
        assertNotNull(blankResult2.errorMessage)
        assertTrue(blankResult2.candidates.isEmpty())
    }

    @Test
    fun testSemanticSearch_InvalidTopK_ReturnsFailureResult() = runBlocking {
        val zeroK = searchService.search("test query", topK = 0)
        assertFalse(zeroK.isSuccess)
        assertNotNull(zeroK.errorMessage)
        assertTrue(zeroK.candidates.isEmpty())

        val negativeK = searchService.search("test query", topK = -10)
        assertFalse(negativeK.isSuccess)
        assertNotNull(negativeK.errorMessage)
        assertTrue(negativeK.candidates.isEmpty())
    }

    @Test
    fun testSemanticSearch_TopKTrimming_LimitsCandidateCount() = runBlocking {
        // Seed 5 items
        for (i in 1..5) {
            val rep = mockProvider.generateEmbedding("media_$i", SemanticInput.Text("sample item $i"), "h$i")
            repository.saveRepresentation((rep as EmbeddingResult.Success).representation)
        }
        retriever.initializeIndex(SemanticRepresentationType.CONTENT, textDescriptor128)

        val result = searchService.search("sample item", topK = 2)
        assertTrue(result.isSuccess)
        assertEquals(2, result.candidates.size)
        assertEquals(2, result.candidateCount)
        assertEquals(5, result.totalIndexedCandidates)
    }

    // =========================================================================
    // 3. MODALITY & DESCRIPTOR ISOLATION TESTS
    // =========================================================================

    @Test
    fun testSemanticSearch_UnsupportedModality_ReturnsFailure() = runBlocking {
        // mockProvider only supports CONTENT, request AUDIO
        val result = searchService.search(
            query = "classical piano concerto",
            targetType = SemanticRepresentationType.AUDIO
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("not supported"))
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun testSemanticSearch_IncompatibleExpectedDescriptor_ReturnsFailure() = runBlocking {
        // Expected descriptor with mismatched version
        val incompatibleDescriptor = textDescriptor128.copy(modelVersion = 2)

        val result = searchService.search(
            query = "portrait photograph",
            expectedDescriptor = incompatibleDescriptor
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("incompatible"))
        assertTrue(result.candidates.isEmpty())
    }

    // =========================================================================
    // 4. PROVIDER FAILURE & READINESS TESTS
    // =========================================================================

    @Test
    fun testSemanticSearch_UnreadyProvider_ReturnsFailure() = runBlocking {
        val unreadyProvider = object : EmbeddingProvider {
            override val descriptor: EmbeddingModelDescriptor = textDescriptor128
            override val supportedTypes: Set<SemanticRepresentationType> = setOf(SemanticRepresentationType.CONTENT)
            override fun isReady(): Boolean = false
            override fun close() {}
            override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
                return EmbeddingResult.Failure(EmbeddingErrorCode.UNINITIALIZED, "Model weights not loaded")
            }
        }

        val unreadyService = DefaultSemanticSearchService(unreadyProvider, retriever)
        assertFalse(unreadyService.isReady())

        val result = unreadyService.search("any query")
        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("not ready"))
    }

    @Test
    fun testSemanticSearch_ProviderInferenceError_PropagatesCleanly() = runBlocking {
        val failingProvider = object : EmbeddingProvider {
            override val descriptor: EmbeddingModelDescriptor = textDescriptor128
            override val supportedTypes: Set<SemanticRepresentationType> = setOf(SemanticRepresentationType.CONTENT)
            override fun isReady(): Boolean = true
            override fun close() {}
            override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
                return EmbeddingResult.Failure(EmbeddingErrorCode.INFERENCE_ERROR, "Out of memory during tensor execution")
            }
        }

        val failingService = DefaultSemanticSearchService(failingProvider, retriever)
        val result = failingService.search("trigger inference error")

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("INFERENCE_ERROR"))
        assertTrue(result.errorMessage!!.contains("Out of memory"))
    }

    // =========================================================================
    // 5. SIMILARITY THRESHOLD & DETERMINISTIC TIE-BREAKING TESTS
    // =========================================================================

    @Test
    fun testSemanticSearch_MinSimilarityThreshold_FiltersLowScoreCandidates() = runBlocking {
        val descriptor = EmbeddingModelDescriptor("test-custom", 1, 2, SemanticRepresentationType.CONTENT)
        val customIndex = InMemoryVectorIndex(descriptor)

        // Seed 3 vectors: [1, 0] (score 1.0), [0.707, 0.707] (score ~0.707), [0, 1] (score 0.0) with query [1, 0]
        val rep1 = SemanticRepresentation("r1", "m_high", SemanticRepresentationType.CONTENT, descriptor, 2, floatArrayOf(1.0f, 0.0f), "h1")
        val rep2 = SemanticRepresentation("r2", "m_mid", SemanticRepresentationType.CONTENT, descriptor, 2, floatArrayOf(0.7071f, 0.7071f), "h2")
        val rep3 = SemanticRepresentation("r3", "m_low", SemanticRepresentationType.CONTENT, descriptor, 2, floatArrayOf(0.0f, 1.0f), "h3")

        val customRetriever = object : SemanticCandidateRetriever {
            override suspend fun initializeIndex(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor) {}
            override suspend fun retrieveCandidates(queryVector: FloatArray, type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor, topK: Int, minSimilarity: Float): List<SemanticRetrievalCandidate> {
                return customIndex.query(queryVector, topK, minSimilarity)
            }
            override fun onRepresentationAdded(representation: SemanticRepresentation) {}
            override fun onRepresentationRemoved(representationId: String) {}
            override fun onMediaRemoved(mediaId: String) {}
            override fun getIndexSize(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): Int = customIndex.size
        }

        customIndex.addAll(listOf(rep1, rep2, rep3))

        // Deterministic unit vector provider
        val fixedProvider = object : EmbeddingProvider {
            override val descriptor: EmbeddingModelDescriptor = descriptor
            override val supportedTypes: Set<SemanticRepresentationType> = setOf(SemanticRepresentationType.CONTENT)
            override fun isReady(): Boolean = true
            override fun close() {}
            override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
                return EmbeddingResult.Success(
                    SemanticRepresentation("q1", mediaId, SemanticRepresentationType.CONTENT, descriptor, 2, floatArrayOf(1.0f, 0.0f), "qh")
                )
            }
        }

        val customService = DefaultSemanticSearchService(fixedProvider, customRetriever)

        // Filter with threshold 0.5 -> should only return m_high (1.0) and m_mid (0.707)
        val filteredResult = customService.search("query", minSimilarity = 0.5f)
        assertTrue(filteredResult.isSuccess)
        assertEquals(2, filteredResult.candidates.size)
        assertEquals("m_high", filteredResult.candidates[0].mediaId)
        assertEquals("m_mid", filteredResult.candidates[1].mediaId)

        // Filter with threshold 0.9 -> should only return m_high
        val strictResult = customService.search("query", minSimilarity = 0.9f)
        assertTrue(strictResult.isSuccess)
        assertEquals(1, strictResult.candidates.size)
        assertEquals("m_high", strictResult.candidates[0].mediaId)
    }

    @Test
    fun testSemanticSearch_DeterministicTieBreak_PreservesAlphabeticalMediaId() = runBlocking {
        val descriptor = EmbeddingModelDescriptor("test-tie", 1, 2, SemanticRepresentationType.CONTENT)
        val customIndex = InMemoryVectorIndex(descriptor)

        // Two identical vectors with different media IDs
        val repZ = SemanticRepresentation("rz", "media_zebra", SemanticRepresentationType.CONTENT, descriptor, 2, floatArrayOf(1.0f, 0.0f), "hz")
        val repA = SemanticRepresentation("ra", "media_apple", SemanticRepresentationType.CONTENT, descriptor, 2, floatArrayOf(1.0f, 0.0f), "ha")

        val customRetriever = object : SemanticCandidateRetriever {
            override suspend fun initializeIndex(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor) {}
            override suspend fun retrieveCandidates(queryVector: FloatArray, type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor, topK: Int, minSimilarity: Float): List<SemanticRetrievalCandidate> {
                return customIndex.query(queryVector, topK, minSimilarity)
            }
            override fun onRepresentationAdded(representation: SemanticRepresentation) {}
            override fun onRepresentationRemoved(representationId: String) {}
            override fun onMediaRemoved(mediaId: String) {}
            override fun getIndexSize(type: SemanticRepresentationType, descriptor: EmbeddingModelDescriptor): Int = customIndex.size
        }

        customIndex.addAll(listOf(repZ, repA))

        val fixedProvider = object : EmbeddingProvider {
            override val descriptor: EmbeddingModelDescriptor = descriptor
            override val supportedTypes: Set<SemanticRepresentationType> = setOf(SemanticRepresentationType.CONTENT)
            override fun isReady(): Boolean = true
            override fun close() {}
            override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
                return EmbeddingResult.Success(
                    SemanticRepresentation("q1", mediaId, SemanticRepresentationType.CONTENT, descriptor, 2, floatArrayOf(1.0f, 0.0f), "qh")
                )
            }
        }

        val customService = DefaultSemanticSearchService(fixedProvider, customRetriever)
        val result = customService.search("query")

        assertTrue(result.isSuccess)
        assertEquals(2, result.candidates.size)
        // Deterministic tie break: 'media_apple' before 'media_zebra'
        assertEquals("media_apple", result.candidates[0].mediaId)
        assertEquals("media_zebra", result.candidates[1].mediaId)
    }

    // =========================================================================
    // FAKE DAO FOR TEST FIXTURES
    // =========================================================================

    private class FakeSemanticDao : SemanticRepresentationDao {
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
            for (r in representations) db[r.id] = r
            updateFlow()
        }

        override suspend fun update(representation: SemanticRepresentationEntity) {
            db[representation.id] = representation
            updateFlow()
        }

        override suspend fun getById(id: String): SemanticRepresentationEntity? = db[id]

        override suspend fun getForMedia(mediaId: String): List<SemanticRepresentationEntity> =
            db.values.filter { it.mediaId == mediaId }

        override fun observeForMedia(mediaId: String): Flow<List<SemanticRepresentationEntity>> =
            _flow.map { list -> list.filter { it.mediaId == mediaId } }

        override suspend fun getCompatibleRepresentations(type: String, modelId: String, version: Int): List<SemanticRepresentationEntity> =
            db.values.filter { it.representationType == type && it.modelId == modelId && it.modelVersion == version }

        override fun observeCompatibleRepresentations(type: String, modelId: String, version: Int): Flow<List<SemanticRepresentationEntity>> =
            _flow.map { list -> list.filter { it.representationType == type && it.modelId == modelId && it.modelVersion == version } }

        override suspend fun getSpecificRepresentation(mediaId: String, type: String, modelId: String, version: Int): SemanticRepresentationEntity? =
            db.values.firstOrNull { it.mediaId == mediaId && it.representationType == type && it.modelId == modelId && it.modelVersion == version }

        override suspend fun getByModel(modelId: String, version: Int): List<SemanticRepresentationEntity> =
            db.values.filter { it.modelId == modelId && it.modelVersion == version }

        override suspend fun getByType(type: String): List<SemanticRepresentationEntity> =
            db.values.filter { it.representationType == type }

        override suspend fun count(): Int = db.size

        override suspend fun countCompatible(type: String, modelId: String, version: Int): Int =
            db.values.count { it.representationType == type && it.modelId == modelId && it.modelVersion == version }

        override suspend fun exists(mediaId: String, type: String, modelId: String, version: Int): Boolean =
            db.values.any { it.mediaId == mediaId && it.representationType == type && it.modelId == modelId && it.modelVersion == version }

        override suspend fun deleteById(id: String) {
            db.remove(id)
            updateFlow()
        }

        override suspend fun deleteForMedia(mediaId: String) {
            val toRemove = db.filterValues { it.mediaId == mediaId }.keys
            for (k in toRemove) db.remove(k)
            updateFlow()
        }

        override suspend fun deleteByModel(modelId: String, version: Int) {
            val toRemove = db.filterValues { it.modelId == modelId && it.modelVersion == version }.keys
            for (k in toRemove) db.remove(k)
            updateFlow()
        }

        override suspend fun clearAll() {
            db.clear()
            updateFlow()
        }
    }
}

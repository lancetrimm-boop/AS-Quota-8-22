package com.example.data.semantic

import com.example.data.db.SemanticRepresentationDao
import com.example.data.db.SemanticRepresentationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class MultiVectorPersistenceAndRetrievalTest {

    private lateinit var fakeDao: FakeSemanticRepresentationDao
    private lateinit var repository: SemanticRepresentationRepository
    private lateinit var retriever: DefaultSemanticCandidateRetriever

    private val textDescriptor384 = EmbeddingModelDescriptor(
        modelId = "all-minilm-l6-v2",
        modelVersion = 1,
        dimensionality = 384,
        primaryType = SemanticRepresentationType.CONTENT
    )

    private val visualDescriptor512 = EmbeddingModelDescriptor(
        modelId = "mobileclip-s0",
        modelVersion = 1,
        dimensionality = 512,
        primaryType = SemanticRepresentationType.VISUAL
    )

    private val moodDescriptor24 = EmbeddingModelDescriptor(
        modelId = "aura-deterministic-trait-24d",
        modelVersion = 1,
        dimensionality = 24,
        primaryType = SemanticRepresentationType.MOOD
    )

    @Before
    fun setup() {
        fakeDao = FakeSemanticRepresentationDao()
        repository = RoomSemanticRepresentationRepository(fakeDao)
        retriever = DefaultSemanticCandidateRetriever(repository)
    }

    // =========================================================================
    // 1. REPOSITORY & MULTI-VECTOR PERSISTENCE TESTS
    // =========================================================================

    @Test
    fun testMultiVectorPersistence_MultipleTypesAndVersionsCoexistence() = runBlocking {
        val mediaId = "synthetic_media_42"

        // 1. CONTENT representation (384-D, MiniLM, v1)
        val contentV1 = SemanticRepresentation(
            id = "sem_content_v1",
            mediaId = mediaId,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = textDescriptor384,
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 1.0f }),
            sourceDataHash = "hash_content_v1"
        )

        // 2. VISUAL representation (512-D, MobileCLIP, v1)
        val visualV1 = SemanticRepresentation(
            id = "sem_visual_v1",
            mediaId = mediaId,
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = visualDescriptor512,
            dimensionality = 512,
            vector = VectorMath.l2Normalize(FloatArray(512) { 2.0f }),
            sourceDataHash = "hash_visual_v1"
        )

        // 3. MOOD representation (24-D, Aura aesthetic, v1)
        val moodV1 = SemanticRepresentation(
            id = "sem_mood_v1",
            mediaId = mediaId,
            type = SemanticRepresentationType.MOOD,
            modelDescriptor = moodDescriptor24,
            dimensionality = 24,
            vector = VectorMath.l2Normalize(FloatArray(24) { 0.5f }),
            sourceDataHash = "hash_mood_v1"
        )

        // Save initial 3 distinct representations
        repository.saveRepresentations(listOf(contentV1, visualV1, moodV1))
        assertEquals(3, repository.count())

        var reps = repository.getForMedia(mediaId)
        assertEquals(3, reps.size)

        // 4. Add CONTENT representation using MiniLM v2 (384-D)
        val textDescriptorV2 = textDescriptor384.copy(modelVersion = 2)
        val contentV2 = SemanticRepresentation(
            id = "sem_content_v2",
            mediaId = mediaId,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = textDescriptorV2,
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 3.0f }),
            sourceDataHash = "hash_content_v2"
        )
        repository.saveRepresentation(contentV2)

        // Verify all FOUR representations coexist
        assertEquals(4, repository.count())
        reps = repository.getForMedia(mediaId)
        assertEquals(4, reps.size)
        assertTrue(reps.any { it.type == SemanticRepresentationType.CONTENT && it.modelDescriptor.modelVersion == 1 })
        assertTrue(reps.any { it.type == SemanticRepresentationType.CONTENT && it.modelDescriptor.modelVersion == 2 })
        assertTrue(reps.any { it.type == SemanticRepresentationType.VISUAL && it.dimensionality == 512 })
        assertTrue(reps.any { it.type == SemanticRepresentationType.MOOD && it.dimensionality == 24 })

        // 5. Attempt to insert the exact same mediaId + type + modelId + modelVersion (upsert behavior)
        val contentV1Updated = contentV1.copy(
            id = "sem_content_v1",
            sourceDataHash = "hash_content_v1_updated",
            confidence = 0.88f
        )
        repository.saveRepresentation(contentV1Updated)

        // Count must remain 4, and updated fields reflected
        assertEquals(4, repository.count())
        val updatedRep = repository.getSpecificRepresentation(mediaId, SemanticRepresentationType.CONTENT, textDescriptor384)
        assertNotNull(updatedRep)
        assertEquals("hash_content_v1_updated", updatedRep?.sourceDataHash)
        assertEquals(0.88f, updatedRep?.confidence ?: 0.0f, 1e-4f)
    }

    @Test
    fun testSerialization_ExactByteSizes() {
        // 24 dimensions = 96 bytes
        val vec24 = FloatArray(24) { 1.0f }
        val bytes24 = VectorMath.serialize(vec24)
        assertEquals(96, bytes24.size)
        assertArrayEquals(vec24, VectorMath.deserialize(bytes24, 24), 0.0f)

        // 128 dimensions = 512 bytes
        val vec128 = FloatArray(128) { 1.0f }
        val bytes128 = VectorMath.serialize(vec128)
        assertEquals(512, bytes128.size)
        assertArrayEquals(vec128, VectorMath.deserialize(bytes128, 128), 0.0f)

        // 384 dimensions = 1536 bytes
        val vec384 = FloatArray(384) { 1.0f }
        val bytes384 = VectorMath.serialize(vec384)
        assertEquals(1536, bytes384.size)
        assertArrayEquals(vec384, VectorMath.deserialize(bytes384, 384), 0.0f)

        // 512 dimensions = 2048 bytes
        val vec512 = FloatArray(512) { 1.0f }
        val bytes512 = VectorMath.serialize(vec512)
        assertEquals(2048, bytes512.size)
        assertArrayEquals(vec512, VectorMath.deserialize(bytes512, 512), 0.0f)
    }

    @Test
    fun testCompatibilityIsolation_AllIncompatiblePairsRejected() {
        val minilmV1_384 = EmbeddingModelDescriptor("all-minilm-l6-v2", 1, 384, SemanticRepresentationType.CONTENT)
        val minilmV1_512 = EmbeddingModelDescriptor("all-minilm-l6-v2", 1, 512, SemanticRepresentationType.CONTENT)
        val minilmV2_384 = EmbeddingModelDescriptor("all-minilm-l6-v2", 2, 384, SemanticRepresentationType.CONTENT)
        val mobileclipV1_512 = EmbeddingModelDescriptor("mobileclip-s0", 1, 512, SemanticRepresentationType.VISUAL)
        val moodV1_24 = EmbeddingModelDescriptor("aura-aesthetic", 1, 24, SemanticRepresentationType.MOOD)

        // 1. CONTENT / MiniLM / v1 / 384-D against CONTENT / MiniLM / v1 / 512-D
        assertFalse(minilmV1_384.isCompatibleWith(minilmV1_512))

        // 2. CONTENT / MiniLM / v1 against CONTENT / MiniLM / v2
        assertFalse(minilmV1_384.isCompatibleWith(minilmV2_384))

        // 3. CONTENT / MiniLM / v1 against VISUAL / MobileCLIP / v1
        assertFalse(minilmV1_384.isCompatibleWith(mobileclipV1_512))

        // 4. CONTENT / MiniLM against MOOD / AuraAesthetic / v1
        assertFalse(minilmV1_384.isCompatibleWith(moodV1_24))
    }

    @Test
    fun testRetrievalIsolation_QueryReturnsOnlyCompatibleSpace() = runBlocking {
        val mediaId1 = "media_1"
        val mediaId2 = "media_2"

        // Populate multiple representation spaces
        val textV1 = SemanticRepresentation(
            id = "rep_t1",
            mediaId = mediaId1,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = textDescriptor384,
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 1.0f }),
            sourceDataHash = "h1"
        )
        val textV2 = SemanticRepresentation(
            id = "rep_t2",
            mediaId = mediaId2,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = textDescriptor384.copy(modelVersion = 2),
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 1.0f }),
            sourceDataHash = "h2"
        )
        val visualV1 = SemanticRepresentation(
            id = "rep_v1",
            mediaId = mediaId1,
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = visualDescriptor512,
            dimensionality = 512,
            vector = VectorMath.l2Normalize(FloatArray(512) { 1.0f }),
            sourceDataHash = "h3"
        )
        val moodV1 = SemanticRepresentation(
            id = "rep_m1",
            mediaId = mediaId1,
            type = SemanticRepresentationType.MOOD,
            modelDescriptor = moodDescriptor24,
            dimensionality = 24,
            vector = VectorMath.l2Normalize(FloatArray(24) { 1.0f }),
            sourceDataHash = "h4"
        )

        repository.saveRepresentations(listOf(textV1, textV2, visualV1, moodV1))

        // Query retriever for textDescriptor384 (CONTENT / MiniLM / v1 / 384-D)
        val queryVec = VectorMath.l2Normalize(FloatArray(384) { 1.0f })
        val candidates = retriever.retrieveCandidates(
            queryVector = queryVec,
            type = SemanticRepresentationType.CONTENT,
            descriptor = textDescriptor384,
            topK = 10
        )

        // Must return ONLY the single representation matching CONTENT / MiniLM / v1 / 384-D
        assertEquals(1, candidates.size)
        assertEquals("media_1", candidates[0].mediaId)
        assertEquals("rep_t1", candidates[0].representationId)
        assertEquals(SemanticRepresentationType.CONTENT, candidates[0].type)
        assertEquals(1, candidates[0].modelDescriptor.modelVersion)
        assertEquals(384, candidates[0].modelDescriptor.dimensionality)
        assertEquals("all-minilm-l6-v2", candidates[0].modelDescriptor.modelId)
    }

    @Test
    fun testTopK_EdgeCases() {
        val descriptor = EmbeddingModelDescriptor("test-model", 1, 2, SemanticRepresentationType.CONTENT)
        val index = InMemoryVectorIndex(descriptor)

        // 1. Query empty index -> empty list
        val emptyResults = index.query(floatArrayOf(1.0f, 0.0f), topK = 5)
        assertTrue(emptyResults.isEmpty())

        // 2. Query with topK = 0 -> empty list
        val rep1 = SemanticRepresentation(
            id = "r1",
            mediaId = "m1",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 2,
            vector = floatArrayOf(1.0f, 0.0f),
            sourceDataHash = "h1"
        )
        index.add(rep1)
        val zeroResults = index.query(floatArrayOf(1.0f, 0.0f), topK = 0)
        assertTrue(zeroResults.isEmpty())

        // 3. Query with topK > available items -> returns all available
        val kLargeResults = index.query(floatArrayOf(1.0f, 0.0f), topK = 50)
        assertEquals(1, kLargeResults.size)

        // 4. Equal similarity scores -> deterministic tie-break by ascending mediaId
        val repB = SemanticRepresentation(
            id = "r_b",
            mediaId = "media_b",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 2,
            vector = floatArrayOf(0.0f, 1.0f),
            sourceDataHash = "hb"
        )
        val repA = SemanticRepresentation(
            id = "r_a",
            mediaId = "media_a",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 2,
            vector = floatArrayOf(0.0f, 1.0f),
            sourceDataHash = "ha"
        )
        index.clear()
        index.addAll(listOf(repB, repA))

        val tieBreakResults = index.query(floatArrayOf(0.0f, 1.0f), topK = 10)
        assertEquals(2, tieBreakResults.size)
        assertEquals("media_a", tieBreakResults[0].mediaId) // tie break: 'media_a' < 'media_b'
        assertEquals("media_b", tieBreakResults[1].mediaId)
    }

    @Test
    fun testRepository_ModelAndVersionIsolation() = runBlocking {
        val repV1 = SemanticRepresentation(
            id = "sem_v1",
            mediaId = "media_1",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = textDescriptor384,
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 1.0f }),
            sourceDataHash = "hash_1"
        )

        val descriptorV2 = textDescriptor384.copy(modelVersion = 2)
        val repV2 = SemanticRepresentation(
            id = "sem_v2",
            mediaId = "media_1",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptorV2,
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 2.0f }),
            sourceDataHash = "hash_2"
        )

        repository.saveRepresentations(listOf(repV1, repV2))

        val v1Items = repository.getCompatibleRepresentations(SemanticRepresentationType.CONTENT, textDescriptor384)
        assertEquals(1, v1Items.size)
        assertEquals("sem_v1", v1Items[0].id)

        val v2Items = repository.getCompatibleRepresentations(SemanticRepresentationType.CONTENT, descriptorV2)
        assertEquals(1, v2Items.size)
        assertEquals("sem_v2", v2Items[0].id)
    }

    @Test
    fun testRepository_CRUDOperations() = runBlocking {
        val rep = SemanticRepresentation(
            id = "sem_crud_1",
            mediaId = "media_crud",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = textDescriptor384,
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 1.0f }),
            sourceDataHash = "hash_crud"
        )

        repository.saveRepresentation(rep)
        assertTrue(repository.exists("media_crud", SemanticRepresentationType.CONTENT, textDescriptor384))
        assertEquals(1, repository.count())

        val retrieved = repository.getById("sem_crud_1")
        assertNotNull(retrieved)
        assertEquals("media_crud", retrieved?.mediaId)

        repository.deleteForMedia("media_crud")
        assertFalse(repository.exists("media_crud", SemanticRepresentationType.CONTENT, textDescriptor384))
        assertEquals(0, repository.count())
    }

    // =========================================================================
    // 2. VECTOR INDEX & TOP-K RETRIEVAL TESTS
    // =========================================================================

    @Test
    fun testVectorIndex_ExactCosineOrderingAndDeterministicTieBreak() {
        val descriptor = EmbeddingModelDescriptor("test-model", 1, 3, SemanticRepresentationType.CONTENT)
        val index = InMemoryVectorIndex(descriptor)

        // Item 1: Exactly collinear with query [1, 0, 0] -> score 1.0
        val rep1 = SemanticRepresentation(
            id = "rep_1",
            mediaId = "media_collinear",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(1.0f, 0.0f, 0.0f),
            sourceDataHash = "h1"
        )

        // Item 2: 45 degrees [1, 1, 0] -> score ~0.707
        val rep2 = SemanticRepresentation(
            id = "rep_2",
            mediaId = "media_45deg",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(1.0f, 1.0f, 0.0f),
            sourceDataHash = "h2"
        )

        // Item 3: Orthogonal [0, 1, 0] -> score 0.0
        val rep3 = SemanticRepresentation(
            id = "rep_3",
            mediaId = "media_orthogonal",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(0.0f, 1.0f, 0.0f),
            sourceDataHash = "h3"
        )

        index.addAll(listOf(rep2, rep3, rep1))

        val queryVec = floatArrayOf(1.0f, 0.0f, 0.0f)
        val results = index.query(queryVec, topK = 10)

        assertEquals(3, results.size)
        assertEquals("media_collinear", results[0].mediaId)
        assertEquals(1.0f, results[0].similarityScore, 1e-4f)

        assertEquals("media_45deg", results[1].mediaId)
        assertEquals(0.7071f, results[1].similarityScore, 1e-3f)

        assertEquals("media_orthogonal", results[2].mediaId)
        assertEquals(0.0f, results[2].similarityScore, 1e-4f)
    }

    @Test
    fun testVectorIndex_PreventsDuplicateMediaIds() {
        val descriptor = EmbeddingModelDescriptor("test-model", 1, 3, SemanticRepresentationType.CONTENT)
        val index = InMemoryVectorIndex(descriptor)

        // Same mediaId with two frames/representations
        val repA = SemanticRepresentation(
            id = "rep_a",
            mediaId = "media_shared",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(0.5f, 0.5f, 0.0f), // lower similarity to [1,0,0]
            sourceDataHash = "ha"
        )

        val repB = SemanticRepresentation(
            id = "rep_b",
            mediaId = "media_shared",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(1.0f, 0.0f, 0.0f), // higher similarity to [1,0,0]
            sourceDataHash = "hb"
        )

        index.addAll(listOf(repA, repB))

        val results = index.query(floatArrayOf(1.0f, 0.0f, 0.0f), topK = 10)
        assertEquals(1, results.size)
        assertEquals("media_shared", results[0].mediaId)
        assertEquals("rep_b", results[0].representationId)
        assertEquals(1.0f, results[0].similarityScore, 1e-4f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testVectorIndex_IncompatibleDescriptor_ThrowsException() {
        val descA = EmbeddingModelDescriptor("model-a", 1, 384, SemanticRepresentationType.CONTENT)
        val descB = EmbeddingModelDescriptor("model-b", 1, 384, SemanticRepresentationType.CONTENT)
        val index = InMemoryVectorIndex(descA)

        val incompatibleRep = SemanticRepresentation(
            id = "rep_bad",
            mediaId = "media_bad",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descB,
            dimensionality = 384,
            vector = VectorMath.l2Normalize(FloatArray(384) { 1.0f }),
            sourceDataHash = "h"
        )

        index.add(incompatibleRep)
    }

    // =========================================================================
    // 3. SCALE BENCHMARK (10,000 VECTORS)
    // =========================================================================

    @Test
    fun testVectorIndex_10000VectorScaleBenchmark() {
        val dim = 128
        val count = 10000
        val descriptor = EmbeddingModelDescriptor("bench-model-128d", 1, dim, SemanticRepresentationType.CONTENT)
        val index = InMemoryVectorIndex(descriptor)

        val representations = ArrayList<SemanticRepresentation>(count)
        val rand = java.util.Random(42)

        for (i in 0 until count) {
            val vec = FloatArray(dim) { rand.nextFloat() * 2.0f - 1.0f }
            val normVec = VectorMath.l2Normalize(vec)
            representations.add(
                SemanticRepresentation(
                    id = "rep_$i",
                    mediaId = "media_$i",
                    type = SemanticRepresentationType.CONTENT,
                    modelDescriptor = descriptor,
                    dimensionality = dim,
                    vector = normVec,
                    sourceDataHash = "hash_$i"
                )
            )
        }

        val buildStart = System.currentTimeMillis()
        index.rebuild(representations)
        val buildDuration = System.currentTimeMillis() - buildStart

        assertEquals(count, index.size)
        assertTrue("Index build time for 10k vectors should be under 500ms on JVM", buildDuration < 500)

        // Query benchmark
        val queryVec = VectorMath.l2Normalize(FloatArray(dim) { rand.nextFloat() * 2.0f - 1.0f })
        val queryStart = System.currentTimeMillis()
        val results = index.query(queryVec, topK = 20)
        val queryDuration = System.currentTimeMillis() - queryStart

        assertEquals(20, results.size)
        assertTrue("Query time for 10k vectors should be under 50ms on JVM", queryDuration < 50)
        
        // Verify results are sorted descending
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].similarityScore >= results[i + 1].similarityScore)
        }
    }

    // =========================================================================
    // 4. TEST MOCK EMBEDDING PROVIDER INTEGRATION
    // =========================================================================

    @Test
    fun testTestMockEmbeddingProvider_GeneratesValidRepresentation() = runBlocking {
        val provider = TestMockEmbeddingProvider()
        assertTrue(provider.isReady())

        val result = provider.generateEmbedding(
            mediaId = "media_mock_1",
            input = SemanticInput.Text("cyberpunk ambient sound"),
            sourceDataHash = "hash_mock"
        )

        assertTrue(result is EmbeddingResult.Success)
        val rep = (result as EmbeddingResult.Success).representation

        assertEquals("media_mock_1", rep.mediaId)
        assertEquals(128, rep.dimensionality)
        assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)

        // Persist through repository
        repository.saveRepresentation(rep)
        val retrieved = repository.getById(rep.id)
        assertNotNull(retrieved)
        assertEquals(rep, retrieved)
    }

    // =========================================================================
    // FAKE DAO IMPLEMENTATION FOR REPOSITORY TESTS
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

        override suspend fun getById(id: String): SemanticRepresentationEntity? {
            return db[id]
        }

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

        override suspend fun count(): Int {
            return db.size
        }

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

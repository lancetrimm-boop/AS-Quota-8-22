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

class MobileCLIPPersistenceTest {

    private lateinit var repository: SemanticRepresentationRepository
    private lateinit var fakeDao: FakeSemanticRepresentationDao
    private lateinit var mobileCLIPProvider: MobileCLIPEmbeddingProvider
    private lateinit var inferenceEngine: LocalMobileCLIPInferenceEngine

    @Before
    fun setUp() {
        fakeDao = FakeSemanticRepresentationDao()
        repository = RoomSemanticRepresentationRepository(fakeDao)
        inferenceEngine = LocalMobileCLIPInferenceEngine()
        mobileCLIPProvider = MobileCLIPEmbeddingProvider(inferenceEngine)
    }

    @Test
    fun testMobileCLIPEmbeddingPersistence_SaveAndRetrieve() = runBlocking {
        val mediaId = "video_123"
        val descriptor = mobileCLIPProvider.descriptor
        val vector = FloatArray(512) { i -> i.toFloat() / 512f }
        VectorMath.l2Normalize(vector) // Ensure it is normalized as per requirement
        
        val representation = SemanticRepresentation(
            id = "sem_${mediaId}_visual_${descriptor.modelId}_v${descriptor.modelVersion}",
            mediaId = mediaId,
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = descriptor,
            dimensionality = 512,
            vector = vector,
            sourceDataHash = "content_hash_v1",
            confidence = 1.0f
        )

        // 1. Save
        repository.saveRepresentation(representation)
        assertEquals(1, repository.count())

        // 2. Retrieve
        val retrieved = repository.getSpecificRepresentation(mediaId, SemanticRepresentationType.VISUAL, descriptor)
        assertNotNull(retrieved)
        assertEquals(mediaId, retrieved?.mediaId)
        assertEquals(SemanticRepresentationType.VISUAL, retrieved?.type)
        assertEquals(512, retrieved?.dimensionality)
        assertEquals(512, retrieved?.vector?.size)
        
        // 3. Numerical Fidelity
        assertArrayEquals("Retrieved vector must match original", representation.vector, retrieved?.vector, 1e-6f)
        assertTrue("Values must be finite", retrieved?.vector?.all { !it.isNaN() && !it.isInfinite() } ?: false)
    }

    @Test
    fun testMobileCLIPUpdate_ReplacesExistingRecord() = runBlocking {
        val mediaId = "video_123"
        val descriptor = mobileCLIPProvider.descriptor
        
        val rep1 = SemanticRepresentation(
            id = "sem_${mediaId}_visual_${descriptor.modelId}_v${descriptor.modelVersion}",
            mediaId = mediaId,
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = descriptor,
            dimensionality = 512,
            vector = FloatArray(512) { 0.1f },
            sourceDataHash = "hash1",
            confidence = 1.0f
        )
        
        repository.saveRepresentation(rep1)
        assertEquals(1, repository.count())

        val rep2 = rep1.copy(sourceDataHash = "hash2", vector = FloatArray(512) { 0.2f })
        repository.saveRepresentation(rep2)
        
        // UNIQUE index would replace it in real DB (via Upsert)
        // Our FakeDao uses id as key for upsert, so it should be replaced.
        assertEquals(1, repository.count())
        val retrieved = repository.getById(rep1.id)
        assertEquals("hash2", retrieved?.sourceDataHash)
    }

    @Test
    fun testMiniLMIsolation_DoesNotConflictWithVisualEmbeddings() = runBlocking {
        val mediaId = "item_1"
        
        // Save MiniLM (CONTENT, 384d)
        val minilmDesc = EmbeddingModelDescriptor("all-minilm-l6-v2", 2, 384, SemanticRepresentationType.CONTENT)
        val minilmRep = SemanticRepresentation(
            id = "sem_${mediaId}_content_${minilmDesc.modelId}_v${minilmDesc.modelVersion}",
            mediaId = mediaId,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = minilmDesc,
            dimensionality = 384,
            vector = FloatArray(384) { 0.5f },
            sourceDataHash = "text_hash",
            confidence = 1.0f
        )
        repository.saveRepresentation(minilmRep)

        // Save MobileCLIP (VISUAL, 512d)
        val visualDesc = mobileCLIPProvider.descriptor
        val visualRep = SemanticRepresentation(
            id = "sem_${mediaId}_visual_${visualDesc.modelId}_v${visualDesc.modelVersion}",
            mediaId = mediaId,
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = visualDesc,
            dimensionality = 512,
            vector = FloatArray(512) { 0.8f },
            sourceDataHash = "visual_hash",
            confidence = 1.0f
        )
        repository.saveRepresentation(visualRep)

        assertEquals(2, repository.count())

        // Verify retrieval isolation
        val retrievedMinilm = repository.getSpecificRepresentation(mediaId, SemanticRepresentationType.CONTENT, minilmDesc)
        val retrievedVisual = repository.getSpecificRepresentation(mediaId, SemanticRepresentationType.VISUAL, visualDesc)

        assertEquals(384, retrievedMinilm?.dimensionality)
        assertEquals(512, retrievedVisual?.dimensionality)
    }

}

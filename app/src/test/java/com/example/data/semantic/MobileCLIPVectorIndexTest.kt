package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MobileCLIPVectorIndexTest {

    private lateinit var repository: SemanticRepresentationRepository
    private lateinit var retriever: DefaultSemanticCandidateRetriever
    private lateinit var mobileCLIPProvider: MobileCLIPEmbeddingProvider
    private lateinit var inferenceEngine: LocalMobileCLIPInferenceEngine

    @Before
    fun setUp() {
        val fakeDao = FakeSemanticRepresentationDao()
        repository = RoomSemanticRepresentationRepository(fakeDao)
        retriever = DefaultSemanticCandidateRetriever(repository)
        inferenceEngine = LocalMobileCLIPInferenceEngine()
        mobileCLIPProvider = MobileCLIPEmbeddingProvider(inferenceEngine)
    }

    @Test
    fun testMobileCLIPHydration_LoadsPersistedVectorsIntoIndex() = runBlocking {
        val mediaId = "video_index_test"
        val descriptor = mobileCLIPProvider.descriptor
        val vector = FloatArray(512) { 0.5f }
        
        val representation = SemanticRepresentation(
            id = "sem_${mediaId}_visual_${descriptor.modelId}_v${descriptor.modelVersion}",
            mediaId = mediaId,
            type = SemanticRepresentationType.VISUAL,
            modelDescriptor = descriptor,
            dimensionality = 512,
            vector = vector,
            sourceDataHash = "hash1",
            confidence = 1.0f
        )
        
        repository.saveRepresentation(representation)

        // retrieveCandidates triggers lazy initialization from repository
        val candidates = retriever.retrieveCandidates(
            queryVector = FloatArray(512) { 0.5f },
            type = SemanticRepresentationType.VISUAL,
            descriptor = descriptor,
            topK = 1
        )

        assertEquals(1, candidates.size)
        assertEquals(mediaId, candidates[0].mediaId)
        assertEquals(1, retriever.getIndexSize(SemanticRepresentationType.VISUAL, descriptor))
    }

    @Test
    fun testDimensionSafety_PreventsCrossModalityComparison() = runBlocking {
        val minilmDesc = EmbeddingModelDescriptor("all-minilm-l6-v2", 2, 384, SemanticRepresentationType.CONTENT)
        val clipDesc = mobileCLIPProvider.descriptor

        // 1. CONTENT Index (384d)
        val minilmRep = SemanticRepresentation(
            id = "sem_1_content", mediaId = "m1", type = SemanticRepresentationType.CONTENT,
            modelDescriptor = minilmDesc, dimensionality = 384, vector = FloatArray(384) { 0.1f },
            sourceDataHash = "h1"
        )
        repository.saveRepresentation(minilmRep)

        // 2. VISUAL Index (512d)
        val clipRep = SemanticRepresentation(
            id = "sem_1_visual", mediaId = "m1", type = SemanticRepresentationType.VISUAL,
            modelDescriptor = clipDesc, dimensionality = 512, vector = FloatArray(512) { 0.2f },
            sourceDataHash = "h2"
        )
        repository.saveRepresentation(clipRep)

        // Querying CONTENT with 512d must fail due to index-level dimensionality check
        try {
            retriever.retrieveCandidates(FloatArray(512) { 0.5f }, SemanticRepresentationType.CONTENT, minilmDesc)
            fail("Should have thrown dimension mismatch exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("dimension mismatch") == true)
        }

        // Querying VISUAL with 512d works
        val candidates = retriever.retrieveCandidates(FloatArray(512) { 0.5f }, SemanticRepresentationType.VISUAL, clipDesc)
        assertEquals(1, candidates.size)
        assertEquals(SemanticRepresentationType.VISUAL, candidates[0].type)
    }

    @Test
    fun testUpdateBehavior_ReplacesIndexEntry() = runBlocking {
        val descriptor = mobileCLIPProvider.descriptor
        val mediaId = "m1"
        
        // Initialize index
        retriever.initializeIndex(SemanticRepresentationType.VISUAL, descriptor)
        
        val rep1 = SemanticRepresentation(
            id = "sem_m1_v1", mediaId = mediaId, type = SemanticRepresentationType.VISUAL,
            modelDescriptor = descriptor, dimensionality = 512, vector = FloatArray(512) { 0.1f },
            sourceDataHash = "h1"
        )
        repository.saveRepresentation(rep1)
        retriever.onRepresentationAdded(rep1)
        
        assertEquals(1, retriever.getIndexSize(SemanticRepresentationType.VISUAL, descriptor))

        // Update
        val rep2 = rep1.copy(vector = FloatArray(512) { 0.9f })
        repository.saveRepresentation(rep2)
        retriever.onRepresentationAdded(rep2)

        assertEquals(1, retriever.getIndexSize(SemanticRepresentationType.VISUAL, descriptor))
        
        val query = FloatArray(512) { 0.9f }
        val candidates = retriever.retrieveCandidates(query, SemanticRepresentationType.VISUAL, descriptor)
        assertEquals(1.0f, candidates[0].similarityScore, 1e-4f)
    }

    @Test
    fun testDeletion_RemovesFromIndex() = runBlocking {
        val descriptor = mobileCLIPProvider.descriptor
        
        // Initialize index
        retriever.initializeIndex(SemanticRepresentationType.VISUAL, descriptor)

        val rep = SemanticRepresentation(
            id = "sem_m1_v1", mediaId = "m1", type = SemanticRepresentationType.VISUAL,
            modelDescriptor = descriptor, dimensionality = 512, vector = FloatArray(512) { 0.1f },
            sourceDataHash = "h1"
        )
        repository.saveRepresentation(rep)
        retriever.onRepresentationAdded(rep)
        assertEquals(1, retriever.getIndexSize(SemanticRepresentationType.VISUAL, descriptor))

        retriever.onMediaRemoved("m1")
        assertEquals(0, retriever.getIndexSize(SemanticRepresentationType.VISUAL, descriptor))
    }
}

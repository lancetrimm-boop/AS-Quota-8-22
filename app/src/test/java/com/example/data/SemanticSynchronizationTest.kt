package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import com.example.data.semantic.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SemanticSynchronizationTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    private val descriptor = EmbeddingModelDescriptor(
        modelId = "test-model",
        modelVersion = 1,
        dimensionality = 384,
        primaryType = SemanticRepresentationType.CONTENT
    )

    private lateinit var fakeEmbeddingProvider: FakeEmbeddingProvider
    private lateinit var semanticRepo: SemanticRepresentationRepository
    private lateinit var retriever: SemanticCandidateRetriever

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository(dispatcher = testDispatcher)
        
        // Inject database
        val dbField = MediaRepository::class.java.getDeclaredField("database")
        dbField.isAccessible = true
        dbField.set(repository, database)
        
        // Set database state to READY
        val stateField = MediaRepository::class.java.getDeclaredField("_databaseState")
        stateField.isAccessible = true
        (stateField.get(repository) as MutableStateFlow<DatabaseState>).value = DatabaseState.READY

        semanticRepo = RoomSemanticRepresentationRepository(database.semanticRepresentationDao())
        fakeEmbeddingProvider = FakeEmbeddingProvider(descriptor)
        retriever = DefaultSemanticCandidateRetriever(semanticRepo)

        // Inject semantic components
        val fields = mapOf(
            "semanticRepresentationRepository" to semanticRepo,
            "embeddingProvider" to fakeEmbeddingProvider,
            "semanticCandidateRetriever" to retriever
        )
        
        fields.forEach { (name, value) ->
            val field = MediaRepository::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(repository, value)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test addition synchronization`() = runTest(testDispatcher) {
        // 1. Initial hydration (empty)
        retriever.initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        assertEquals(0, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))

        // 2. Ingest new media
        val mediaId = "new_item"
        val entity = MediaEntity(id = mediaId, title = "New Item", mediaType = "PHOTO", compatibilityStatus = "ANALYSIS_PENDING")
        database.mediaDao().insert(entity)

        repository.processPendingMedia(context, 1L, emptySet())

        // 3. Verify it's in the index without rebuild
        assertEquals("Index should have 1 item after ingestion", 1, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }

    @Test
    fun `test update synchronization`() = runTest(testDispatcher) {
        // 1. Setup existing item
        val mediaId = "update_item"
        val entity = MediaEntity(id = mediaId, title = "Original Title", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE", contentHash = "h1")
        database.mediaDao().insert(entity)
        
        // Create initial representation
        val rep1 = SemanticRepresentation(
            id = "sem_update_item_content_test-model_v1", // Stable ID
            mediaId = mediaId,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 384,
            vector = FloatArray(384) { 0.1f },
            sourceDataHash = "h1"
        )
        semanticRepo.saveRepresentation(rep1)
        retriever.initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        assertEquals(1, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))

        // 2. Update media title/hash
        val updatedEntity = entity.copy(title = "New Title", contentHash = "h2", compatibilityStatus = "ANALYSIS_PENDING")
        database.mediaDao().insert(updatedEntity) // REPLACE

        // Mock different vector for new title
        fakeEmbeddingProvider.vectorValue = 0.9f

        repository.processPendingMedia(context, 1L, emptySet())

        // 3. Verify index updated
        assertEquals("Index size should still be 1", 1, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
        
        // Query to check if new vector is used
        val queryVector = FloatArray(384) { 0.9f }
        val results = retriever.retrieveCandidates(queryVector, SemanticRepresentationType.CONTENT, descriptor)
        assertEquals(1, results.size)
        assertTrue("Similarity should be high for new vector", results[0].similarityScore > 0.99f)
    }

    @Test
    fun `test deletion synchronization`() = runTest(testDispatcher) {
        // 1. Setup item
        val mediaId = "delete_item"
        val entity = MediaEntity(id = mediaId, title = "To Delete", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE")
        database.mediaDao().insert(entity)
        
        val rep = SemanticRepresentation(
            id = "sem_delete_item_content_test-model_v1",
            mediaId = mediaId,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 384,
            vector = FloatArray(384) { 0.1f },
            sourceDataHash = "h"
        )
        semanticRepo.saveRepresentation(rep)
        retriever.initializeIndex(SemanticRepresentationType.CONTENT, descriptor)
        assertEquals(1, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))

        // 2. Delete item
        repository.deleteMediaItem(mediaId)

        // 3. Verify removal
        assertEquals("Index should be empty after deletion", 0, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
        
        val dbCount = semanticRepo.count()
        assertEquals("DB should be empty after deletion", 0, dbCount)
    }

    class FakeEmbeddingProvider(
        override val descriptor: EmbeddingModelDescriptor
    ) : EmbeddingProvider {
        override val supportedTypes = setOf(SemanticRepresentationType.CONTENT)
        var vectorValue = 0.1f
        
        override fun isReady(): Boolean = true
        override fun close() {}
        override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
            val rep = SemanticRepresentation(
                id = "sem_${mediaId}_content_${descriptor.modelId}_v${descriptor.modelVersion}",
                mediaId = mediaId,
                type = SemanticRepresentationType.CONTENT,
                modelDescriptor = descriptor,
                dimensionality = 384,
                vector = FloatArray(384) { vectorValue },
                sourceDataHash = sourceDataHash
            )
            return EmbeddingResult.Success(rep)
        }
    }
}

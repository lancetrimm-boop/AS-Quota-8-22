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
class SemanticIngestionIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeEmbeddingProvider: FakeEmbeddingProvider
    private lateinit var semanticRepo: SemanticRepresentationRepository

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
        
        // Inject semantic components
        val repoField = MediaRepository::class.java.getDeclaredField("semanticRepresentationRepository")
        repoField.isAccessible = true
        repoField.set(repository, semanticRepo)

        val descriptor = EmbeddingModelDescriptor(
            modelId = "test-model",
            modelVersion = 1,
            dimensionality = 384,
            primaryType = SemanticRepresentationType.CONTENT
        )
        fakeEmbeddingProvider = FakeEmbeddingProvider(descriptor)
        
        val providerField = MediaRepository::class.java.getDeclaredField("embeddingProvider")
        providerField.isAccessible = true
        providerField.set(repository, fakeEmbeddingProvider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test processPendingMedia triggers embedding generation and persistence`() = runTest(testDispatcher) {
        val mediaId = "test_media_1"
        val entity = MediaEntity(
            id = mediaId,
            title = "Golden Retriever Puppy",
            mediaType = "PHOTO",
            uriPath = "file:///test1.jpg",
            compatibilityStatus = "ANALYSIS_PENDING" // This will make it pending
        )
        database.mediaDao().insert(entity)

        // Run ingestion
        repository.processPendingMedia(context, 123L, emptySet())

        // Verify provider was called
        assertEquals(1, fakeEmbeddingProvider.callCount)
        assertEquals(mediaId, fakeEmbeddingProvider.lastMediaId)
        assertTrue(fakeEmbeddingProvider.lastInput is SemanticInput.Text)
        assertEquals("Golden Retriever Puppy", (fakeEmbeddingProvider.lastInput as SemanticInput.Text).text)

        // Verify persistence
        val persisted = semanticRepo.getForMedia(mediaId)
        assertEquals(1, persisted.size)
        assertEquals(384, persisted[0].dimensionality)
        assertEquals("test-model", persisted[0].modelDescriptor.modelId)
    }

    @Test
    fun `test semantic processing is idempotent`() = runTest(testDispatcher) {
        val mediaId = "test_media_idempotent"
        val entity = MediaEntity(
            id = mediaId,
            title = "Sunset at Beach",
            mediaType = "PHOTO",
            uriPath = "file:///beach.jpg",
            compatibilityStatus = "ANALYSIS_PENDING"
        )
        database.mediaDao().insert(entity)

        // Pre-insert a representation for the SAME model
        val existingRep = SemanticRepresentation(
            id = "existing_sem",
            mediaId = mediaId,
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = fakeEmbeddingProvider.descriptor,
            dimensionality = 384,
            vector = FloatArray(384) { 0.5f },
            sourceDataHash = "old_hash"
        )
        semanticRepo.saveRepresentation(existingRep)

        // Run ingestion
        repository.processPendingMedia(context, 123L, emptySet())

        // Verify provider was NOT called because it already exists
        assertEquals(0, fakeEmbeddingProvider.callCount)
    }

    @Test
    fun `test semantic failure does not abort media ingestion`() = runTest(testDispatcher) {
        val mediaId = "test_media_failure"
        val entity = MediaEntity(
            id = mediaId,
            title = "Failing Semantic Item",
            mediaType = "PHOTO",
            uriPath = "file:///fail.jpg",
            compatibilityStatus = "ANALYSIS_PENDING"
        )
        database.mediaDao().insert(entity)

        // Mock failure
        fakeEmbeddingProvider.shouldFail = true

        // Run ingestion
        repository.processPendingMedia(context, 123L, emptySet())

        // Verify provider was called
        assertEquals(1, fakeEmbeddingProvider.callCount)

        // Verify media item IS ingested (status changed from ANALYSIS_PENDING)
        val ingestedEntity = database.mediaDao().getMediaById(mediaId)
        assertNotNull(ingestedEntity)
        assertNotEquals("ANALYSIS_PENDING", ingestedEntity?.compatibilityStatus)
        
        // Verify NO representation was persisted
        assertTrue(semanticRepo.getForMedia(mediaId).isEmpty())
    }

    @Test
    fun `test multiple items with partial semantic failure`() = runTest(testDispatcher) {
        // We use a small batch to ensure we test the batching logic too
        val items = (1..5).map { i ->
            MediaEntity(
                id = "media_$i",
                title = "Item $i",
                mediaType = "PHOTO",
                uriPath = "file:///$i.jpg",
                compatibilityStatus = "ANALYSIS_PENDING"
            )
        }
        database.mediaDao().insertAll(items)

        // Fail for odd items (1, 3, 5), succeed for even (2, 4)
        // Since we can't easily change shouldFail per call in our simple fake without more logic:
        val providerWithToggle = object : FakeEmbeddingProvider(fakeEmbeddingProvider.descriptor) {
            override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
                val idNum = mediaId.removePrefix("media_").toInt()
                return if (idNum % 2 != 0) {
                    EmbeddingResult.Failure(EmbeddingErrorCode.INFERENCE_ERROR, "Fail")
                } else {
                    super.generateEmbedding(mediaId, input, sourceDataHash)
                }
            }
        }
        
        val providerField = MediaRepository::class.java.getDeclaredField("embeddingProvider")
        providerField.isAccessible = true
        providerField.set(repository, providerWithToggle)

        // Run ingestion
        repository.processPendingMedia(context, 123L, emptySet())

        // Verify all media items were processed
        items.forEach { entity ->
            val ingested = database.mediaDao().getMediaById(entity.id)
            assertNotEquals("ANALYSIS_PENDING", ingested?.compatibilityStatus)
        }

        // Verify only 2 and 4 have representations (2 total)
        assertEquals(2, semanticRepo.count())
    }

    open class FakeEmbeddingProvider(
        override val descriptor: EmbeddingModelDescriptor,
        override val supportedTypes: Set<SemanticRepresentationType> = setOf(SemanticRepresentationType.CONTENT)
    ) : EmbeddingProvider {
        var shouldFail = false
        var lastMediaId: String? = null
        var lastInput: SemanticInput? = null
        var callCount = 0

        override fun isReady(): Boolean = true

        override fun close() {}

        override suspend fun generateEmbedding(
            mediaId: String,
            input: SemanticInput,
            sourceDataHash: String
        ): EmbeddingResult {
            callCount++
            lastMediaId = mediaId
            lastInput = input
            
            if (shouldFail) {
                return EmbeddingResult.Failure(EmbeddingErrorCode.INFERENCE_ERROR, "Simulated Failure")
            }
            
            val rep = SemanticRepresentation(
                id = "sem_$mediaId",
                mediaId = mediaId,
                type = descriptor.primaryType,
                modelDescriptor = descriptor,
                dimensionality = descriptor.dimensionality,
                vector = FloatArray(descriptor.dimensionality) { 0.1f },
                sourceDataHash = sourceDataHash
            )
            return EmbeddingResult.Success(rep)
        }
    }
}

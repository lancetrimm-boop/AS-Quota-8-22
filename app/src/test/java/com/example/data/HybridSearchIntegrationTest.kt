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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HybridSearchIntegrationTest {

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

        // Manually setup semantic stack for integration testing
        val semanticRepo = RoomSemanticRepresentationRepository(database.semanticRepresentationDao())
        val mockProvider = object : EmbeddingProvider {
            override val descriptor = this@HybridSearchIntegrationTest.descriptor
            override val supportedTypes = setOf(SemanticRepresentationType.CONTENT)
            override fun isReady() = true
            override suspend fun generateEmbedding(mediaId: String, input: SemanticInput, sourceDataHash: String): EmbeddingResult {
                // For testing search, we just need a deterministic vector
                return EmbeddingResult.Success(SemanticRepresentation(
                    id = "query_rep", mediaId = mediaId, type = SemanticRepresentationType.CONTENT,
                    modelDescriptor = descriptor, dimensionality = 384,
                    vector = FloatArray(384) { 0.1f }, sourceDataHash = sourceDataHash
                ))
            }
            override fun close() {}
        }

        val retriever = DefaultSemanticCandidateRetriever(semanticRepo)
        val semanticService = DefaultSemanticSearchService(mockProvider, retriever)
        
        // Extract inner class instance for Lexical Search
        val repoClass = MediaRepository::class.java
        val lexicalClassName = "${repoClass.name}\$ProductionLexicalRetriever"
        val lexicalClass = Class.forName(lexicalClassName)
        val lexicalConstructor = lexicalClass.getDeclaredConstructor(repoClass)
        lexicalConstructor.isAccessible = true
        val lexicalRetriever = lexicalConstructor.newInstance(repository) as LexicalCandidateRetriever
        
        val hybridEngine = DefaultHybridSearchEngine(semanticService, lexicalRetriever)

        // Inject all components
        val fields = mapOf(
            "semanticRepresentationRepository" to semanticRepo,
            "embeddingProvider" to mockProvider,
            "semanticCandidateRetriever" to retriever,
            "semanticSearchService" to semanticService,
            "hybridSearchEngine" to hybridEngine
        )
        
        fields.forEach { (name, value) ->
            val field = repoClass.getDeclaredField(name)
            field.isAccessible = true
            field.set(repository, value)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createMediaItem(id: String, title: String, dateAdded: Long = System.currentTimeMillis()): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "PHOTO",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            dateAdded = dateAdded
        )
    }

    @Test
    fun `test hybrid search returns fused results for valid query`() = runTest(testDispatcher) {
        // 1. Setup Data
        val items = listOf(
            createMediaItem("m1", "Synthwave Neon"),
            createMediaItem("m2", "Acoustic Guitar"),
            createMediaItem("m3", "Neon Cityscape")
        )
        repository.setMediaItemsForTesting(items)

        // 2. Perform search
        repository.librarySearchQuery = "Neon"
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Verify Results
        val searchResults = repository.latestAiSortRecommendation.value
        assertFalse("Search should return items", searchResults.isEmpty())
        
        val ids = searchResults.map { it.id }
        assertTrue("m1 should be found", ids.contains("m1"))
        assertTrue("m3 should be found", ids.contains("m3"))
        assertFalse("m2 should NOT be found", ids.contains("m2"))
    }

    @Test
    fun `test hybrid search includes semantic-only matches`() = runTest(testDispatcher) {
        // m1: lexical match ("Neon")
        // m2: semantic-only match
        val items = listOf(
            createMediaItem("m1", "Neon Light"),
            createMediaItem("m2", "Cyberpunk Vibe")
        )
        repository.setMediaItemsForTesting(items)

        // Add semantic representation for m2 that matches our test query vector
        val semanticRepo = repository.semanticRepresentationRepository!!
        semanticRepo.saveRepresentation(SemanticRepresentation(
            id = "sem_m2", mediaId = "m2", type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor, dimensionality = 384,
            vector = FloatArray(384) { 0.1f },
            sourceDataHash = "h2"
        ))

        repository.librarySearchQuery = "Anything"
        testDispatcher.scheduler.advanceUntilIdle()

        val results = repository.latestAiSortRecommendation.value
        val ids = results.map { it.id }
        
        // m2 should be found semantically even though title doesn't match "Anything"
        assertTrue("m2 should be found semantically", ids.contains("m2"))
    }

    @Test
    fun `test lazy index hydration on first search`() = runTest(testDispatcher) {
        // Add item with representation
        val item = createMediaItem("m1", "Hydration Test")
        repository.setMediaItemsForTesting(listOf(item))
        
        repository.semanticRepresentationRepository!!.saveRepresentation(SemanticRepresentation(
            id = "s1", mediaId = "m1", type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor, dimensionality = 384,
            vector = FloatArray(384) { 0.1f }, sourceDataHash = "h1"
        ))

        val retriever = repository.semanticCandidateRetriever!!
        assertEquals("Index should be empty before search", 0, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))

        // Search
        repository.librarySearchQuery = "test"
        testDispatcher.scheduler.advanceUntilIdle()

        // Index should be hydrated
        assertEquals("Index should be hydrated after search", 1, retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor))
    }

    @Test
    fun `test blank query uses legacy sorting`() = runTest(testDispatcher) {
        val items = listOf(
            createMediaItem("m1", "B", dateAdded = 1000L),
            createMediaItem("m2", "A", dateAdded = 2000L)
        )
        repository.setMediaItemsForTesting(items)

        // Default sort is NEWEST_FIRST
        repository.librarySearchQuery = ""
        testDispatcher.scheduler.advanceUntilIdle()

        val results = repository.latestAiSortRecommendation.value
        assertEquals("m2", results[0].id) // A (newer)
        assertEquals("m1", results[1].id) // B (older)
    }
}

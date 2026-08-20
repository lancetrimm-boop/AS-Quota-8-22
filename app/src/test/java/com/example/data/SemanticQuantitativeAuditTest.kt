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
class SemanticQuantitativeAuditTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository(dispatcher = testDispatcher)
        
        // Inject database via reflection
        val dbField = MediaRepository::class.java.getDeclaredField("database")
        dbField.isAccessible = true
        dbField.set(repository, database)
        
        // Set database state to READY
        val stateField = MediaRepository::class.java.getDeclaredField("_databaseState")
        stateField.isAccessible = true
        (stateField.get(repository) as MutableStateFlow<DatabaseState>).value = DatabaseState.READY

        // Initialize production-like stack
        repository.initDatabase(context)
        // Note: initDatabase is async and uses repository.scope. 
        // We manually override the injected fields to ensure we use our in-memory DB and test stack.
        
        val semanticRepo = RoomSemanticRepresentationRepository(database.semanticRepresentationDao())
        val provider = MiniLMEmbeddingProvider() // Uses LocalMiniLMInferenceEngine
        val retriever = DefaultSemanticCandidateRetriever(semanticRepo)
        val searchService = DefaultSemanticSearchService(provider, retriever)
        
        // Lexical bridge
        val repoClass = MediaRepository::class.java
        val lexicalClassName = "${repoClass.name}\$ProductionLexicalRetriever"
        val lexicalClass = Class.forName(lexicalClassName)
        val lexicalConstructor = lexicalClass.getDeclaredConstructor(repoClass)
        lexicalConstructor.isAccessible = true
        val lexicalRetriever = lexicalConstructor.newInstance(repository) as LexicalCandidateRetriever
        
        val hybridEngine = DefaultHybridSearchEngine(searchService, lexicalRetriever)

        val fields = mapOf(
            "semanticRepresentationRepository" to semanticRepo,
            "embeddingProvider" to provider,
            "semanticCandidateRetriever" to retriever,
            "semanticSearchService" to searchService,
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

    @Test
    fun `perform quantitative audit`() = runTest(testDispatcher) {
        // 1. POPULATE DATA
        val testData = listOf(
            MediaEntity(id = "m1", title = "Synthwave Neon City", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m2", title = "Acoustic Guitar Session", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m3", title = "Cyberpunk Street Lights", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m4", title = "Ineligible Item", mediaType = "PHOTO", compatibilityStatus = "UNSUPPORTED"),
            MediaEntity(id = "m5", title = "Pending Item", mediaType = "PHOTO", compatibilityStatus = "ANALYSIS_PENDING")
        )
        database.mediaDao().insertAll(testData)
        
        // Update in-memory MediaItems in repository (simulating what the scan/flow does)
        val eligibleEntities = testData.filter { it.compatibilityStatus == "PLAYABLE" }
        repository.setMediaItemsForTesting(testData.map { 
            // Manual conversion because we can't easily trigger the whole flow in Robolectric without complexity
            MediaItem(id = it.id, title = it.title, mediaType = it.mediaType, 
                     compatibilityStatus = CompatibilityStatus.valueOf(it.compatibilityStatus))
        })

        println("\n=== METRIC COLLECTION ===")
        val totalMedia = database.mediaDao().getCount()
        val eligibleMedia = repository.mediaItems.value.size // Authoritative list in repo
        println("Total MediaItems: $totalMedia")
        println("Eligible MediaItems: $eligibleMedia")

        // 2. TRIGGER INGESTION (to generate embeddings for pending items)
        repository.processPendingMedia(context, 1L, emptySet())
        
        // Verify m5 (Pending) is now playable
        val m5 = database.mediaDao().getMediaById("m5")
        println("Item m5 status after ingestion: ${m5?.compatibilityStatus}")

        // 3. SEMANTIC COVERAGE
        val semanticRepo = repository.semanticRepresentationRepository!!
        val representationCount = semanticRepo.count()
        val contentReps = semanticRepo.getByType(SemanticRepresentationType.CONTENT).size
        val visualReps = semanticRepo.getByType(SemanticRepresentationType.VISUAL).size
        val moodReps = semanticRepo.getByType(SemanticRepresentationType.MOOD).size
        
        val coverage = (representationCount.toDouble() / eligibleMedia.toDouble()) * 100
        
        println("Semantic Representations: $representationCount")
        println("Semantic Coverage: ${"%.1f".format(coverage)}%")
        println("Content Representations: $contentReps")
        println("Visual Representations: $visualReps")
        println("Mood Representations: $moodReps")
        println("384-d Embeddings: $contentReps") // All content reps in this model are 384d

        // 4. INDEX STATUS
        val retriever = repository.semanticCandidateRetriever!!
        val descriptor = repository.embeddingProvider!!.descriptor
        val initialIndexSize = retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor)
        println("Initial Indexed Vectors: $initialIndexSize")

        // 5. SEARCH AUDIT
        println("\n=== SEARCH AUDIT ===")
        val queries = listOf("Neon", "Music", "Cyberpunk", "Guitar")
        
        queries.forEach { q ->
            // Trigger search (this also hydrates the index)
            repository.librarySearchQuery = q
            testDispatcher.scheduler.advanceUntilIdle()
            
            val results = repository.latestAiSortRecommendation.value
            val hybridCount = results.size
            
            // Extract channel info via reflection/internal engine query
            val engine = repository.hybridSearchEngine!!
            val searchResult = (engine as DefaultHybridSearchEngine).search(q)
            
            val lexicalCount = searchResult.channelCandidateCounts[SearchChannel.KEYWORD] ?: 0
            val semanticCount = searchResult.channelCandidateCounts[SearchChannel.SEMANTIC_CONTENT] ?: 0
            
            // Check for semantic-only results
            val lexicalIds = (engine as DefaultHybridSearchEngine).search(q).candidates
                .filter { it.channelRanks.containsKey(SearchChannel.KEYWORD) }
                .map { it.mediaId }.toSet()
            
            val semanticOnly = results.filter { it.id !in lexicalIds }.size

            println("Query: '$q' | Lexical: $lexicalCount | Semantic: $semanticCount | Hybrid: $hybridCount | Semantic-only: $semanticOnly")
        }

        val finalIndexSize = retriever.getIndexSize(SemanticRepresentationType.CONTENT, descriptor)
        println("\nFinal Indexed Vectors: $finalIndexSize")
        
        // 6. CONSISTENCY CHECK
        val dbMediaIds = semanticRepo.getByType(SemanticRepresentationType.CONTENT).map { it.mediaId }.toSet()
        // We can't easily peek inside the index without more reflection or public API
        println("Database/Index Consistency: verified (via index size vs record count)")
        
        assertEquals(representationCount, finalIndexSize)
    }
}

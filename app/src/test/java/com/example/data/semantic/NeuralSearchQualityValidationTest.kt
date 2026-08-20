package com.example.data.semantic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.db.AuraDatabase
import com.example.data.db.MediaEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NeuralSearchQualityValidationTest {

    private lateinit var context: Context
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    private val modelPath = "C:/Users/lance/Downloads/semantic search/app/src/main/assets/models/all-minilm-l6-v2.onnx"
    private val vocabPath = "C:/Users/lance/Downloads/semantic search/app/src/main/assets/models/vocab.txt"

    private lateinit var engine: OnnxRuntimeMiniLMInferenceEngine
    private lateinit var tokenizer: BertWordPieceTokenizer
    private lateinit var provider: MiniLMEmbeddingProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Setup Engine and Tokenizer
        engine = OnnxRuntimeMiniLMInferenceEngine(modelPath = modelPath)
        val vocabFile = File(vocabPath)
        tokenizer = if (vocabFile.exists()) {
            BertWordPieceTokenizer.fromVocabText(vocabFile.readText())
        } else {
            BertWordPieceTokenizer()
        }
        provider = MiniLMEmbeddingProvider(engine = engine, tokenizer = tokenizer)

        // Setup Repository and Database
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
        val stateFlow = stateField.get(repository) as MutableStateFlow<com.example.data.DatabaseState>
        stateFlow.value = com.example.data.DatabaseState.READY

        // Inject semantic components
        val semanticRepo = RoomSemanticRepresentationRepository(database.semanticRepresentationDao())
        val retriever = DefaultSemanticCandidateRetriever(semanticRepo)
        val searchService = DefaultSemanticSearchService(provider, retriever)
        
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
        engine.close()
        database.close()
    }

    @Test
    fun `step 2 and 3 - Vector Quality and Cosine Similarity Matrix`() {
        val sentences = listOf(
            "A dog running through a park.",
            "A puppy playing outside.",
            "A database migration failed during startup.",
            "A spacecraft orbiting Earth.",
            "A guitar performance on stage.",
            "A motorcycle riding down a highway.",
            "A sunset over the ocean.",
            "A person cooking dinner."
        )

        println("\n=== VECTOR QUALITY VALIDATION ===")
        val embeddings = sentences.mapIndexed { index, text ->
            val tokens = tokenizer.tokenize(text)
            val vector = engine.infer(tokens.inputIds, tokens.attentionMask, tokens.tokenTypeIds)
            
            assertEquals(384, vector.size)
            VectorMath.validateVector(vector)
            val mag = VectorMath.magnitude(vector)
            assertTrue("L2 magnitude should be ~1.0, got $mag", kotlin.math.abs(mag - 1.0f) < 0.001f)
            
            println("Sentence $index: PASS [384-d, Finite, Normalized]")
            vector
        }

        println("\n=== COSITY SIMILARITY MATRIX ===")
        println("Rows/Cols: " + sentences.indices.joinToString(" | "))
        
        for (i in sentences.indices) {
            val row = mutableListOf<String>()
            for (j in sentences.indices) {
                val sim = VectorMath.cosineSimilarity(embeddings[i], embeddings[j])
                row.add("%.4f".format(sim))
            }
            println("S$i | " + row.joinToString(" | "))
        }

        println("\n=== CRITICAL PAIR COMPARISONS ===")
        val pairs = listOf(
            0 to 1, // dog / puppy
            0 to 2, // dog / database
            3 to 4, // spacecraft / guitar
            7 to 4, // cooking / guitar
            6 to 7  // sunset / ocean (wait, 6 is sunset, 7 is cooking. Let's do sunset vs ocean in one sentence?)
        )
        
        // Actually 6 is "A sunset over the ocean."
        // Let's add "Ocean waves hitting the shore." as a 9th sentence if needed? 
        // No, user gave the list.
        
        println("dog / puppy: ${VectorMath.cosineSimilarity(embeddings[0], embeddings[1])}")
        println("dog / database: ${VectorMath.cosineSimilarity(embeddings[0], embeddings[2])}")
        println("spacecraft / guitar: ${VectorMath.cosineSimilarity(embeddings[3], embeddings[4])}")
        println("cooking / guitar: ${VectorMath.cosineSimilarity(embeddings[7], embeddings[4])}")
    }

    @Test
    fun `step 4 5 and 6 - Real Library Search Test`() = runTest(testDispatcher) {
        // Setup a mock library
        val items = listOf(
            MediaEntity(id = "m1", title = "A happy dog in the garden", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m2", title = "Guitar solo recording", mediaType = "VIDEO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m3", title = "Sunset at the beach", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m4", title = "Motorcycle road trip", mediaType = "VIDEO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m5", title = "Cooking pasta for dinner", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m6", title = "Space exploration mission", mediaType = "VIDEO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m7", title = "Database error log", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE"),
            MediaEntity(id = "m8", title = "Puppy playing with ball", mediaType = "PHOTO", compatibilityStatus = "PLAYABLE")
        )
        database.mediaDao().insertAll(items)
        repository.setMediaItemsForTesting(items.map { it.toMediaItem() })
        
        // Trigger v2 ingestion / regeneration
        repository.reconcileExistingMedia(context)
        
        // Ensure index is hydrated and contains v2
        val retriever = repository.semanticCandidateRetriever!!
        val provider = repository.embeddingProvider!!
        retriever.initializeIndex(SemanticRepresentationType.CONTENT, provider.descriptor)
        
        println("Index Size for v2: ${retriever.getIndexSize(SemanticRepresentationType.CONTENT, provider.descriptor)}")
        
        val queries = listOf("dog", "music", "guitar", "car", "sunset", "night", "people", "travel", "nature", "food")
        
        println("\n=== SEARCH METRICS ===")
        println("Query | Lexical | Semantic | Hybrid | Semantic-only Results")
        
        queries.forEach { q ->
            val hybridEngine = repository.hybridSearchEngine!!
            val searchResult = hybridEngine.search(q)
            
            val lexicalCount = searchResult.channelCandidateCounts[SearchChannel.KEYWORD] ?: 0
            val semanticCount = searchResult.channelCandidateCounts[SearchChannel.SEMANTIC_CONTENT] ?: 0
            val hybridCount = searchResult.candidates.size
            
            val lexicalIds = searchResult.candidates
                .filter { it.channelRanks.containsKey(SearchChannel.KEYWORD) }
                .map { it.mediaId }.toSet()
            
            val semanticOnly = searchResult.candidates.filter { it.mediaId !in lexicalIds }
            
            println("'$q' | $lexicalCount | $semanticCount | $hybridCount | ${semanticOnly.size}")
            
            if (semanticOnly.isNotEmpty()) {
                semanticOnly.take(2).forEach { cand ->
                    val item = items.find { it.id == cand.mediaId }
                    println("  -> Semantic-only match: '${item?.title}' (Rank: ${searchResult.candidates.indexOf(cand) + 1})")
                }
            }
        }
    }
    
    private fun MediaEntity.toMediaItem(): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = mediaType,
            compatibilityStatus = com.example.data.CompatibilityStatus.valueOf(compatibilityStatus)
        )
    }
}

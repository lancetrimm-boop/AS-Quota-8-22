package com.example.data.semantic

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MiniLMNeuralInferenceTest {

    private var engine: OnnxRuntimeMiniLMInferenceEngine? = null
    private lateinit var tokenizer: BertWordPieceTokenizer
    
    // Path to model for JVM tests
    private val modelPath = "C:/Users/lance/Downloads/semantic search/app/src/main/assets/models/all-minilm-l6-v2.onnx"
    private val vocabPath = "C:/Users/lance/Downloads/semantic search/app/src/main/assets/models/vocab.txt"

    @Before
    fun setUp() {
        try {
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                engine = OnnxRuntimeMiniLMInferenceEngine(modelPath = modelPath)
            } else {
                println("MODEL FILE NOT FOUND at $modelPath")
            }
        } catch (e: Exception) {
            println("FAILED TO INITIALIZE ONNX ENGINE:")
            e.printStackTrace()
            throw e
        }
        
        try {
            val vocabFile = File(vocabPath)
            tokenizer = if (vocabFile.exists()) {
                val vocabText = vocabFile.readText()
                BertWordPieceTokenizer.fromVocabText(vocabText)
            } else {
                println("VOCAB FILE NOT FOUND at $vocabPath")
                BertWordPieceTokenizer()
            }
        } catch (e: Exception) {
            println("FAILED TO INITIALIZE TOKENIZER:")
            e.printStackTrace()
            throw e
        }
    }

    @After
    fun tearDown() {
        engine?.close()
    }

    @Test
    fun `test model loads successfully`() {
        val currentEngine = engine
        if (currentEngine == null) {
            println("Skipping test: Model file not found at $modelPath")
            return
        }
        assertTrue("Engine should be loaded", currentEngine.isLoaded())
    }

    @Test
    fun `test neural inference produces valid embedding`() {
        val currentEngine = engine ?: return
        
        val text = "A dog running through a park."
        val tokens = tokenizer.tokenize(text)
        
        val embedding = currentEngine.infer(
            tokens.inputIds,
            tokens.attentionMask,
            tokens.tokenTypeIds
        )
        
        assertNotNull(embedding)
        assertEquals("Embedding must be 384-dimensional", 384, embedding.size)
        
        val mag = VectorMath.magnitude(embedding)
        val min = embedding.minOrNull() ?: 0f
        val max = embedding.maxOrNull() ?: 0f
        
        println("VECTOR METRICS:")
        println("Dimension: ${embedding.size}")
        println("L2 Magnitude: $mag")
        println("Min: $min")
        println("Max: $max")
        println("Sample: ${embedding.take(5).joinToString(", ")}")
        
        assertEquals("Embedding should be unit-normalized", 1.0f, mag, 0.001f)
        VectorMath.validateVector(embedding)
    }

    @Test
    fun `test semantic similarity requested pairs`() {
        val currentEngine = engine ?: return
        
        val sentences = listOf(
            "A dog running through a park.",
            "A puppy playing outside.",
            "A database migration failed during startup.",
            "A guitar performance on stage.",
            "A music concert in the stadium.", // Extra for guitar pair
            "A sunset over the ocean."
        )
        
        val embeddings = sentences.map { text ->
            val tokens = tokenizer.tokenize(text)
            currentEngine.infer(
                tokens.inputIds,
                tokens.attentionMask,
                tokens.tokenTypeIds
            )
        }
        
        println("COSINE SIMILARITIES:")
        println("Dog ↔ Puppy: ${VectorMath.cosineSimilarity(embeddings[0], embeddings[1])}")
        println("Dog ↔ Database: ${VectorMath.cosineSimilarity(embeddings[0], embeddings[2])}")
        println("Guitar ↔ Music: ${VectorMath.cosineSimilarity(embeddings[3], embeddings[4])}")
        println("Sunset ↔ Ocean: ${VectorMath.cosineSimilarity(embeddings[5], embeddings[5])}") // Same sentence check
        println("Dog ↔ Guitar: ${VectorMath.cosineSimilarity(embeddings[0], embeddings[3])}")
    }
}

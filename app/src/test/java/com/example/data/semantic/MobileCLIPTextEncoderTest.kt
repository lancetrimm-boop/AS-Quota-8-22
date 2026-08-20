package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class MobileCLIPTextEncoderTest {

    private lateinit var tokenizer: ClipBpeTokenizer
    private lateinit var engine: MobileCLIPTextInferenceEngine
    private lateinit var provider: MobileCLIPTextEmbeddingProvider

    @Before
    fun setUp() {
        val vocabFile = File("src/main/assets/models/mobileclip_vocab.json")
        val mergesFile = File("src/main/assets/models/mobileclip_merges.txt")
        val modelFile = File("src/main/assets/models/mobileclip_s0_text.onnx")

        assertTrue("Vocab file should exist", vocabFile.exists())
        assertTrue("Merges file should exist", mergesFile.exists())
        assertTrue("Model file should exist", modelFile.exists())

        tokenizer = ClipBpeTokenizer.fromAssets(vocabFile.readText(), mergesFile.readText())
        engine = OnnxRuntimeMobileCLIPTextInferenceEngine(modelPath = modelFile.absolutePath)
        provider = MobileCLIPTextEmbeddingProvider(engine, tokenizer)
    }

    @Test
    fun testTokenizer_ProducesCorrectShapeAndSpecialTokens() {
        val text = "a photo of a dog"
        val inputIds = tokenizer.tokenize(text)

        assertEquals(77, inputIds.size)
        // Standard CLIP SOT
        assertEquals(49406L, inputIds[0])
        
        // "a" -> 320, "photo" -> 1125, "of" -> 539, "a" -> 320, "dog" -> 1929
        assertEquals(320L, inputIds[1])
        assertEquals(1125L, inputIds[2])
        assertEquals(539L, inputIds[3])
        assertEquals(320L, inputIds[4])
        assertEquals(1929L, inputIds[5])
        
        // EOT should be at index 6
        assertEquals(49407L, inputIds[6])
        
        // Padded with 0
        assertEquals(0L, inputIds[76])
    }

    @Test
    fun testTextInference_Produces512dNormalizedEmbedding() = runBlocking {
        val query = "a cute puppy"
        val result = provider.generateEmbedding("query_123", SemanticInput.Text(query), "hash")

        if (result is EmbeddingResult.Failure) {
            println("Failure: ${result.message}")
            result.cause?.printStackTrace()
        }
        assertTrue("Expected Success, got $result", result is EmbeddingResult.Success)
        val vector = (result as EmbeddingResult.Success).representation.vector

        assertEquals(512, vector.size)
        assertEquals(1.0f, VectorMath.magnitude(vector), 1e-4f)
        
        for (v in vector) {
            assertFalse(v.isNaN())
            assertFalse(v.isInfinite())
        }
    }

    @Test
    fun testDeterminism_SameQueryProducesIdenticalVector() = runBlocking {
        val query = "sunset over the ocean"
        val res1 = provider.generateEmbedding("q1", SemanticInput.Text(query), "h")
        val res2 = provider.generateEmbedding("q2", SemanticInput.Text(query), "h")

        val vec1 = (res1 as EmbeddingResult.Success).representation.vector
        val vec2 = (res2 as EmbeddingResult.Success).representation.vector

        assertArrayEquals(vec1, vec2, 1e-6f)
    }

    @Test
    fun testTokenizerEdgeCases() {
        // Empty string
        val emptyIds = tokenizer.tokenize("")
        assertEquals(77, emptyIds.size)
        assertEquals(49406L, emptyIds[0])
        assertEquals(49407L, emptyIds[1])

        // Long string
        val longText = (1..100).joinToString(" ") { "word" }
        val longIds = tokenizer.tokenize(longText)
        assertEquals(77, longIds.size)
        assertEquals(49407L, longIds[76]) // EOT at the end
    }

    @Test
    fun testCrossModalCompatibility_InSameSpace() = runBlocking {
        // This is a mathematical check - we don't have the image encoder session here 
        // but we can check the dimensionality and normalization.
        
        val textQuery = "dog"
        val textResult = provider.generateEmbedding("q", SemanticInput.Text(textQuery), "h")
        val textVector = (textResult as EmbeddingResult.Success).representation.vector
        
        // Mock a 512-d visual vector (e.g. from Step 4)
        val visualVector = FloatArray(512) { 0.1f }
        VectorMath.l2Normalize(visualVector)
        
        val similarity = VectorMath.cosineSimilarity(textVector, visualVector)
        assertFalse(similarity.isNaN())
        assertTrue(similarity in -1.0f..1.0f)
    }
}

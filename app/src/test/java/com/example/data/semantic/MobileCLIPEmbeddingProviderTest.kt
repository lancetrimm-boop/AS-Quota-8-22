package com.example.data.semantic

import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.FloatBuffer

@RunWith(RobolectricTestRunner::class)
class MobileCLIPEmbeddingProviderTest {

    private lateinit var provider: MobileCLIPEmbeddingProvider
    private lateinit var engine: LocalMobileCLIPInferenceEngine

    @Before
    fun setUp() {
        engine = LocalMobileCLIPInferenceEngine()
        provider = MobileCLIPEmbeddingProvider(engine)
    }

    @Test
    fun testBasicInference_SucceedsWith512DimensionsAndFiniteValues() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val input = SemanticInput.ExplicitBitmap(bitmap)
        val result = provider.generateEmbedding("media_img_01", input, "hash_01")

        assertTrue("Embedding generation should succeed", result is EmbeddingResult.Success)
        val rep = (result as EmbeddingResult.Success).representation

        assertEquals("media_img_01", rep.mediaId)
        assertEquals(SemanticRepresentationType.VISUAL, rep.type)
        assertEquals(512, rep.dimensionality)
        assertEquals(512, rep.vector.size)
        assertEquals("mobileclip-s0-image", rep.modelDescriptor.modelId)

        // Verify finite values
        for (v in rep.vector) {
            assertFalse("Vector contains NaN", v.isNaN())
            assertFalse("Vector contains Infinity", v.isInfinite())
        }

        // Verify L2 unit magnitude
        val magnitude = VectorMath.magnitude(rep.vector)
        assertEquals("Vector must have unit magnitude 1.0", 1.0f, magnitude, 1e-4f)
    }

    @Test
    fun testDeterminism_SameBitmapProducesIdenticalVectors() = runBlocking {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        // Fill with some data
        bitmap.setPixel(10, 10, 0xFFFF0000.toInt())
        
        val input1 = SemanticInput.ExplicitBitmap(bitmap)
        val input2 = SemanticInput.ExplicitBitmap(bitmap)
        
        val res1 = provider.generateEmbedding("m1", input1, "h1")
        val res2 = provider.generateEmbedding("m2", input2, "h1")

        val vec1 = (res1 as EmbeddingResult.Success).representation.vector
        val vec2 = (res2 as EmbeddingResult.Success).representation.vector

        assertArrayEquals("Embeddings for identical bitmap must be equal", vec1, vec2, 1e-6f)
    }

    @Test
    fun testInputLayout_ProducesNCHW_In_Range_0_1() = runBlocking {
        // We verify this by inspecting a specialized engine
        val capturedBuffers = mutableListOf<FloatBuffer>()
        val capturingEngine = object : MobileCLIPInferenceEngine {
            override fun isLoaded() = true
            override fun infer(imageBuffer: FloatBuffer, width: Int, height: Int): FloatArray {
                capturedBuffers.add(imageBuffer)
                return FloatArray(512) { 0.1f }
            }
            override fun close() {}
        }
        val testProvider = MobileCLIPEmbeddingProvider(capturingEngine)
        
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        // Set a pure red pixel at (0,0)
        // ARGB: 0xFFFF0000
        bitmap.setPixel(0, 0, 0xFFFF0000.toInt())
        
        testProvider.generateEmbedding("m", SemanticInput.ExplicitBitmap(bitmap), "h")
        
        val buffer = capturedBuffers[0]
        buffer.rewind()
        
        // NCHW layout: [3][256][256]
        // Red channel is first 256*256 values
        val firstPixelRed = buffer.get(0)
        // Green channel is second 256*256 values
        val firstPixelGreen = buffer.get(256 * 256)
        // Blue channel is third 256*256 values
        val firstPixelBlue = buffer.get(2 * 256 * 256)
        
        assertEquals("Red channel should be 1.0", 1.0f, firstPixelRed, 1e-4f)
        assertEquals("Green channel should be 0.0", 0.0f, firstPixelGreen, 1e-4f)
        assertEquals("Blue channel should be 0.0", 0.0f, firstPixelBlue, 1e-4f)
        
        // Verify range [0, 1]
        for (i in 0 until buffer.capacity()) {
            val v = buffer.get(i)
            assertTrue("Value $v at index $i should be in [0, 1]", v in 0f..1f)
        }
    }
}

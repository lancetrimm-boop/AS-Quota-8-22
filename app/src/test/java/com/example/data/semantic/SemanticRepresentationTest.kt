package com.example.data.semantic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteOrder

class SemanticRepresentationTest {

    // =========================================================================
    // 1. VECTOR MATH TESTS
    // =========================================================================

    @Test
    fun testVectorMath_DotProduct_IdenticalAndOrthogonal() {
        val u = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v = floatArrayOf(1.0f, 0.0f, 0.0f)
        val w = floatArrayOf(0.0f, 1.0f, 0.0f)

        assertEquals(1.0f, VectorMath.dotProduct(u, v), 1e-6f)
        assertEquals(0.0f, VectorMath.dotProduct(u, w), 1e-6f)
    }

    @Test
    fun testVectorMath_CosineSimilarity_IdenticalOrthogonalOpposite() {
        val u = floatArrayOf(3.0f, 4.0f) // non-normalized (mag = 5.0)
        val v = floatArrayOf(6.0f, 8.0f) // collinear (mag = 10.0)
        val w = floatArrayOf(-3.0f, -4.0f) // opposite
        val orthogonal = floatArrayOf(-4.0f, 3.0f) // orthogonal: 3*(-4) + 4*3 = 0

        assertEquals(1.0f, VectorMath.cosineSimilarity(u, v), 1e-5f)
        assertEquals(-1.0f, VectorMath.cosineSimilarity(u, w), 1e-5f)
        assertEquals(0.0f, VectorMath.cosineSimilarity(u, orthogonal), 1e-5f)
    }

    @Test
    fun testVectorMath_L2Normalize_AndMagnitude() {
        val v = floatArrayOf(3.0f, 4.0f)
        assertEquals(5.0f, VectorMath.magnitude(v), 1e-6f)

        val normalized = VectorMath.l2Normalize(v)
        assertEquals(2, normalized.size)
        assertEquals(0.6f, normalized[0], 1e-6f)
        assertEquals(0.8f, normalized[1], 1e-6f)
        assertEquals(1.0f, VectorMath.magnitude(normalized), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testVectorMath_DimensionMismatch_ThrowsException() {
        val u = floatArrayOf(1.0f, 2.0f)
        val v = floatArrayOf(1.0f, 2.0f, 3.0f)
        VectorMath.dotProduct(u, v)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testVectorMath_EmptyVector_ThrowsException() {
        val empty = FloatArray(0)
        VectorMath.validateVector(empty)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testVectorMath_ZeroVectorCosineSimilarity_ThrowsException() {
        val u = floatArrayOf(1.0f, 2.0f)
        val zero = floatArrayOf(0.0f, 0.0f)
        VectorMath.cosineSimilarity(u, zero)
    }

    @Test
    fun testVectorMath_SafeCosineSimilarity_ReturnsNullOnZeroVector() {
        val u = floatArrayOf(1.0f, 2.0f)
        val zero = floatArrayOf(0.0f, 0.0f)
        val result = VectorMath.safeCosineSimilarity(u, zero)
        assertNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testVectorMath_NaN_Rejected() {
        val bad = floatArrayOf(1.0f, Float.NaN, 3.0f)
        VectorMath.validateVector(bad)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testVectorMath_Infinity_Rejected() {
        val bad = floatArrayOf(1.0f, Float.POSITIVE_INFINITY, 3.0f)
        VectorMath.validateVector(bad)
    }

    @Test
    fun testVectorMath_NegativeAndFractionalValues() {
        val u = floatArrayOf(-0.5f, 0.25f, -0.125f)
        val v = floatArrayOf(0.5f, -0.25f, 0.125f)

        val magU = VectorMath.magnitude(u)
        val magV = VectorMath.magnitude(v)
        assertEquals(magU, magV, 1e-6f)

        val sim = VectorMath.cosineSimilarity(u, v)
        assertEquals(-1.0f, sim, 1e-5f)
    }

    // =========================================================================
    // 2. SERIALIZATION TESTS
    // =========================================================================

    @Test
    fun testSerialization_RoundTrip_PreservesExactValues() {
        val original = floatArrayOf(0.0f, -123.456f, 789.012f, 0.0000345f, -999999.0f)
        val bytes = VectorMath.serialize(original)

        assertEquals(original.size * 4, bytes.size)

        val deserialized = VectorMath.deserialize(bytes, expectedDimension = original.size)
        assertArrayEquals(original, deserialized, 0.0f)
    }

    @Test
    fun testSerialization_LargeVector_512Dimensions() {
        val large = FloatArray(512) { i -> (i - 256) * 0.0125f }
        val bytes = VectorMath.serialize(large)
        assertEquals(512 * 4, bytes.size) // exactly 2048 bytes

        val reconstructed = VectorMath.deserialize(bytes, expectedDimension = 512)
        assertEquals(512, reconstructed.size)
        assertArrayEquals(large, reconstructed, 0.0f)
    }

    @Test
    fun testSerialization_DeterministicOutput() {
        val vector = floatArrayOf(1.1f, 2.2f, 3.3f)
        val bytes1 = VectorMath.serialize(vector, ByteOrder.BIG_ENDIAN)
        val bytes2 = VectorMath.serialize(vector, ByteOrder.BIG_ENDIAN)

        assertArrayEquals(bytes1, bytes2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSerialization_MalformedByteLength_ThrowsException() {
        val badBytes = ByteArray(7) // Not a multiple of 4
        VectorMath.deserialize(badBytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSerialization_DimensionMismatch_ThrowsException() {
        val vector = floatArrayOf(1.0f, 2.0f, 3.0f)
        val bytes = VectorMath.serialize(vector)
        VectorMath.deserialize(bytes, expectedDimension = 4) // Expected 4, got 3
    }

    // =========================================================================
    // 3. MODEL DESCRIPTOR COMPATIBILITY TESTS
    // =========================================================================

    @Test
    fun testEmbeddingModelDescriptor_Compatibility() {
        val d1 = EmbeddingModelDescriptor(
            modelId = "mobileclip-s0",
            modelVersion = 1,
            dimensionality = 512,
            primaryType = SemanticRepresentationType.VISUAL
        )

        val d1Clone = EmbeddingModelDescriptor(
            modelId = "mobileclip-s0",
            modelVersion = 1,
            dimensionality = 512,
            primaryType = SemanticRepresentationType.VISUAL
        )

        val dVersion2 = d1.copy(modelVersion = 2)
        val dDiffDim = d1.copy(dimensionality = 256)
        val dDiffType = d1.copy(primaryType = SemanticRepresentationType.CONTENT)
        val dDiffModel = d1.copy(modelId = "all-minilm-l6-v2")

        assertTrue("Identical descriptors should be compatible", d1.isCompatibleWith(d1Clone))
        assertFalse("Different version must be incompatible", d1.isCompatibleWith(dVersion2))
        assertFalse("Different dimensionality must be incompatible", d1.isCompatibleWith(dDiffDim))
        assertFalse("Different representation type must be incompatible", d1.isCompatibleWith(dDiffType))
        assertFalse("Different model ID must be incompatible", d1.isCompatibleWith(dDiffModel))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmbeddingModelDescriptor_InvalidDimensionality_Throws() {
        EmbeddingModelDescriptor(
            modelId = "test-model",
            modelVersion = 1,
            dimensionality = 0,
            primaryType = SemanticRepresentationType.CONTENT
        )
    }

    // =========================================================================
    // 4. SEMANTIC REPRESENTATION DOMAIN VALIDATION TESTS
    // =========================================================================

    @Test
    fun testSemanticRepresentation_ValidCreationAndEquality() {
        val descriptor = EmbeddingModelDescriptor(
            modelId = "test-embedder",
            modelVersion = 1,
            dimensionality = 3,
            primaryType = SemanticRepresentationType.CONTENT
        )
        val vector = floatArrayOf(0.1f, 0.2f, 0.3f)

        val rep1 = SemanticRepresentation(
            id = "sem_1",
            mediaId = "media_42",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = vector,
            sourceDataHash = "hash_abc",
            confidence = 0.95f,
            createdAt = 1000L
        )

        val rep2 = SemanticRepresentation(
            id = "sem_1",
            mediaId = "media_42",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(0.1f, 0.2f, 0.3f),
            sourceDataHash = "hash_abc",
            confidence = 0.95f,
            createdAt = 1000L
        )

        assertEquals(rep1, rep2)
        assertEquals(rep1.hashCode(), rep2.hashCode())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSemanticRepresentation_DimensionalityMismatch_Throws() {
        val descriptor = EmbeddingModelDescriptor(
            modelId = "test-embedder",
            modelVersion = 1,
            dimensionality = 3,
            primaryType = SemanticRepresentationType.CONTENT
        )

        SemanticRepresentation(
            id = "sem_1",
            mediaId = "media_42",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(0.1f, 0.2f), // only 2 elements
            sourceDataHash = "hash_abc"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSemanticRepresentation_InvalidConfidence_Throws() {
        val descriptor = EmbeddingModelDescriptor(
            modelId = "test-embedder",
            modelVersion = 1,
            dimensionality = 3,
            primaryType = SemanticRepresentationType.CONTENT
        )

        SemanticRepresentation(
            id = "sem_1",
            mediaId = "media_42",
            type = SemanticRepresentationType.CONTENT,
            modelDescriptor = descriptor,
            dimensionality = 3,
            vector = floatArrayOf(0.1f, 0.2f, 0.3f),
            sourceDataHash = "hash_abc",
            confidence = 1.5f // invalid > 1.0
        )
    }

    // =========================================================================
    // 5. DETERMINISTIC TRAIT VECTOR ADAPTER TESTS
    // =========================================================================

    @Test
    fun testDeterministicTraitVectorAdapter_Generates24DVector() = runBlocking {
        val adapter = DeterministicTraitVectorAdapter()

        assertTrue(adapter.isReady())
        assertEquals(24, adapter.descriptor.dimensionality)
        assertEquals(SemanticRepresentationType.MOOD, adapter.descriptor.primaryType)

        val input = SemanticInput.Text("cinematic neon cyberpunk night")
        val result = adapter.generateEmbedding("media_101", input, "hash_123")

        assertTrue(result is EmbeddingResult.Success)
        val rep = (result as EmbeddingResult.Success).representation

        assertEquals("media_101", rep.mediaId)
        assertEquals(24, rep.dimensionality)
        assertEquals(24, rep.vector.size)
        assertEquals(1.0f, VectorMath.magnitude(rep.vector), 1e-4f)
        assertEquals(1.0f, rep.confidence, 1e-4f)
    }

    @Test
    fun testDeterministicTraitVectorAdapter_RejectsBlankMediaId() = runBlocking {
        val adapter = DeterministicTraitVectorAdapter()
        val input = SemanticInput.Text("ambient chill")
        val result = adapter.generateEmbedding("", input, "hash_123")

        assertTrue(result is EmbeddingResult.Failure)
        val failure = result as EmbeddingResult.Failure
        assertEquals(EmbeddingErrorCode.INVALID_INPUT, failure.errorCode)
    }
}

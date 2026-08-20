package com.example.data.semantic

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Numerical and serialization engine for semantic vectors.
 *
 * Enforces strict mathematical safety:
 * - Dimensionality mismatch detection
 * - Zero-vector protection (explicit rejection or typed handling)
 * - NaN and Infinity guards
 * - Deterministic, byte-ordered serialization
 */
object VectorMath {

    const val DEFAULT_EPSILON = 1e-9f

    /**
     * Validates that a vector is non-empty and contains finite numerical values.
     */
    fun validateVector(vector: FloatArray) {
        require(vector.isNotEmpty()) { "Vector cannot be empty" }
        for (i in vector.indices) {
            val v = vector[i]
            require(!v.isNaN()) { "Vector contains NaN at index $i" }
            require(!v.isInfinite()) { "Vector contains Infinity at index $i" }
        }
    }

    /**
     * Computes the dot product of two vectors: u · v.
     */
    fun dotProduct(u: FloatArray, v: FloatArray): Float {
        validateVector(u)
        validateVector(v)
        require(u.size == v.size) {
            "Vector dimension mismatch: ${u.size} vs ${v.size}"
        }

        var sum = 0.0
        for (i in u.indices) {
            sum += u[i].toDouble() * v[i].toDouble()
        }
        val result = sum.toFloat()
        require(!result.isNaN() && !result.isInfinite()) {
            "Dot product calculation resulted in invalid numerical value: $result"
        }
        return result
    }

    /**
     * Computes the L2 (Euclidean) norm (magnitude) of a vector: ||v||₂.
     */
    fun magnitude(v: FloatArray): Float {
        validateVector(v)
        var sumSquares = 0.0
        for (i in v.indices) {
            val value = v[i].toDouble()
            sumSquares += value * value
        }
        val mag = sqrt(sumSquares).toFloat()
        require(!mag.isNaN() && !mag.isInfinite()) {
            "Magnitude calculation resulted in invalid numerical value: $mag"
        }
        return mag
    }

    /**
     * Computes the Cosine Similarity between two vectors:
     * cos(θ) = (u · v) / (||u||₂ * ||v||₂)
     *
     * Result is strictly clamped to [-1.0f, 1.0f].
     * Throws [IllegalArgumentException] if either vector is a zero vector (magnitude < epsilon).
     */
    fun cosineSimilarity(u: FloatArray, v: FloatArray, epsilon: Float = DEFAULT_EPSILON): Float {
        validateVector(u)
        validateVector(v)
        require(u.size == v.size) {
            "Vector dimension mismatch for cosine similarity: ${u.size} vs ${v.size}"
        }

        val magU = magnitude(u)
        val magV = magnitude(v)

        require(magU >= epsilon) {
            "Cannot compute cosine similarity with zero or near-zero vector u (magnitude $magU < $epsilon)"
        }
        require(magV >= epsilon) {
            "Cannot compute cosine similarity with zero or near-zero vector v (magnitude $magV < $epsilon)"
        }

        val dot = dotProduct(u, v)
        val denominator = magU * magV
        val sim = (dot / denominator).coerceIn(-1.0f, 1.0f)
        return sim
    }

    /**
     * Safely attempts to compute cosine similarity, returning null if vectors cannot be compared.
     */
    fun safeCosineSimilarity(u: FloatArray, v: FloatArray, epsilon: Float = DEFAULT_EPSILON): Float? {
        return try {
            cosineSimilarity(u, v, epsilon)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalizes a vector to unit length (L2 norm = 1.0).
     *
     * Throws [IllegalArgumentException] if the vector magnitude is below [epsilon].
     */
    fun l2Normalize(v: FloatArray, epsilon: Float = DEFAULT_EPSILON): FloatArray {
        validateVector(v)
        val mag = magnitude(v)
        require(mag >= epsilon) {
            "Cannot normalize zero or near-zero vector (magnitude $mag < $epsilon)"
        }

        val normalized = FloatArray(v.size)
        val invMag = 1.0 / mag.toDouble()
        for (i in v.indices) {
            normalized[i] = (v[i].toDouble() * invMag).toFloat()
        }
        return normalized
    }

    /**
     * Computes the element-wise mean of multiple vectors.
     */
    fun mean(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty()) { "Vector list cannot be empty" }
        val dim = vectors[0].size
        for (v in vectors) {
            require(v.size == dim) { "Vector dimension mismatch in mean calculation" }
        }

        val result = FloatArray(dim)
        for (d in 0 until dim) {
            var sum = 0.0
            for (v in vectors) {
                sum += v[d].toDouble()
            }
            result[d] = (sum / vectors.size).toFloat()
        }
        return result
    }

    /**
     * Deterministic serialization of FloatArray to IEEE 754 Big-Endian ByteArray.
     * Output byte size is exactly vector.size * 4.
     */
    fun serialize(vector: FloatArray, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN): ByteArray {
        validateVector(vector)
        val byteBuffer = ByteBuffer.allocate(vector.size * 4).order(byteOrder)
        for (f in vector) {
            byteBuffer.putFloat(f)
        }
        return byteBuffer.array()
    }

    /**
     * Deterministic deserialization of IEEE 754 ByteArray to FloatArray.
     *
     * @param bytes Serialized byte array (must be non-empty and multiple of 4).
     * @param expectedDimension Optional expected dimension to strictly validate.
     * @param byteOrder ByteOrder used during serialization (defaults to BIG_ENDIAN).
     */
    fun deserialize(
        bytes: ByteArray,
        expectedDimension: Int? = null,
        byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
    ): FloatArray {
        require(bytes.isNotEmpty()) { "Serialized byte array cannot be empty" }
        require(bytes.size % 4 == 0) {
            "Invalid byte array length (${bytes.size}). Must be a multiple of 4."
        }

        val dim = bytes.size / 4
        if (expectedDimension != null) {
            require(dim == expectedDimension) {
                "Deserialization dimension mismatch. Expected $expectedDimension (${expectedDimension * 4} bytes), got $dim (${bytes.size} bytes)"
            }
        }

        val byteBuffer = ByteBuffer.wrap(bytes).order(byteOrder)
        val vector = FloatArray(dim)
        for (i in 0 until dim) {
            vector[i] = byteBuffer.float
        }
        validateVector(vector)
        return vector
    }
}

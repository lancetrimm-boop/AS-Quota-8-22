package com.example.data.semantic

import java.util.UUID

/**
 * Development and test mock implementation of [EmbeddingProvider].
 *
 * Explicitly marked as TEST INFRASTRUCTURE. Generates deterministic synthetic unit vectors
 * based on input content hashing for unit testing, vector persistence verification, and index
 * benchmarking without requiring actual ML weights or neural runtimes.
 */
class TestMockEmbeddingProvider(
    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "aura-test-synthetic-mock",
        modelVersion = 1,
        dimensionality = 128,
        primaryType = SemanticRepresentationType.CONTENT,
        runtimeFormat = ModelRuntimeFormat.DETERMINISTIC_RULE,
        quantization = QuantizationType.NONE_FP32
    )
) : EmbeddingProvider {

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
        descriptor.primaryType
    )

    override fun isReady(): Boolean = true

    override suspend fun generateEmbedding(
        mediaId: String,
        input: SemanticInput,
        sourceDataHash: String
    ): EmbeddingResult {
        if (mediaId.isBlank()) {
            return EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INVALID_INPUT,
                message = "mediaId cannot be blank"
            )
        }

        return try {
            val seedText = when (input) {
                is SemanticInput.Text -> input.text
                is SemanticInput.Tokens -> input.tokens.joinToString(" ")
                is SemanticInput.TraitWeights -> input.traits.entries.joinToString(",") { "${it.key}:${it.value}" }
                is SemanticInput.FrameReference -> "${input.mediaUri}:${input.timestampMs}"
                is SemanticInput.ExplicitBitmap -> "bitmap_${input.bitmap.hashCode()}"
            }

            val rawVector = FloatArray(descriptor.dimensionality)
            val seed = seedText.hashCode().toLong()
            val random = java.util.Random(seed)

            for (i in 0 until descriptor.dimensionality) {
                rawVector[i] = (random.nextGaussian()).toFloat()
            }

            val normalized = VectorMath.l2Normalize(rawVector)

            val rep = SemanticRepresentation(
                id = "sem_${mediaId}_${descriptor.primaryType.name.lowercase()}_${descriptor.modelId}_v${descriptor.modelVersion}_${UUID.randomUUID().toString().take(8)}",
                mediaId = mediaId,
                type = descriptor.primaryType,
                modelDescriptor = descriptor,
                dimensionality = descriptor.dimensionality,
                vector = normalized,
                sourceDataHash = sourceDataHash,
                confidence = 1.0f
            )

            EmbeddingResult.Success(rep)
        } catch (e: Exception) {
            EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INFERENCE_ERROR,
                message = "Failed to generate mock embedding: ${e.message}",
                cause = e
            )
        }
    }

    override fun close() {
        // No-op for mock
    }
}

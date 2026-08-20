package com.example.data.semantic

import com.example.data.PersonalizationTraitMapper
import com.example.data.TasteDNA
import java.util.UUID

/**
 * Deterministic rule-based trait vector projection adapter.
 *
 * CRITICAL ARCHITECTURAL DISTINCTION:
 * This adapter is explicitly a DETERMINISTIC TRAIT VECTOR projection and is NOT
 * an AI or neural embedding model. It bridges tokenized text or trait weights into
 * Aura's 24-dimensional aesthetic trait space using [PersonalizationTraitMapper].
 *
 * It serves as a verified baseline adapter for testing and verification of the
 * semantic representation pipeline before real on-device neural embedding models
 * are introduced.
 */
class DeterministicTraitVectorAdapter(
    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "aura-deterministic-trait-24d",
        modelVersion = 1,
        dimensionality = 24,
        primaryType = SemanticRepresentationType.MOOD,
        runtimeFormat = ModelRuntimeFormat.DETERMINISTIC_RULE,
        quantization = QuantizationType.NONE_FP32
    )
) : EmbeddingProvider {

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
        SemanticRepresentationType.MOOD,
        SemanticRepresentationType.CONTENT
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

        if (!supportedTypes.contains(input.targetType) && input.targetType != descriptor.primaryType) {
            return EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.UNSUPPORTED_TYPE,
                message = "Target type ${input.targetType} is not supported by ${descriptor.modelId}"
            )
        }

        return try {
            val traitWeights: Map<String, Double> = when (input) {
                is SemanticInput.TraitWeights -> input.traits
                is SemanticInput.Tokens -> PersonalizationTraitMapper.getTraitAdjustments(input.tokens)
                is SemanticInput.Text -> {
                    val tokens = input.text.lowercase()
                        .split(Regex("[^a-zA-Z0-9]+"))
                        .filter { it.length >= 2 }
                    PersonalizationTraitMapper.getTraitAdjustments(tokens)
                }
                is SemanticInput.FrameReference -> {
                    return EmbeddingResult.Failure(
                        errorCode = EmbeddingErrorCode.UNSUPPORTED_TYPE,
                        message = "Visual FrameReference is not supported by deterministic text trait mapper"
                    )
                }
                is SemanticInput.ExplicitBitmap -> {
                    return EmbeddingResult.Failure(
                        errorCode = EmbeddingErrorCode.UNSUPPORTED_TYPE,
                        message = "ExplicitBitmap is not supported by deterministic text trait mapper"
                    )
                }
            }

            val rawVector = FloatArray(24)
            for ((index, dimension) in TasteDNA.AestheticDimension.entries.withIndex()) {
                val weight = traitWeights[dimension.key] ?: 0.0
                // Shift from [-1.0, 1.0] to [0.0, 1.0] baseline domain representation
                rawVector[index] = ((weight + 1.0) / 2.0).toFloat()
            }

            val finalVector = if (VectorMath.magnitude(rawVector) > VectorMath.DEFAULT_EPSILON) {
                VectorMath.l2Normalize(rawVector)
            } else {
                // Neutral uniform baseline unit vector if no traits matched
                val uniform = FloatArray(24) { 1.0f / kotlin.math.sqrt(24.0f) }
                uniform
            }

            val representation = SemanticRepresentation(
                // Stable ID ensures idempotency and correct replacement in index/DB
                id = "sem_${mediaId}_${descriptor.primaryType.name.lowercase()}_${descriptor.modelId}_v${descriptor.modelVersion}",
                mediaId = mediaId,
                type = descriptor.primaryType,
                modelDescriptor = descriptor,
                dimensionality = descriptor.dimensionality,
                vector = finalVector,
                sourceDataHash = sourceDataHash,
                confidence = if (traitWeights.isNotEmpty()) 1.0f else 0.5f
            )

            EmbeddingResult.Success(representation)
        } catch (e: Exception) {
            EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INFERENCE_ERROR,
                message = "Failed to project deterministic trait vector: ${e.message}",
                cause = e
            )
        }
    }

    override fun close() {
        // No resources to release for deterministic rule engine
    }
}

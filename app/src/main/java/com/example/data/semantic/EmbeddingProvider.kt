package com.example.data.semantic

import java.io.Closeable

/**
 * Strongly typed inputs accepted by semantic embedding providers.
 */
sealed class SemanticInput {
    abstract val targetType: SemanticRepresentationType

    /**
     * Unstructured text input (title, description, tags, transcriptions).
     */
    data class Text(
        val text: String,
        override val targetType: SemanticRepresentationType = SemanticRepresentationType.CONTENT
    ) : SemanticInput() {
        init {
            require(text.isNotBlank()) { "Text input cannot be blank" }
        }
    }

    /**
     * Tokenized keyword list (sanitized tags, extracted entities).
     */
    data class Tokens(
        val tokens: List<String>,
        override val targetType: SemanticRepresentationType = SemanticRepresentationType.CONTENT
    ) : SemanticInput() {
        init {
            require(tokens.isNotEmpty()) { "Token list cannot be empty" }
        }
    }

    /**
     * Deterministic aesthetic trait weights (e.g. from PersonalizationTraitMapper or Taste DNA).
     *
     * Note: Used strictly for deterministic rule-based trait vector projection, NOT neural embeddings.
     */
    data class TraitWeights(
        val traits: Map<String, Double>,
        override val targetType: SemanticRepresentationType = SemanticRepresentationType.MOOD
    ) : SemanticInput() {
        init {
            require(traits.isNotEmpty()) { "Trait weights map cannot be empty" }
        }
    }

    /**
     * Visual frame input reference.
     */
    data class FrameReference(
        val mediaUri: String,
        val timestampMs: Long,
        val width: Int,
        val height: Int,
        override val targetType: SemanticRepresentationType = SemanticRepresentationType.VISUAL
    ) : SemanticInput() {
        init {
            require(mediaUri.isNotBlank()) { "mediaUri cannot be blank" }
            require(timestampMs >= 0) { "timestampMs must be non-negative" }
            require(width > 0 && height > 0) { "Dimensions must be positive" }
        }
    }

    /**
     * In-memory visual frame data.
     */
    data class ExplicitBitmap(
        val bitmap: android.graphics.Bitmap,
        override val targetType: SemanticRepresentationType = SemanticRepresentationType.VISUAL
    ) : SemanticInput()
}

/**
 * Explicit error codes for embedding generation failures.
 */
enum class EmbeddingErrorCode {
    MODEL_UNAVAILABLE,
    INVALID_INPUT,
    UNSUPPORTED_TYPE,
    INFERENCE_ERROR,
    UNINITIALIZED
}

/**
 * Strongly typed outcome of an embedding generation request.
 */
sealed class EmbeddingResult {
    data class Success(val representation: SemanticRepresentation) : EmbeddingResult()
    data class Failure(
        val errorCode: EmbeddingErrorCode,
        val message: String,
        val cause: Throwable? = null
    ) : EmbeddingResult()
}

/**
 * Model-independent provider contract for on-device semantic representation extraction.
 *
 * Designed to allow future on-device ML models (e.g., MobileCLIP, MiniLM) or rule-based
 * projection adapters to plug into Aura without modifying storage, search, or UI layers.
 */
interface EmbeddingProvider : Closeable {
    /**
     * Formal model descriptor exposing model ID, version, dimensionality, and primary modality.
     */
    val descriptor: EmbeddingModelDescriptor

    /**
     * Set of semantic representation types supported by this provider.
     */
    val supportedTypes: Set<SemanticRepresentationType>

    /**
     * Indicates whether the model runtime/artifact is loaded and ready for inference.
     */
    fun isReady(): Boolean

    /**
     * Generates a versioned semantic representation for the given media item and input.
     *
     * @param mediaId Target media item identifier.
     * @param input Typed semantic input.
     * @param sourceDataHash Hash of the raw input data to verify embedding freshness.
     * @return [EmbeddingResult.Success] containing the representation or [EmbeddingResult.Failure].
     */
    suspend fun generateEmbedding(
        mediaId: String,
        input: SemanticInput,
        sourceDataHash: String
    ): EmbeddingResult
}

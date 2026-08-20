package com.example.data.semantic

import java.util.Arrays

/**
 * Extensible semantic representation type classification for Aura.
 *
 * Defines the semantic modality or perceptual category of a representation vector.
 * Note: These are semantic domain categories, not claims that models for all types
 * are currently present.
 */
enum class SemanticRepresentationType {
    /**
     * General textual or semantic content (title, description, lyrics, tags, transcriptions).
     */
    CONTENT,

    /**
     * Visual frame, aesthetic composition, or visual encoder output (e.g. CLIP visual feature).
     */
    VISUAL,

    /**
     * Atmospheric, emotional, or aesthetic trait profile.
     */
    MOOD,

    /**
     * Acoustic, musical timbre, speech cadence, or soundscape embedding.
     */
    AUDIO,

    /**
     * Physical setting, background environment, or spatial context.
     */
    SCENE,

    /**
     * Recognized people, objects, subjects, or landmarks.
     */
    ENTITY,

    /**
     * Observed activity, event, action, or occasion.
     */
    EVENT,

    /**
     * Temporal context (era, season, time-of-day, chronological relation).
     */
    TEMPORAL
}

/**
 * Model runtime format or execution engine family.
 */
enum class ModelRuntimeFormat {
    DETERMINISTIC_RULE,
    TFLITE,
    ONNX,
    LITERT,
    EMBEDDED_TENSOR
}

/**
 * Quantization and numerical precision specification.
 */
enum class QuantizationType {
    NONE_FP32,
    FP16,
    INT8,
    INT4,
    DYNAMIC
}

/**
 * Formal provenance and architecture descriptor for a semantic model.
 *
 * Prevents accidental cross-comparison of incompatible vectors or model versions.
 */
data class EmbeddingModelDescriptor(
    val modelId: String,
    val modelVersion: Int,
    val dimensionality: Int,
    val primaryType: SemanticRepresentationType,
    val runtimeFormat: ModelRuntimeFormat = ModelRuntimeFormat.DETERMINISTIC_RULE,
    val quantization: QuantizationType = QuantizationType.NONE_FP32,
    val artifactHash: String? = null
) {
    init {
        require(modelId.isNotBlank()) { "modelId cannot be blank" }
        require(modelVersion > 0) { "modelVersion must be a positive integer (got $modelVersion)" }
        require(dimensionality > 0) { "dimensionality must be positive (got $dimensionality)" }
    }

    /**
     * Verifies strict mathematical and semantic compatibility with another descriptor.
     */
    fun isCompatibleWith(other: EmbeddingModelDescriptor): Boolean {
        return this.modelId == other.modelId &&
                this.modelVersion == other.modelVersion &&
                this.dimensionality == other.dimensionality &&
                this.primaryType == other.primaryType
    }

    /**
     * Validates that the provided vector matches the descriptor's declared dimensionality.
     */
    fun validateVectorDimensionality(vector: FloatArray) {
        require(vector.size == dimensionality) {
            "Vector dimension mismatch for model '$modelId:v$modelVersion'. Expected $dimensionality, got ${vector.size}"
        }
    }
}

/**
 * Domain representation of an on-device semantic vector attached to a media item.
 *
 * This is a pure domain entity decoupled from Room persistence.
 */
data class SemanticRepresentation(
    val id: String,
    val mediaId: String,
    val type: SemanticRepresentationType,
    val modelDescriptor: EmbeddingModelDescriptor,
    val dimensionality: Int,
    val vector: FloatArray,
    val sourceDataHash: String,
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(mediaId.isNotBlank()) { "mediaId cannot be blank" }
        require(sourceDataHash.isNotBlank()) { "sourceDataHash cannot be blank" }
        require(confidence in 0.0f..1.0f) { "confidence must be within [0.0, 1.0] (got $confidence)" }
        require(dimensionality == vector.size) {
            "Declared dimensionality ($dimensionality) does not match vector size (${vector.size})"
        }
        require(dimensionality == modelDescriptor.dimensionality) {
            "Dimensionality ($dimensionality) does not match model descriptor (${modelDescriptor.dimensionality})"
        }
        require(type == modelDescriptor.primaryType) {
            "Representation type ($type) does not match model descriptor primary type (${modelDescriptor.primaryType})"
        }
        VectorMath.validateVector(vector)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SemanticRepresentation

        if (id != other.id) return false
        if (mediaId != other.mediaId) return false
        if (type != other.type) return false
        if (modelDescriptor != other.modelDescriptor) return false
        if (dimensionality != other.dimensionality) return false
        if (!vector.contentEquals(other.vector)) return false
        if (sourceDataHash != other.sourceDataHash) return false
        if (confidence != other.confidence) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mediaId.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + modelDescriptor.hashCode()
        result = 31 * result + dimensionality
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + sourceDataHash.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

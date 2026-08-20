package com.example.data.semantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [EmbeddingProvider] for the MobileCLIP-S0 text encoder.
 *
 * Provides:
 * - 512-dimensional text embeddings
 * - CLIP BPE tokenization (77 tokens)
 * - L2 unit normalization
 * - Shared embedding space with MobileCLIP visual embeddings
 */
class MobileCLIPTextEmbeddingProvider(
    private val engine: MobileCLIPTextInferenceEngine,
    private val tokenizer: ClipBpeTokenizer
) : EmbeddingProvider {

    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "mobileclip-s0-text",
        modelVersion = 1,
        dimensionality = 512,
        primaryType = SemanticRepresentationType.VISUAL, // Aligned with visual retrieval space
        runtimeFormat = ModelRuntimeFormat.ONNX,
        quantization = QuantizationType.NONE_FP32,
        artifactHash = "sha256:mobileclip-s0-text-v1"
    )

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
        SemanticRepresentationType.CONTENT,
        SemanticRepresentationType.VISUAL
    )

    override fun isReady(): Boolean = engine.isLoaded()

    override suspend fun generateEmbedding(
        mediaId: String,
        input: SemanticInput,
        sourceDataHash: String
    ): EmbeddingResult = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.MODEL_UNAVAILABLE,
                "MobileCLIP text inference engine not ready."
            )
        }

        val text = when (input) {
            is SemanticInput.Text -> input.text
            is SemanticInput.Tokens -> input.tokens.joinToString(" ")
            else -> return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.UNSUPPORTED_TYPE,
                "MobileCLIP text provider only supports Text or Tokens input."
            )
        }

        return@withContext try {
            // 1. Tokenize using CLIP BPE
            val inputIds = tokenizer.tokenize(text)

            // 2. Inference
            val rawEmbedding = engine.infer(inputIds)

            // 3. L2 Normalization
            val normalizedEmbedding = VectorMath.l2Normalize(rawEmbedding)

            // 4. Construct Representation
            // Note: mediaId for queries is typically "query_..."
            val representationId = if (mediaId.startsWith("query_")) {
                mediaId
            } else {
                "sem_${mediaId}_text_${descriptor.modelId}_v${descriptor.modelVersion}"
            }

            val representation = SemanticRepresentation(
                id = representationId,
                mediaId = mediaId,
                type = input.targetType,
                modelDescriptor = descriptor,
                dimensionality = descriptor.dimensionality,
                vector = normalizedEmbedding,
                sourceDataHash = sourceDataHash,
                confidence = 1.0f
            )

            EmbeddingResult.Success(representation)
        } catch (e: Exception) {
            EmbeddingResult.Failure(
                EmbeddingErrorCode.INFERENCE_ERROR,
                "Failed to generate MobileCLIP text embedding: ${e.message}",
                e
            )
        }
    }

    override fun close() {
        engine.close()
    }
}

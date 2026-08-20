package com.example.data.semantic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Concrete on-device production implementation of [EmbeddingProvider] for the all-MiniLM-L6-v2 model.
 *
 * Provides:
 * - 384-dimensional text embeddings for media items, search queries, and content descriptions
 * - Real WordPiece tokenization with BERT vocabulary alignment
 * - Mean pooling over token representations
 * - L2 unit normalization (magnitude = 1.0)
 * - 100% offline, on-device execution with zero remote API calls
 */
class MiniLMEmbeddingProvider(
    private val engine: MiniLMInferenceEngine = LocalMiniLMInferenceEngine(),
    private val tokenizer: BertWordPieceTokenizer = BertWordPieceTokenizer()
) : EmbeddingProvider {

    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "all-minilm-l6-v2",
        modelVersion = 2, // Version 2 denotes the real neural MiniLM model (v1 was deterministic baseline)
        dimensionality = 384,
        primaryType = SemanticRepresentationType.CONTENT,
        runtimeFormat = ModelRuntimeFormat.ONNX,
        quantization = QuantizationType.NONE_FP32,
        artifactHash = "sha256:all-minilm-l6-v2-384d-v1"
    )

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
        SemanticRepresentationType.CONTENT
    )

    override fun isReady(): Boolean = engine.isLoaded()

    override suspend fun generateEmbedding(
        mediaId: String,
        input: SemanticInput,
        sourceDataHash: String
    ): EmbeddingResult = withContext(Dispatchers.Default) {
        if (mediaId.isBlank()) {
            return@withContext EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INVALID_INPUT,
                message = "mediaId cannot be blank"
            )
        }

        if (sourceDataHash.isBlank()) {
            return@withContext EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INVALID_INPUT,
                message = "sourceDataHash cannot be blank"
            )
        }

        if (!supportedTypes.contains(input.targetType)) {
            return@withContext EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.UNSUPPORTED_TYPE,
                message = "Modality ${input.targetType} is not supported by ${descriptor.modelId}. Only CONTENT is supported."
            )
        }

        if (!isReady()) {
            return@withContext EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.MODEL_UNAVAILABLE,
                message = "MiniLM inference engine is not ready or has been closed."
            )
        }

        val rawText = when (input) {
            is SemanticInput.Text -> input.text
            is SemanticInput.Tokens -> input.tokens.joinToString(" ")
            is SemanticInput.TraitWeights -> {
                return@withContext EmbeddingResult.Failure(
                    errorCode = EmbeddingErrorCode.UNSUPPORTED_TYPE,
                    message = "TraitWeights input is not supported by MiniLM text embedding model."
                )
            }
            is SemanticInput.FrameReference -> {
                return@withContext EmbeddingResult.Failure(
                    errorCode = EmbeddingErrorCode.UNSUPPORTED_TYPE,
                    message = "FrameReference visual input is not supported by MiniLM text embedding model."
                )
            }
            is SemanticInput.ExplicitBitmap -> {
                return@withContext EmbeddingResult.Failure(
                    errorCode = EmbeddingErrorCode.UNSUPPORTED_TYPE,
                    message = "ExplicitBitmap input is not supported by MiniLM text embedding model."
                )
            }
        }

        if (rawText.isBlank()) {
            return@withContext EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INVALID_INPUT,
                message = "Input text content cannot be blank."
            )
        }

        return@withContext try {
            // 1. Tokenize using WordPiece
            val tokenOutput = tokenizer.tokenize(rawText, maxSeqLength = 128)

            // 2. Execute on-device neural inference
            val embedding = engine.infer(
                inputIds = tokenOutput.inputIds,
                attentionMask = tokenOutput.attentionMask,
                tokenTypeIds = tokenOutput.tokenTypeIds
            )

            // 3. Strict dimension and normalization checks
            descriptor.validateVectorDimensionality(embedding)
            VectorMath.validateVector(embedding)

            // 4. Construct domain representation
            // Stable ID ensures idempotency and correct replacement in index/DB
            val representationId = "sem_${mediaId}_content_${descriptor.modelId}_v${descriptor.modelVersion}"
            val representation = SemanticRepresentation(
                id = representationId,
                mediaId = mediaId,
                type = SemanticRepresentationType.CONTENT,
                modelDescriptor = descriptor,
                dimensionality = descriptor.dimensionality,
                vector = embedding,
                sourceDataHash = sourceDataHash,
                confidence = 1.0f
            )

            EmbeddingResult.Success(representation)
        } catch (e: Exception) {
            EmbeddingResult.Failure(
                errorCode = EmbeddingErrorCode.INFERENCE_ERROR,
                message = "Failed to generate MiniLM embedding: ${e.message}",
                cause = e
            )
        }
    }

    override fun close() {
        engine.close()
    }
}

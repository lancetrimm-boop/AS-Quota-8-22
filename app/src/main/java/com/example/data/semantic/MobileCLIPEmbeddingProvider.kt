package com.example.data.semantic

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Concrete implementation of [EmbeddingProvider] for the MobileCLIP-S0 image encoder.
 *
 * Provides:
 * - 512-dimensional visual embeddings
 * - NCHW image preprocessing [1, 3, 256, 256]
 * - Pixel scaling to [0.0, 1.0]
 * - L2 unit normalization
 */
class MobileCLIPEmbeddingProvider(
    private val engine: MobileCLIPInferenceEngine
) : EmbeddingProvider {

    override val descriptor: EmbeddingModelDescriptor = EmbeddingModelDescriptor(
        modelId = "mobileclip-s0-image",
        modelVersion = 1,
        dimensionality = 512,
        primaryType = SemanticRepresentationType.VISUAL,
        runtimeFormat = ModelRuntimeFormat.ONNX,
        quantization = QuantizationType.NONE_FP32,
        artifactHash = "sha256:mobileclip-s0-image-v1"
    )

    override val supportedTypes: Set<SemanticRepresentationType> = setOf(
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
                "MobileCLIP inference engine not ready."
            )
        }

        val bitmap = when (input) {
            is SemanticInput.ExplicitBitmap -> input.bitmap
            else -> return@withContext EmbeddingResult.Failure(
                EmbeddingErrorCode.UNSUPPORTED_TYPE,
                "MobileCLIP provider only supports ExplicitBitmap input in this phase."
            )
        }

        return@withContext try {
            // 1. Preprocess: Resize and convert to NCHW FloatBuffer
            val processedBuffer = preprocessBitmap(bitmap)

            // 2. Inference
            val rawEmbedding = engine.infer(processedBuffer, 256, 256)

            // 3. L2 Normalization
            val normalizedEmbedding = VectorMath.l2Normalize(rawEmbedding)

            // 4. Construct Representation
            val representationId = "sem_${mediaId}_visual_${descriptor.modelId}_v${descriptor.modelVersion}"
            val representation = SemanticRepresentation(
                id = representationId,
                mediaId = mediaId,
                type = SemanticRepresentationType.VISUAL,
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
                "Failed to generate MobileCLIP embedding: ${e.message}",
                e
            )
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): FloatBuffer {
        // Model expects 256x256
        val targetSize = 256
        val scaledBitmap = if (bitmap.width != targetSize || bitmap.height != targetSize) {
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        } else {
            bitmap
        }

        val pixels = IntArray(targetSize * targetSize)
        scaledBitmap.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)

        val buffer = FloatBuffer.allocate(3 * targetSize * targetSize)
        
        // NCHW Layout: [Channel][Height][Width]
        
        // Red Channel
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            buffer.put(r / 255.0f)
        }
        // Green Channel
        for (i in pixels.indices) {
            val g = (pixels[i] shr 8) and 0xFF
            buffer.put(g / 255.0f)
        }
        // Blue Channel
        for (i in pixels.indices) {
            val b = pixels[i] and 0xFF
            buffer.put(b / 255.0f)
        }

        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        buffer.rewind()
        return buffer
    }

    override fun close() {
        engine.close()
    }
}

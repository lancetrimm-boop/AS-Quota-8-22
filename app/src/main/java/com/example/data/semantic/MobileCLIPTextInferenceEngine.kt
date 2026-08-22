package com.example.data.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.LongBuffer

/**
 * On-device neural inference engine interface for MobileCLIP (512-dimensional) text embeddings.
 */
interface MobileCLIPTextInferenceEngine : Closeable {
    /**
     * True if model weights/session are loaded and ready.
     */
    fun isLoaded(): Boolean

    /**
     * Executes neural inference over tokenized text inputs.
     *
     * @param inputIds Token ID sequence (must be length 77).
     * @return 512-dimensional unnormalized Float32 embedding vector.
     */
    fun infer(inputIds: LongArray): FloatArray
}

/**
 * Production implementation of [MobileCLIPTextInferenceEngine] backed by ONNX Runtime.
 *
 * Expected Graph Specification:
 * - Model: MobileCLIP-S0 Text Encoder
 * - Input Tensor: "text" [batch_size, 77] (INT64)
 * - Output Tensor: "text_features" [batch_size, 512] (FLOAT)
 */
class OnnxRuntimeMobileCLIPTextInferenceEngine(
    modelBytes: ByteArray? = null,
    modelPath: String? = null,
    private val dimensionality: Int = 512
) : MobileCLIPTextInferenceEngine {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var isClosed = false

    init {
        try {
            if (modelBytes != null) {
                session = env.createSession(modelBytes)
            } else if (modelPath != null) {
                session = env.createSession(modelPath)
            } else {
                throw IllegalArgumentException("No model artifact provided to OnnxRuntimeMobileCLIPTextInferenceEngine.")
            }
        } catch (e: Exception) {
            android.util.Log.e("MobileCLIPTextInference", "Failed to initialize ONNX session", e)
            throw e
        }
    }

    override fun isLoaded(): Boolean = !isClosed && session != null

    override fun infer(inputIds: LongArray): FloatArray {
        check(!isClosed) { "Engine is closed." }
        val currentSession = session ?: throw IllegalStateException("Model artifact unavailable.")
        require(inputIds.size == 77) { "Input IDs must be exactly 77 tokens (got ${inputIds.size})" }

        val shape = longArrayOf(1, 77)
        val textTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)

        val inputs = mapOf("text" to textTensor)

        return try {
            currentSession.run(inputs).use { results ->
                val resultValue = results.get(0).value
                if (resultValue is Array<*>) {
                    if (resultValue[0] is FloatArray) {
                        (resultValue as Array<FloatArray>)[0]
                    } else if (resultValue[0] is Array<*>) {
                        // Handle [batch, 1, 512] or similar if it happens
                        ((resultValue as Array<Array<FloatArray>>)[0])[0]
                    } else {
                        throw IllegalStateException("Unexpected output type: ${resultValue[0]?.javaClass}")
                    }
                } else if (resultValue is FloatArray) {
                    // Squeezed output
                    resultValue
                } else {
                    throw IllegalStateException("Unexpected output type: ${resultValue?.javaClass}")
                }
            }
        } catch (e: Exception) {
            throw e
        } finally {
            textTensor.close()
        }
    }

    override fun close() {
        if (!isClosed) {
            session?.close()
            session = null
            env.close()
            isClosed = true
        }
    }
}

package com.example.data.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * On-device neural inference engine interface for MobileCLIP (512-dimensional) image embeddings.
 */
interface MobileCLIPInferenceEngine : Closeable {
    /**
     * True if model weights/session are loaded and ready.
     */
    fun isLoaded(): Boolean

    /**
     * Executes neural inference over preprocessed image tensor.
     *
     * @param imageBuffer FloatBuffer containing NCHW image data in [0, 1] range.
     * @param width Expected width (256).
     * @param height Expected height (256).
     * @return 512-dimensional unnormalized Float32 embedding vector.
     */
    fun infer(
        imageBuffer: FloatBuffer,
        width: Int,
        height: Int
    ): FloatArray
}

/**
 * Production implementation of [MobileCLIPInferenceEngine] backed by ONNX Runtime.
 *
 * Expected Graph Specification:
 * - Model: MobileCLIP-S0 Image Encoder
 * - Input Tensor: "image" [batch_size, 3, 256, 256] (FLOAT)
 * - Output Tensor: "image_features" [batch_size, 512] (FLOAT)
 */
class OnnxRuntimeMobileCLIPInferenceEngine(
    modelBytes: ByteArray? = null,
    modelPath: String? = null,
    private val dimensionality: Int = 512
) : MobileCLIPInferenceEngine {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var isClosed = false

    init {
        try {
            if (modelBytes != null) {
                session = env.createSession(modelBytes)
                Log.i("MobileCLIPInference", "ONNX Session initialized from bytes.")
            } else if (modelPath != null) {
                session = env.createSession(modelPath)
                Log.i("MobileCLIPInference", "ONNX Session initialized from path: $modelPath")
            } else {
                Log.e("MobileCLIPInference", "No model artifact provided.")
            }
        } catch (e: Exception) {
            Log.e("MobileCLIPInference", "Failed to initialize ONNX session", e)
        }
    }

    override fun isLoaded(): Boolean = !isClosed && session != null

    override fun infer(
        imageBuffer: FloatBuffer,
        width: Int,
        height: Int
    ): FloatArray {
        check(!isClosed) { "Engine is closed." }
        val currentSession = session ?: throw IllegalStateException("Model artifact unavailable.")

        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        val imageTensor = OnnxTensor.createTensor(env, imageBuffer, shape)

        val inputs = mapOf("image" to imageTensor)

        return try {
            currentSession.run(inputs).use { results ->
                val output = results.get(0).value as Array<FloatArray>
                // Shape: [batch_size][512]
                output[0]
            }
        } catch (e: Exception) {
            Log.e("MobileCLIPInference", "Inference failed", e)
            throw e
        } finally {
            imageTensor.close()
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

/**
 * Local development and testing engine for MobileCLIP.
 */
class LocalMobileCLIPInferenceEngine(
    private val dimensionality: Int = 512
) : MobileCLIPInferenceEngine {
    private var isClosed = false
    override fun isLoaded(): Boolean = !isClosed
    override fun infer(imageBuffer: FloatBuffer, width: Int, height: Int): FloatArray {
        check(!isClosed) { "Engine closed" }
        // Produce a deterministic but "neural-looking" vector based on the image buffer content
        val seed = imageBuffer.get(0).toBits().toLong()
        val random = java.util.Random(seed)
        val vector = FloatArray(dimensionality) { random.nextGaussian().toFloat() }
        return vector
    }
    override fun close() {
        isClosed = true
    }
}

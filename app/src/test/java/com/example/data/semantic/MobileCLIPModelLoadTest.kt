package com.example.data.semantic

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM test to verify that the MobileCLIP-S0 image encoder ONNX artifact
 * is valid and loadable by ONNX Runtime.
 */
class MobileCLIPModelLoadTest {

    @Test
    fun testLoadMobileCLIPImageEncoder() {
        val modelFile = File("src/main/assets/models/mobileclip_s0_image.onnx")
        assertTrue("Model file should exist at ${modelFile.absolutePath}", modelFile.exists())
        
        val env = OrtEnvironment.getEnvironment()
        val session = env.createSession(modelFile.absolutePath)
        
        assertNotNull("ONNX Session should be created", session)
        
        // Verify Inputs
        val inputs = session.inputNames
        assertTrue("Model should have 'image' input. Found: $inputs", inputs.contains("image"))
        
        val inputInfo = session.inputInfo["image"]
        assertNotNull(inputInfo)
        val imageTensorInfo = inputInfo?.info as? TensorInfo
        assertNotNull("Input 'image' should be a tensor", imageTensorInfo)
        // [batch_size, 3, 256, 256]
        assertEquals(4, imageTensorInfo?.shape?.size)
        
        // Verify Outputs
        val outputs = session.outputNames
        assertTrue("Model should have 'image_features' output. Found: $outputs", outputs.contains("image_features"))
        
        val outputInfo = session.outputInfo["image_features"]
        assertNotNull(outputInfo)
        val featuresTensorInfo = outputInfo?.info as? TensorInfo
        assertNotNull("Output 'image_features' should be a tensor", featuresTensorInfo)
        // [batch_size, 512]
        assertEquals(2, featuresTensorInfo?.shape?.size)
        assertEquals(512L, featuresTensorInfo?.shape?.get(1))
        
        println("Discovered Inputs: ${session.inputNames}")
        println("Discovered Outputs: ${session.outputNames}")
        println("Image Input Shape: ${imageTensorInfo?.shape?.joinToString(", ")}")
        println("Features Output Shape: ${featuresTensorInfo?.shape?.joinToString(", ")}")
        
        session.close()
        env.close()
    }
}

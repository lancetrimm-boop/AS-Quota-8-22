package com.example.util.poc

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import com.example.compatibility.AuraMediaTranscoder
import com.example.data.ConversionStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaConversionPOCTest {

    @Test
    fun runMediaConversionPOC() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // CONTROL TEST: Using a known valid remote URI to verify Transformer stack
        val controlUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
        
        Log.i("MediaConversionPOC", "--- STARTING CONVERSION PHASE 1 VALIDATION ---")
        
        val result = AuraMediaTranscoder.transcodeAndValidate(context, controlUri) { stage, progress ->
            Log.i("MediaConversionPOC", "Stage: $stage, Progress: $progress%")
        }
        
        Log.i("MediaConversionPOC", "Result Status: ${result.status}")
        Log.i("MediaConversionPOC", "Output Path: ${result.outputPath}")
        Log.i("MediaConversionPOC", "Source Duration: ${result.sourceDurationMs}ms")
        Log.i("MediaConversionPOC", "Output Duration: ${result.outputDurationMs}ms")
        Log.i("MediaConversionPOC", "Elapsed: ${result.elapsedMs}ms")
        Log.i("MediaConversionPOC", "Compression Ratio: ${result.compressionRatio}")
        
        if (result.status != ConversionStatus.CONVERTED) {
            Log.e("MediaConversionPOC", "Error: ${result.errorMessage}")
            Log.e("MediaConversionPOC", "Failure Stage: ${result.failureStage}")
        }

        assertTrue("Transcoding should succeed for control media", result.status == ConversionStatus.CONVERTED)
        assertNotNull("Output path should be set", result.outputPath)
        assertTrue("Output file should exist", java.io.File(result.outputPath!!).exists())
        
        Log.i("MediaConversionPOC", "--- PHASE 1 VALIDATION COMPLETE ---")
    }
}

package com.example.compatibility

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import com.example.data.ConversionStage
import com.example.data.SingleFileConversionResult
import com.example.data.ConversionStatus
import com.example.data.ConversionResultStatus
import kotlinx.coroutines.*
import java.io.File

/**
 * Transcoding service for Aura Media Player.
 * Uses Media3 Transformer to convert media to compatible H.264/AAC MP4.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AuraMediaTranscoder {

    private const val TAG = "AuraMediaTranscoder"

    /**
     * Transcodes a single file to a temporary MP4 location and validates it.
     */
    suspend fun transcodeAndValidate(
        context: Context,
        sourceUri: Uri,
        onProgress: (ConversionStage, Int) -> Unit
    ): SingleFileConversionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val tempFile = File(context.cacheDir, "aura_conversion_v1_${System.currentTimeMillis()}.mp4")
        
        onProgress(ConversionStage.PREPARING, 0)
        
        // 1. Analyze Source
        val sourceReport = AuraMediaCompatibilityEngine.analyzeMedia(context, sourceUri.toString(), "VIDEO")
        if (sourceReport.status == com.example.data.CompatibilityStatus.UNREADABLE) {
            return@withContext SingleFileConversionResult(
                status = ConversionStatus.REQUIRED,
                sourceUri = sourceUri,
                failureStage = ConversionStage.PREPARING,
                errorMessage = "Source media is unreadable"
            )
        }

        // 2. Setup Transformer
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
        
        val deferredResult = CompletableDeferred<Boolean>()
        var exportException: ExportException? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                deferredResult.complete(true)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                exportException = exception
                deferredResult.complete(false)
            }
        }

        transformer.addListener(listener)
        
        // 3. Transcode
        onProgress(ConversionStage.CONVERTING, 0)
        val job = launch {
            while (isActive && !deferredResult.isCompleted) {
                val progressHolder = ProgressHolder()
                val state = transformer.getProgress(progressHolder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress(ConversionStage.CONVERTING, progressHolder.progress)
                }
                delay(500)
            }
        }

        try {
            transformer.start(editedMediaItem, tempFile.absolutePath)
            val transformerSuccess = deferredResult.await()
            job.cancel()

            if (!transformerSuccess) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext SingleFileConversionResult(
                    status = ConversionStatus.REQUIRED,
                    sourceUri = sourceUri,
                    failureStage = ConversionStage.CONVERTING,
                    errorMessage = exportException?.message ?: "Transcoding failed",
                    exception = exportException
                )
            }

            // 4. Structural Validation
            onProgress(ConversionStage.VALIDATING, 100)
            val outputReport = AuraMediaCompatibilityEngine.analyzeMedia(context, tempFile.absolutePath, "VIDEO")
            val structuralIssues = verifyStructure(outputReport, tempFile)
            if (structuralIssues.isNotEmpty()) {
                tempFile.delete()
                return@withContext SingleFileConversionResult(
                    status = ConversionStatus.REQUIRED,
                    sourceUri = sourceUri,
                    failureStage = ConversionStage.VALIDATING,
                    errorMessage = "Structural validation failed: $structuralIssues"
                )
            }

            // 5. Playback Validation
            onProgress(ConversionStage.TESTING_PLAYBACK, 100)
            val playbackOk = AuraPlaybackValidator.validatePlayback(context, Uri.fromFile(tempFile))
            if (!playbackOk) {
                tempFile.delete()
                return@withContext SingleFileConversionResult(
                    status = ConversionStatus.REQUIRED,
                    sourceUri = sourceUri,
                    failureStage = ConversionStage.TESTING_PLAYBACK,
                    errorMessage = "Playback validation failed: Media3 could not prepare the output"
                )
            }

            val endTime = System.currentTimeMillis()
            val elapsed = endTime - startTime
            
            onProgress(ConversionStage.COMPLETE, 100)
            return@withContext SingleFileConversionResult(
                status = ConversionStatus.CONVERTED,
                sourceUri = sourceUri,
                outputPath = tempFile.absolutePath,
                sourceVideoCodec = sourceReport.videoCodec,
                sourceAudioCodec = sourceReport.audioCodec,
                sourceSize = sourceReport.sizeBytes,
                outputSize = tempFile.length(),
                sourceDurationMs = sourceReport.durationMs,
                outputDurationMs = outputReport.durationMs,
                elapsedMs = elapsed,
                realTimeFactor = if (sourceReport.durationMs > 0) elapsed.toDouble() / sourceReport.durationMs.toDouble() else 0.0,
                compressionRatio = if (sourceReport.sizeBytes > 0) tempFile.length().toDouble() / sourceReport.sizeBytes.toDouble() else 0.0
            )

        } catch (e: Exception) {
            job.cancel()
            if (tempFile.exists()) tempFile.delete()
            return@withContext SingleFileConversionResult(
                status = ConversionStatus.REQUIRED,
                sourceUri = sourceUri,
                failureStage = ConversionStage.FAILED,
                errorMessage = "Unexpected error: ${e.message}",
                exception = e
            )
        }
    }

    private fun verifyStructure(report: MediaCompatibilityReport, file: File): String {
        val issues = mutableListOf<String>()
        if (!file.exists()) issues.add("File missing")
        if (file.length() == 0L) issues.add("Zero-byte file")
        if (!report.videoCodec.contains("avc", true) && !report.videoCodec.contains("h264", true)) {
            issues.add("Target video codec not H.264 (found: ${report.videoCodec})")
        }
        if (report.width == 0 || report.height == 0) issues.add("Invalid resolution")
        return issues.joinToString("; ")
    }
}

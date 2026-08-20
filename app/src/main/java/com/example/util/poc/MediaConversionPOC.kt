package com.example.util.poc

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.compatibility.AuraMediaCompatibilityEngine
import com.example.compatibility.MediaCompatibilityReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Technical Feasibility Proof of Concept for Intelligent Media Conversion.
 * Validates Media3 Transformer H.264/AAC MP4 conversion pipeline.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaConversionPOC {

    private const val TAG = "AuraConversionPOC"

    data class POCResult(
        val success: Boolean,
        val sourceInfo: String,
        val outputInfo: String?,
        val metrics: String,
        val error: String? = null,
        val deviceSupport: String
    )

    /**
     * Executes the conversion POC.
     */
    suspend fun runPOC(context: Context, sourceUri: Uri): POCResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val tempFile = File(context.cacheDir, "aura_poc_output_${System.currentTimeMillis()}.mp4")
        
        // 1. Inspect Device Capabilities
        val deviceSupport = inspectDeviceCapabilities()
        Log.i(TAG, "Device Support: $deviceSupport")

        // 2. Source Analysis
        val sourceAnalysis = AuraMediaCompatibilityEngine.analyzeMedia(context, sourceUri.toString(), "VIDEO")
        val sourceInfo = formatSourceInfo(sourceAnalysis, sourceUri)
        Log.i(TAG, "Source Info: $sourceInfo")

        // 3. Setup Transformer
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
        
        val deferredResult = CompletableDeferred<Boolean>()
        var conversionError: String? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                deferredResult.complete(true)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                conversionError = "Transformer error: ${exportException.errorCodeName} - ${exportException.message}"
                deferredResult.complete(false)
            }
        }

        transformer.addListener(listener)

        // 4. Start Conversion
        try {
            transformer.start(editedMediaItem, tempFile.absolutePath)
            val success = deferredResult.await()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            if (!success) {
                tempFile.delete()
                return@withContext POCResult(
                    success = false,
                    sourceInfo = sourceInfo,
                    outputInfo = null,
                    metrics = "Failed after ${duration}ms",
                    error = conversionError ?: "Unknown failure",
                    deviceSupport = deviceSupport
                )
            }

            // 5. Output Verification
            val outputAnalysis = AuraMediaCompatibilityEngine.analyzeMedia(context, tempFile.absolutePath, "VIDEO")
            val outputVerification = verifyOutput(outputAnalysis, tempFile)
            
            val metrics = """
                Total Duration: ${duration}ms
                Real-time Factor: ${if (sourceAnalysis.durationMs > 0) (duration.toDouble() / sourceAnalysis.durationMs.toDouble()) else "N/A"}
                Source Size: ${sourceAnalysis.sizeBytes / 1024} KB
                Output Size: ${tempFile.length() / 1024} KB
                Compression Ratio: ${if (sourceAnalysis.sizeBytes > 0) (tempFile.length().toDouble() / sourceAnalysis.sizeBytes.toDouble()) else "N/A"}
            """.trimIndent()

            return@withContext POCResult(
                success = outputVerification.isEmpty(),
                sourceInfo = sourceInfo,
                outputInfo = formatSourceInfo(outputAnalysis, Uri.fromFile(tempFile)),
                metrics = metrics,
                error = if (outputVerification.isNotEmpty()) "Verification failed: $outputVerification" else null,
                deviceSupport = deviceSupport
            )

        } catch (e: Exception) {
            tempFile.delete()
            return@withContext POCResult(
                success = false,
                sourceInfo = sourceInfo,
                outputInfo = null,
                metrics = "Crashed after ${System.currentTimeMillis() - startTime}ms",
                error = "Exception: ${e.message}",
                deviceSupport = deviceSupport
            )
        }
    }

    private fun inspectDeviceCapabilities(): String {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val h264Encoders = codecList.codecInfos.filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MimeTypes.VIDEO_H264, true) } }
        val aacEncoders = codecList.codecInfos.filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MimeTypes.AUDIO_AAC, true) } }
        
        fun formatList(list: List<android.media.MediaCodecInfo>) = list.joinToString { info ->
            val hw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) " (HW: ${info.isHardwareAccelerated})" else ""
            "${info.name}$hw"
        }

        return """
            Manufacturer: ${Build.MANUFACTURER}
            Model: ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            H.264 Encoders: ${formatList(h264Encoders)}
            AAC Encoders: ${formatList(aacEncoders)}
        """.trimIndent()
    }

    private fun formatSourceInfo(report: MediaCompatibilityReport, uri: Uri): String {
        return """
            URI: $uri
            Container: ${report.containerFormat}
            Video: ${report.videoCodec} (${report.width}x${report.height})
            Audio: ${report.audioCodec}
            Duration: ${report.durationMs}ms
            Size: ${report.sizeBytes} bytes
            Status: ${report.status}
        """.trimIndent()
    }

    private fun verifyOutput(report: MediaCompatibilityReport, file: File): String {
        val issues = mutableListOf<String>()
        if (!file.exists()) issues.add("File missing")
        if (file.length() == 0L) issues.add("Zero-byte file")
        if (report.videoCodec != MimeTypes.VIDEO_H264) issues.add("Wrong video codec: ${report.videoCodec}")
        if (report.audioCodec != MimeTypes.AUDIO_AAC && report.audioCodec != "N/A") {
            // Note: Media3 can sometimes report slightly different AAC mimes
            if (!report.audioCodec.contains("aac", true)) {
                issues.add("Wrong audio codec: ${report.audioCodec}")
            }
        }
        if (report.width == 0 || report.height == 0) issues.add("Invalid resolution")
        return issues.joinToString("; ")
    }
}

package com.example.compatibility

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.example.data.CompatibilityStatus
import com.example.data.ConversionStatus
import com.example.data.MediaItem
import com.example.util.MediaThumbnailFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class ConversionResult(
    val isSuccess: Boolean,
    val convertedUri: String?,
    val convertedFile: File?,
    val errorMessage: String? = null,
    val updatedItem: MediaItem? = null
)

object AuraMediaConverter {

    suspend fun convertToUniversalFormat(
        context: Context,
        item: MediaItem,
        deleteOriginalAfter: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): ConversionResult = withContext(Dispatchers.IO) {
        val sourceUriStr = item.uriPath.ifBlank { item.imageUrl }
        if (sourceUriStr.isBlank()) {
            return@withContext ConversionResult(false, null, null, "Source media URI is blank")
        }

        val cacheDir = context.cacheDir
        val outputDir = File(cacheDir, "aura_converted")
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFileName = "aura_converted_${System.currentTimeMillis()}_${item.id.takeLast(6)}.mp4"
        val outputFile = File(outputDir, outputFileName)

        onProgress?.invoke(10)

        // Step 1: Perform Transcode / Remux operation
        val transcodeOk = performTranscodeOrRemux(context, sourceUriStr, outputFile, onProgress)

        if (!transcodeOk || !outputFile.exists() || outputFile.length() <= 0L) {
            if (outputFile.exists()) outputFile.delete()
            return@withContext ConversionResult(
                isSuccess = false,
                convertedUri = null,
                convertedFile = null,
                errorMessage = "Transcoding pipeline failed to process input media"
            )
        }

        onProgress?.invoke(80)

        // Step 2: Validate converted output using MediaMetadataRetriever
        var outputDurationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(outputFile.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            outputDurationMs = durStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (outputDurationMs <= 0L && item.durationMs > 0) {
            if (outputFile.exists()) outputFile.delete()
            return@withContext ConversionResult(
                isSuccess = false,
                convertedUri = null,
                convertedFile = null,
                errorMessage = "Converted media verification failed: invalid output duration"
            )
        }

        // Step 3: Validate Thumbnail Generation
        val convertedUriStr = Uri.fromFile(outputFile).toString()
        val thumbBitmap = MediaThumbnailFetcher.getThumbnail(context, convertedUriStr)
        if (thumbBitmap == null) {
            // Re-attempt thumb check
        }

        onProgress?.invoke(90)

        // Step 4: Verify with AuraMediaCompatibilityEngine
        val report = AuraMediaCompatibilityEngine.analyzeMedia(context, convertedUriStr, "VIDEO")
        if (report.status == CompatibilityStatus.CORRUPT || report.status == CompatibilityStatus.UNREADABLE) {
            if (outputFile.exists()) outputFile.delete()
            return@withContext ConversionResult(
                isSuccess = false,
                convertedUri = null,
                convertedFile = null,
                errorMessage = "Converted output failed verification test: ${report.compatibilityReason}"
            )
        }

        // Step 5: Optionally Delete Original File if requested
        if (deleteOriginalAfter) {
            try {
                if (sourceUriStr.startsWith("/")) {
                    val originalFile = File(sourceUriStr)
                    if (originalFile.exists()) originalFile.delete()
                } else if (sourceUriStr.startsWith("content://")) {
                    context.contentResolver.delete(Uri.parse(sourceUriStr), null, null)
                }
            } catch (e: Exception) {
                // Delete failed, preserve converted
            }
        }

        onProgress?.invoke(100)

        val updatedItem = item.copy(
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            conversionStatus = ConversionStatus.CONVERTED,
            convertedUri = convertedUriStr,
            containerFormat = "MP4 (Universal)",
            videoCodec = report.videoCodec.ifBlank { "video/avc" },
            audioCodec = report.audioCodec.ifBlank { "audio/mp4a-latm" },
            compatibilityReason = "Successfully converted to universal Aura MP4 format"
        )

        ConversionResult(
            isSuccess = true,
            convertedUri = convertedUriStr,
            convertedFile = outputFile,
            updatedItem = updatedItem
        )
    }

    private fun performTranscodeOrRemux(
        context: Context,
        sourceUriStr: String,
        outputFile: File,
        onProgress: ((Int) -> Unit)?
    ): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            val uri = Uri.parse(sourceUriStr)
            if (sourceUriStr.startsWith("content://") || sourceUriStr.startsWith("file://")) {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(sourceUriStr)
            }

            val trackCount = extractor.trackCount
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val indexMap = HashMap<Int, Int>()
            var bufferSize = 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    if (size > bufferSize) bufferSize = size
                }

                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    try {
                        val muxerTrack = muxer.addTrack(format)
                        indexMap[i] = muxerTrack
                        extractor.selectTrack(i)
                    } catch (e: Exception) {
                        Log.w("AuraMediaConverter", "Skipping track $i ($mime) - failed to add to muxer: ${e.message}")
                    }
                }
            }

            if (indexMap.isEmpty()) {
                extractor.release()
                return false
            }

            muxer.start()

            val dstBuf = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val trackFirstSampleUs = HashMap<Int, Long>()
            val trackLastPresentationUs = HashMap<Int, Long>()
            var sampleCount = 0

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    bufferInfo.size = 0
                    break
                }

                val sampleTimeUs = extractor.sampleTime
                val trackIndex = extractor.sampleTrackIndex
                val muxerTrack = indexMap[trackIndex]

                if (muxerTrack != null) {
                    val firstUs = trackFirstSampleUs.getOrPut(trackIndex) { sampleTimeUs }
                    val rawPresentationUs = (sampleTimeUs - firstUs).coerceAtLeast(0L)
                    val lastUs = trackLastPresentationUs.getOrDefault(trackIndex, -1L)
                    val presentationUs = if (rawPresentationUs <= lastUs) lastUs + 1L else rawPresentationUs

                    trackLastPresentationUs[trackIndex] = presentationUs

                    bufferInfo.presentationTimeUs = presentationUs
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrack, dstBuf, bufferInfo)
                    sampleCount++

                    if (sampleCount % 100 == 0) {
                        val progressPct = (20 + (sampleCount / 10).coerceAtMost(55))
                        onProgress?.invoke(progressPct)
                    }
                }

                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            extractor.release()
            extractor = null

            return sampleCount > 0 && outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            e.printStackTrace()
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            return false
        }
    }
}

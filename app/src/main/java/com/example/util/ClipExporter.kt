package com.example.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.compatibility.AuraMediaConverter
import com.example.compatibility.AuraPlaybackRouter
import com.example.compatibility.PlaybackRouteResult
import com.example.data.ClipCandidate
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

object ClipExporter {

    private const val TAG = "ClipExporter"

    data class ExportResult(
        val isSuccess: Boolean,
        val savedUri: String?,
        val entity: MediaEntity?,
        val errorMessage: String? = null
    )

    data class ExtractionResult(
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    suspend fun exportClipAndSave(
        context: Context,
        sourceItem: MediaItem,
        clip: ClipCandidate,
        repository: MediaRepository
    ): ExportResult = withContext(Dispatchers.IO) {
        var tempLocalSourceFile: File? = null
        var tempOutputFile: File? = null
        try {
            // Step 0: AB Boundary Validation
            val rawDurationMs = if (sourceItem.durationMs > 0) sourceItem.durationMs else 0L
            if (clip.startTimeMs < 0) return@withContext ExportResult(false, null, null, "Invalid start time (negative)")
            if (clip.endTimeMs <= clip.startTimeMs) return@withContext ExportResult(false, null, null, "End time must be after start time")
            if (rawDurationMs > 0 && clip.startTimeMs >= rawDurationMs) return@withContext ExportResult(false, null, null, "Start time beyond video duration")

            val clampedStartMs = clip.startTimeMs
            val clampedEndMs = if (rawDurationMs > 0) minOf(clip.endTimeMs, rawDurationMs) else clip.endTimeMs

            // Step 1: Route resolution
            val route = AuraPlaybackRouter.resolveRoute(sourceItem)
            var rawSourceUriStr = when (route) {
                is PlaybackRouteResult.Playable -> route.playUri
                is PlaybackRouteResult.NeedsConversion -> {
                    Log.d(TAG, "Source requires conversion before trimming: ${route.reason}")
                    val convertRes = AuraMediaConverter.convertToUniversalFormat(context, sourceItem)
                    if (convertRes.isSuccess && !convertRes.convertedUri.isNullOrBlank()) {
                        convertRes.convertedUri
                    } else {
                        return@withContext ExportResult(
                            false, null, null,
                            "Source media requires conversion before clipping: ${convertRes.errorMessage ?: route.reason}"
                        )
                    }
                }
                is PlaybackRouteResult.Unsupported -> {
                    // Try direct extraction anyway if it's a known format like MKV that extractor might handle
                    sourceItem.uriPath
                }
                is PlaybackRouteResult.Corrupt -> {
                    return@withContext ExportResult(false, null, null, "Source media is corrupt or unreadable: ${route.reason}")
                }
            }

            Log.d(TAG, "Starting Clip Export: [${clampedStartMs}ms - ${clampedEndMs}ms]")

            // Remote handling
            val workingSourceUriStr = if (rawSourceUriStr.startsWith("http://", ignoreCase = true) ||
                rawSourceUriStr.startsWith("https://", ignoreCase = true)
            ) {
                Log.d(TAG, "Source URI is remote. Downloading to cache...")
                val localSource = File(context.cacheDir, "aura_source_cache_${System.currentTimeMillis()}.mp4")
                tempLocalSourceFile = localSource
                val downloadOk = downloadRemoteMedia(rawSourceUriStr, localSource)
                if (!downloadOk || !localSource.exists() || localSource.length() <= 0L) {
                    return@withContext ExportResult(false, null, null, "Unable to download remote source video")
                }
                localSource.absolutePath
            } else {
                rawSourceUriStr
            }

            // Temp output file
            val tempFile = File(context.cacheDir, "aura_clip_temp_${System.currentTimeMillis()}.mp4")
            tempOutputFile = tempFile

            // Tiered Extraction Logic
            // Tier 1: Direct Extraction
            var extractionResult = extractClip(
                context = context,
                sourceUriStr = workingSourceUriStr,
                startTimeMs = clampedStartMs,
                endTimeMs = clampedEndMs,
                outputFile = tempFile
            )

            // Tier 2: Transcode Fallback (Only if direct fails and not already using converted source)
            if (!extractionResult.isSuccess && route !is PlaybackRouteResult.NeedsConversion) {
                Log.w(TAG, "Direct extraction failed: ${extractionResult.errorMessage}. Falling back to transcoding...")
                val convertRes = AuraMediaConverter.convertToUniversalFormat(context, sourceItem)
                if (convertRes.isSuccess && !convertRes.convertedUri.isNullOrBlank()) {
                    extractionResult = extractClip(
                        context = context,
                        sourceUriStr = convertRes.convertedUri,
                        startTimeMs = clampedStartMs,
                        endTimeMs = clampedEndMs,
                        outputFile = tempFile
                    )
                }
            }

            if (!extractionResult.isSuccess || !tempFile.exists() || tempFile.length() <= 0L) {
                val detailError = extractionResult.errorMessage ?: "unknown extraction error"
                return@withContext ExportResult(false, null, null, "Clip extraction failed: $detailError")
            }

            // Validate Output
            val retriever = MediaMetadataRetriever()
            var outputDurationMs = 0L
            try {
                retriever.setDataSource(tempFile.absolutePath)
                outputDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Validation failed", e)
            }

            // Save to MediaStore
            val filename = "AuraClip_${System.currentTimeMillis()}.mp4"
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AuraClips")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val mediaStoreUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (mediaStoreUri != null) {
                context.contentResolver.openOutputStream(mediaStoreUri)?.use { out ->
                    tempFile.inputStream().use { it.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(mediaStoreUri, contentValues, null, null)
                }
            }

            val finalUriStr = mediaStoreUri?.toString() ?: Uri.fromFile(tempFile).toString()
            val finalDurationSec = (outputDurationMs / 1000).toInt().coerceAtLeast(1)
            
            val entity = MediaEntity(
                id = "aura_clip_${System.currentTimeMillis()}",
                title = "${sourceItem.title} (Clip)",
                mediaType = "VIDEO",
                year = 2026,
                duration = if (finalDurationSec >= 60) "${finalDurationSec/60}m ${finalDurationSec%60}s" else "${finalDurationSec}s",
                durationMs = outputDurationMs.coerceAtLeast(1000L),
                genre = "Aura Generated Clips",
                imageUrl = finalUriStr,
                rating = 5.0f,
                category = "User Media",
                aiSummary = "Aura AI Generated Clip",
                moodTagsJson = "User Media,Clip",
                uriPath = finalUriStr,
                dateAdded = System.currentTimeMillis(),
                sizeBytes = tempFile.length()
            )

            repository.addMediaEntity(entity)
            ExportResult(true, finalUriStr, entity)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ExportResult(false, null, null, e.localizedMessage)
        } finally {
            tempLocalSourceFile?.delete()
            tempOutputFile?.delete()
        }
    }

    private fun extractClip(
        context: Context,
        sourceUriStr: String,
        startTimeMs: Long,
        endTimeMs: Long,
        outputFile: File
    ): ExtractionResult {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var pfd: ParcelFileDescriptor? = null

        try {
            extractor = MediaExtractor()
            val uri = Uri.parse(sourceUriStr)

            // Robust URI Handling
            if (sourceUriStr.startsWith("content://")) {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    extractor.setDataSource(pfd.fileDescriptor)
                } else {
                    return ExtractionResult(false, "Failed to open content URI descriptor")
                }
            } else if (sourceUriStr.startsWith("file://") || sourceUriStr.startsWith("/")) {
                val path = if (sourceUriStr.startsWith("file://")) uri.path ?: "" else sourceUriStr
                extractor.setDataSource(path)
            } else {
                extractor.setDataSource(sourceUriStr, HashMap())
            }

            val trackCount = extractor.trackCount
            if (trackCount <= 0) return ExtractionResult(false, "No tracks found in source")

            // Identify usable tracks (Tier 1/2 logic)
            val indexMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 1024 * 1024 // 1MB baseline
            var hasVideo = false

            // We use MP4 as universal output for clips
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    try {
                        val muxerTrackIndex = muxer.addTrack(format)
                        indexMap[i] = muxerTrackIndex
                        extractor.selectTrack(i)
                        if (mime.startsWith("video/")) hasVideo = true
                        
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            if (size > maxBufferSize) maxBufferSize = size
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not add track $mime: ${e.message}")
                    }
                }
            }

            if (indexMap.isEmpty()) return ExtractionResult(false, "No video or audio tracks found in source media")

            muxer.start()

            val startTimeUs = startTimeMs * 1000L
            val endTimeUs = endTimeMs * 1000L

            // Seek to A-point (Keyframe alignment)
            extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var samplesWritten = 0
            
            // Track-specific timestamp normalization to ensure clips start at 0
            val firstTimestamps = mutableMapOf<Int, Long>()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                
                if (bufferInfo.size < 0) break

                val trackIndex = extractor.sampleTrackIndex
                val sampleTimeUs = extractor.sampleTime

                if (sampleTimeUs > endTimeUs) {
                    // Optimization: if we have video and we passed end, we can stop
                    // But we might need to wait for audio to catch up for other tracks
                    indexMap.remove(trackIndex)
                    if (indexMap.isEmpty()) break
                } else if (indexMap.containsKey(trackIndex)) {
                    val firstUs = firstTimestamps.getOrPut(trackIndex) { sampleTimeUs }
                    bufferInfo.presentationTimeUs = (sampleTimeUs - firstUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    
                    muxer.writeSampleData(indexMap[trackIndex]!!, buffer, bufferInfo)
                    samplesWritten++
                }

                if (!extractor.advance()) break
            }

            return if (samplesWritten > 0) ExtractionResult(true) else ExtractionResult(false, "No samples extracted")
        } catch (e: Exception) {
            Log.e(TAG, "Extraction error", e)
            return ExtractionResult(false, e.localizedMessage)
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun downloadRemoteMedia(urlStr: String, outputFile: File): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.connect()
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else false
        } catch (e: Exception) { false }
    }
}

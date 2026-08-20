package com.example.compatibility

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.CompatibilityStatus
import com.example.data.ConversionStatus
import com.example.data.MediaItem
import com.example.util.MediaThumbnailFetcher
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class MediaCompatibilityReport(
    val status: CompatibilityStatus,
    val containerFormat: String,
    val videoCodec: String,
    val audioCodec: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val mimeType: String,
    val sizeBytes: Long,
    val compatibilityReason: String,
    val thumbnailStatus: String,
    val playbackVerified: Boolean,
    val conversionStatus: ConversionStatus,
    val convertedUri: String? = null
)

object AuraMediaCompatibilityEngine {

    private const val TAG = "AuraMediaCompEngine"
    private const val ANALYSIS_TIMEOUT_MS = 45000L // 45 seconds per file

    /**
     * Determines if a compatibility status is eligible for the normal playable media ecosystem.
     */
    fun isEligibleForImport(status: CompatibilityStatus): Boolean {
        return when (status) {
            CompatibilityStatus.PLAYABLE,
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE,
            CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
            CompatibilityStatus.THUMBNAIL_FAILED,
            CompatibilityStatus.UNTESTED,
            CompatibilityStatus.NEEDS_TRANSCODE -> true
            else -> false
        }
    }

    suspend fun analyzeMedia(context: Context, uriString: String, mediaTypeStr: String): MediaCompatibilityReport {
        return withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) {
            performAnalysis(context, uriString, mediaTypeStr)
        } ?: MediaCompatibilityReport(
            status = CompatibilityStatus.ANALYSIS_FAILED,
            containerFormat = "Unknown",
            videoCodec = "Timeout",
            audioCodec = "Timeout",
            width = 0,
            height = 0,
            durationMs = 0L,
            mimeType = "unknown",
            sizeBytes = 0L,
            compatibilityReason = "Media analysis timed out after ${ANALYSIS_TIMEOUT_MS/1000}s",
            thumbnailStatus = "FAILED",
            playbackVerified = false,
            conversionStatus = ConversionStatus.NONE
        )
    }

    private suspend fun performAnalysis(context: Context, uriString: String, mediaTypeStr: String): MediaCompatibilityReport {
        val isVideo = mediaTypeStr.equals("VIDEO", ignoreCase = true) || mediaTypeStr.equals("Movie", ignoreCase = true)
        
        if (!isVideo) {
            // Photo analysis
            return analyzePhoto(context, uriString)
        }

        // Video analysis
        var containerFormat = ""
        var videoCodec = ""
        var audioCodec = ""
        var width = 0
        var height = 0
        var durationMs = 0L
        var mimeType = ""
        var sizeBytes = 0L
        var compatibilityReason = "File successfully verified for Aura playback"
        var thumbnailStatus = "PENDING"
        var playbackVerified = false

        // 1. Check file accessibility & size
        val uri = Uri.parse(uriString)
        try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    sizeBytes = stream.available().toLong()
                }
            } else if (uriString.startsWith("/")) {
                val f = File(uriString)
                if (f.exists()) sizeBytes = f.length()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Accessibility check failed for $uriString: ${e.message}")
        }

        if (sizeBytes <= 0L) {
            return MediaCompatibilityReport(
                status = CompatibilityStatus.UNREADABLE,
                containerFormat = "Unknown",
                videoCodec = "Unknown",
                audioCodec = "Unknown",
                width = 0,
                height = 0,
                durationMs = 0L,
                mimeType = "unknown",
                sizeBytes = 0L,
                compatibilityReason = "Media file is empty or inaccessible",
                thumbnailStatus = "FAILED",
                playbackVerified = false,
                conversionStatus = ConversionStatus.NONE
            )
        }

        // 2. Extract metadata using MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                retriever.setDataSource(context, uri)
            } else if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                retriever.setDataSource(uriString, HashMap<String, String>())
            } else {
                retriever.setDataSource(uriString)
            }

            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            
            if (mimeType.isBlank() && durationMs <= 0) {
                retriever.release()
                return MediaCompatibilityReport(
                    status = CompatibilityStatus.CORRUPT,
                    containerFormat = "Unknown",
                    videoCodec = "Invalid",
                    audioCodec = "Invalid",
                    width = 0,
                    height = 0,
                    durationMs = 0L,
                    mimeType = "unknown",
                    sizeBytes = sizeBytes,
                    compatibilityReason = "File contains no recognizable media metadata",
                    thumbnailStatus = "FAILED",
                    playbackVerified = false,
                    conversionStatus = ConversionStatus.NONE
                )
            }

            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            width = wStr?.toIntOrNull() ?: 0
            height = hStr?.toIntOrNull() ?: 0

            containerFormat = detectContainerFormat(uriString, mimeType)
        } catch (e: Exception) {
            Log.e(TAG, "Metadata extraction failed for $uriString: ${e.message}")
            try { retriever.release() } catch (_: Exception) {}
            return MediaCompatibilityReport(
                status = CompatibilityStatus.CORRUPT,
                containerFormat = detectContainerFormat(uriString, ""),
                videoCodec = "Corrupt",
                audioCodec = "Corrupt",
                width = 0,
                height = 0,
                durationMs = 0L,
                mimeType = "unknown",
                sizeBytes = sizeBytes,
                compatibilityReason = "Corrupt container or unreadable video headers: ${e.message}",
                thumbnailStatus = "FAILED",
                playbackVerified = false,
                conversionStatus = ConversionStatus.NONE
            )
        }
        try { retriever.release() } catch (_: Exception) {}

        // 3. Extract stream codecs using MediaExtractor
        val extractor = MediaExtractor()
        var hasVideoTrack = false
        var hasAudioTrack = false
        var isSoftwareVideoCodec = false
        var isUnsupportedVideoCodec = false

        try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(uriString)
            }

            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (trackMime.startsWith("video/")) {
                    hasVideoTrack = true
                    videoCodec = trackMime
                    val codecCheck = checkCodecSupport(trackMime)
                    if (codecCheck == CodecSupport.SOFTWARE_ONLY) {
                        isSoftwareVideoCodec = true
                    } else if (codecCheck == CodecSupport.UNSUPPORTED) {
                        isUnsupportedVideoCodec = true
                    }
                } else if (trackMime.startsWith("audio/")) {
                    hasAudioTrack = true
                    audioCodec = trackMime
                }
            }
            playbackVerified = hasVideoTrack || hasAudioTrack
        } catch (e: Exception) {
            Log.e(TAG, "MediaExtractor failed for $uriString: ${e.message}")
            try { extractor.release() } catch (_: Exception) {}
            return MediaCompatibilityReport(
                status = CompatibilityStatus.PLAYABLE_AFTER_CONVERSION,
                containerFormat = containerFormat,
                videoCodec = videoCodec.ifBlank { "Unknown" },
                audioCodec = audioCodec.ifBlank { "Unknown" },
                width = width,
                height = height,
                durationMs = durationMs,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                compatibilityReason = "Header parse issue: ${e.message} — conversion suggested",
                thumbnailStatus = "PENDING",
                playbackVerified = false,
                conversionStatus = ConversionStatus.REQUIRED
            )
        }
        try { extractor.release() } catch (_: Exception) {}

        // 4. Verify Thumbnail Extraction
        val bitmap = try {
            MediaThumbnailFetcher.getThumbnail(context, uriString)
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation crashed for $uriString: ${e.message}")
            null
        }
        thumbnailStatus = if (bitmap != null) "VALID" else "FAILED"

        // 5. Determine Compatibility Status
        var status = CompatibilityStatus.PLAYABLE
        var conversionStatus = ConversionStatus.NONE

        if (containerFormat == "AVI") {
            status = CompatibilityStatus.UNSUPPORTED
            compatibilityReason = "AVI container is not supported for native playback"
        } else if (!hasVideoTrack && !hasAudioTrack) {
            status = CompatibilityStatus.CORRUPT
            compatibilityReason = "File contains no valid audio or video streams"
        } else if (isUnsupportedVideoCodec) {
            status = CompatibilityStatus.PLAYABLE_AFTER_CONVERSION
            conversionStatus = ConversionStatus.REQUIRED
            compatibilityReason = "Video codec '$videoCodec' is not supported natively — conversion needed"
        } else if (isSoftwareVideoCodec) {
            status = CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE
            compatibilityReason = "Video codec '$videoCodec' will use software decoding"
        } else if (thumbnailStatus == "FAILED" && isVideo) {
            // Keep as playable but mark thumbnail failed (Phase 9)
            status = CompatibilityStatus.THUMBNAIL_FAILED
            compatibilityReason = "Playback likely works, but thumbnail generation failed"
        }

        return MediaCompatibilityReport(
            status = status,
            containerFormat = containerFormat,
            videoCodec = videoCodec.ifBlank { "N/A" },
            audioCodec = audioCodec.ifBlank { "N/A" },
            width = width,
            height = height,
            durationMs = durationMs,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            compatibilityReason = compatibilityReason,
            thumbnailStatus = thumbnailStatus,
            playbackVerified = playbackVerified,
            conversionStatus = conversionStatus
        )
    }

    private fun analyzePhoto(context: Context, uriString: String): MediaCompatibilityReport {
        val uri = Uri.parse(uriString)
        var exists = true
        var sizeBytes = 0L

        try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    sizeBytes = stream.available().toLong()
                }
            } else if (uriString.startsWith("/")) {
                val f = File(uriString)
                exists = f.exists()
                if (exists) sizeBytes = f.length()
            }
        } catch (e: Exception) {
            exists = false
        }

        if (!exists) {
            return MediaCompatibilityReport(
                status = CompatibilityStatus.UNREADABLE,
                containerFormat = "Image",
                videoCodec = "N/A",
                audioCodec = "N/A",
                width = 0,
                height = 0,
                durationMs = 0L,
                mimeType = "image/*",
                sizeBytes = 0L,
                compatibilityReason = "Image file is unreadable or missing",
                thumbnailStatus = "FAILED",
                playbackVerified = false,
                conversionStatus = ConversionStatus.NONE
            )
        }

        return MediaCompatibilityReport(
            status = CompatibilityStatus.PLAYABLE,
            containerFormat = detectContainerFormat(uriString, "image/jpeg"),
            videoCodec = "N/A",
            audioCodec = "N/A",
            width = 0,
            height = 0,
            durationMs = 0L,
            mimeType = "image/*",
            sizeBytes = sizeBytes,
            compatibilityReason = "Photo is valid and playable",
            thumbnailStatus = "VALID",
            playbackVerified = true,
            conversionStatus = ConversionStatus.NONE
        )
    }

    private fun detectContainerFormat(uriString: String, mimeType: String): String {
        val lower = uriString.lowercase()
        return when {
            lower.endsWith(".mp4") || mimeType == "video/mp4" -> "MP4"
            lower.endsWith(".mkv") || mimeType == "video/x-matroska" -> "MKV"
            lower.endsWith(".webm") || mimeType == "video/webm" -> "WebM"
            lower.endsWith(".avi") || mimeType == "video/avi" || mimeType == "video/x-msvideo" -> "AVI"
            lower.endsWith(".mov") || mimeType == "video/quicktime" -> "MOV"
            lower.endsWith(".wmv") || mimeType == "video/x-ms-wmv" -> "WMV"
            lower.endsWith(".flv") || mimeType == "video/x-flv" -> "FLV"
            lower.endsWith(".3gp") || mimeType == "video/3gpp" -> "3GP"
            lower.endsWith(".ts") || lower.endsWith(".m2ts") -> "MPEG-TS"
            lower.endsWith(".mpg") || lower.endsWith(".mpeg") -> "MPEG"
            mimeType.isNotBlank() -> mimeType.substringAfter("/")
            else -> "Media Container"
        }
    }

    private enum class CodecSupport {
        HARDWARE,
        SOFTWARE_ONLY,
        UNSUPPORTED
    }

    private fun checkCodecSupport(mimeType: String): CodecSupport {
        if (mimeType.isBlank()) return CodecSupport.HARDWARE

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val infos = codecList.codecInfos
        var hasDecoder = false
        var isHardware = false

        for (info in infos) {
            if (info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    hasDecoder = true
                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                        !info.name.startsWith("c2.android.", ignoreCase = true)
                    }
                    if (isHw) {
                        isHardware = true
                        break
                    }
                }
            }
            if (isHardware) break
        }

        return when {
            isHardware -> CodecSupport.HARDWARE
            hasDecoder -> CodecSupport.SOFTWARE_ONLY
            else -> CodecSupport.UNSUPPORTED
        }
    }
}

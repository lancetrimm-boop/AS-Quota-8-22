package com.example.util

import android.os.Build
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.data.MediaItem
import com.example.data.db.PlaybackErrorLogEntity
import java.util.UUID

/**
 * Utility for capturing detailed playback diagnostics.
 */
object AuraPlaybackDiagnostics {

    /**
     * Generates a new unique playback session ID.
     */
    fun createSessionId(): String = UUID.randomUUID().toString()

    /**
     * Captures a complete diagnostic record for a playback failure.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun captureError(
        error: PlaybackException,
        player: Player,
        mediaItem: MediaItem?,
        sessionId: String?,
        appVersion: String = "1.0.0", // Fallback if not provided
        media3Version: String = "1.3.1" // Current version from dependencies
    ): PlaybackErrorLogEntity {
        val timestamp = System.currentTimeMillis()
        
        // Error info
        val errorCode = error.errorCode
        val errorCodeName = error.errorCodeName
        val errorMessage = error.message
        val exceptionClass = error.javaClass.simpleName
        
        // Causal chain extraction
        val causeChain = extractCausalChain(error)
        val stackTrace = error.stackTraceToString()
        
        // Playback state
        val playbackState = when (player.playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN"
        }
        val playWhenReady = player.playWhenReady
        val playbackPositionMs = player.currentPosition
        
        // Media info (from MediaItem if provided, otherwise from player if possible)
        val mId = mediaItem?.id
        val mUri = mediaItem?.uriPath
        val mTitle = mediaItem?.title
        val mFileName = extractFileName(mUri)
        val mMimeType = mediaItem?.mediaType // Using existing field as proxy
        val mDuration = mediaItem?.durationMs
        
        // Device info
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        
        // Determination of local vs remote
        val isLocal = when {
            mUri == null -> true
            mUri.startsWith("http://", ignoreCase = true) -> false
            mUri.startsWith("https://", ignoreCase = true) -> false
            else -> true
        }
        
        // Codec/Renderer diagnostics
        var rendererName = ""
        var rendererIndex = -1
        
        // Use type-safe check if possible or reflection as last resort for Media3 internal types
        try {
            // Media3 ExoPlaybackException is often the cause or the exception itself
            val exoException = when {
                error is androidx.media3.exoplayer.ExoPlaybackException -> error
                error.cause is androidx.media3.exoplayer.ExoPlaybackException -> error.cause as androidx.media3.exoplayer.ExoPlaybackException
                else -> null
            }
            
            exoException?.let {
                rendererName = it.rendererName ?: ""
                rendererIndex = it.rendererIndex
            }
        } catch (_: Throwable) {}
        
        val codecName = "" // Decoding details often require Format from the renderer
        val codecMimeType = ""
        
        return PlaybackErrorLogEntity(
            timestamp = timestamp,
            mediaItemId = mId,
            mediaUri = mUri,
            mediaTitle = mTitle,
            fileName = mFileName,
            mimeType = mMimeType,
            durationMs = mDuration,
            playbackPositionMs = playbackPositionMs,
            playbackState = playbackState,
            playWhenReady = playWhenReady,
            errorCode = errorCode,
            errorCodeName = errorCodeName,
            errorMessage = errorMessage,
            exceptionClass = exceptionClass,
            causeChain = causeChain,
            stackTrace = stackTrace,
            rendererName = rendererName,
            rendererIndex = rendererIndex,
            codecName = codecName,
            codecMimeType = codecMimeType,
            deviceManufacturer = manufacturer,
            deviceModel = model,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            appVersion = appVersion,
            media3Version = media3Version,
            networkState = "UNKNOWN", // Placeholder
            isLocalFile = isLocal,
            sessionId = sessionId,
            recoveryAttempted = false,
            recoverySuccessful = null,
            diagnosticSummary = generateSummary(error)
        )
    }

    private fun extractCausalChain(throwable: Throwable?): String {
        if (throwable == null) return ""
        val sb = StringBuilder()
        var current: Throwable? = throwable
        val seen = mutableSetOf<Throwable>()
        
        while (current != null && !seen.contains(current)) {
            seen.add(current)
            if (sb.isNotEmpty()) sb.append(" -> ")
            sb.append("${current.javaClass.simpleName}: ${current.message}")
            current = current.cause
        }
        return sb.toString()
    }

    private fun extractFileName(uri: String?): String? {
        if (uri == null) return null
        return try {
            val lastSlash = uri.lastIndexOf('/')
            if (lastSlash != -1) uri.substring(lastSlash + 1) else uri
        } catch (e: Exception) {
            null
        }
    }

    private fun generateSummary(error: PlaybackException): String {
        return when (error.errorCode) {
            // DECODER
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Hardware decoder initialization failure"
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "Media decoding failure"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Resolution or frame rate exceeds device capabilities"
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Unsupported media format or codec"
            
            // AUDIO
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "Audio output initialization failure"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "Audio write failure"

            // SOURCE / IO
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Media file not found or inaccessible"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failure"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network timeout"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "Invalid MIME type from source"
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "Seek position out of bounds"
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "Cleartext (non-HTTPS) traffic not permitted"
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "Source loading error"

            // CONTAINER / PARSING
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Malformed or corrupt media container"
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Malformed streaming manifest"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Unsupported media container format"

            // DRM
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "Unsupported DRM scheme"
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM provisioning failure"
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM license acquisition failure"

            PlaybackException.ERROR_CODE_UNSPECIFIED -> "Unexpected playback failure"
            else -> "Media3 error: ${error.errorCodeName}"
        }
    }
}

package com.example.compatibility

import android.content.Context
import com.example.data.CompatibilityStatus
import com.example.data.ConversionStatus
import com.example.data.MediaItem

sealed class PlaybackRouteResult {
    data class Playable(
        val playUri: String,
        val item: MediaItem,
        val isSoftwareDecode: Boolean = false,
        val containerFormat: String = "",
        val videoCodec: String = "",
        val audioCodec: String = ""
    ) : PlaybackRouteResult()

    data class NeedsConversion(
        val reason: String,
        val item: MediaItem
    ) : PlaybackRouteResult()

    data class Unsupported(
        val reason: String,
        val item: MediaItem
    ) : PlaybackRouteResult()

    data class Corrupt(
        val reason: String,
        val item: MediaItem
    ) : PlaybackRouteResult()
}

object AuraPlaybackRouter {

    fun resolveRoute(item: MediaItem): PlaybackRouteResult {
        // Preferred Playable URI (Converted URI if conversion succeeded)
        val targetUri = if (item.conversionStatus == ConversionStatus.CONVERTED && !item.convertedUri.isNullOrBlank()) {
            item.convertedUri
        } else if (item.uriPath.isNotBlank()) {
            item.uriPath
        } else {
            item.imageUrl
        }

        if (targetUri.isBlank()) {
            return PlaybackRouteResult.Corrupt(
                reason = "Media item has no valid URI path",
                item = item
            )
        }

        // AURA PHASE 5: Handle Redirection for REPLACED items
        if (item.compatibilityStatus == CompatibilityStatus.REPLACED && !item.replacedByMediaId.isNullOrBlank()) {
            // Ideally we would resolve the new item here, but resolveRoute is synchronous 
            // and item-focused. For now, we signal that this item is unplayable because it was replaced.
            return PlaybackRouteResult.Corrupt(
                reason = "This file has been replaced by a compatible version.",
                item = item
            )
        }

        // If convertedUri is set and valid, media is guaranteed playable
        if (item.conversionStatus == ConversionStatus.CONVERTED && !item.convertedUri.isNullOrBlank()) {
            return PlaybackRouteResult.Playable(
                playUri = item.convertedUri,
                item = item,
                isSoftwareDecode = false,
                containerFormat = item.containerFormat.ifBlank { "MP4 (Universal)" },
                videoCodec = item.videoCodec.ifBlank { "video/avc" },
                audioCodec = item.audioCodec.ifBlank { "audio/mp4a-latm" }
            )
        }

        return when (item.compatibilityStatus) {
            CompatibilityStatus.PLAYABLE, CompatibilityStatus.THUMBNAIL_FAILED -> {
                PlaybackRouteResult.Playable(
                    playUri = targetUri,
                    item = item,
                    isSoftwareDecode = false,
                    containerFormat = item.containerFormat,
                    videoCodec = item.videoCodec,
                    audioCodec = item.audioCodec
                )
            }
            CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE -> {
                PlaybackRouteResult.Playable(
                    playUri = targetUri,
                    item = item,
                    isSoftwareDecode = true,
                    containerFormat = item.containerFormat,
                    videoCodec = item.videoCodec,
                    audioCodec = item.audioCodec
                )
            }
            CompatibilityStatus.PLAYABLE_AFTER_CONVERSION -> {
                PlaybackRouteResult.NeedsConversion(
                    reason = item.compatibilityReason.ifBlank { "This video format requires conversion for smooth playback on this device" },
                    item = item
                )
            }
            CompatibilityStatus.UNSUPPORTED -> {
                PlaybackRouteResult.Unsupported(
                    reason = item.compatibilityReason.ifBlank { "Video codec or container format is not supported on this device" },
                    item = item
                )
            }
            CompatibilityStatus.CORRUPT, CompatibilityStatus.UNREADABLE, CompatibilityStatus.DELETED, CompatibilityStatus.REPLACED -> {
                PlaybackRouteResult.Corrupt(
                    reason = item.compatibilityReason.ifBlank { "Media file is corrupt, unreadable, missing, or has been replaced" },
                    item = item
                )
            }
            CompatibilityStatus.ANALYSIS_PENDING, 
            CompatibilityStatus.ANALYSIS_IN_PROGRESS, 
            CompatibilityStatus.ANALYSIS_FAILED,
            CompatibilityStatus.UNTESTED,
            CompatibilityStatus.NEEDS_TRANSCODE -> {
                // Default fallback: allow attempt but mark reason
                PlaybackRouteResult.Playable(
                    playUri = targetUri,
                    item = item,
                    isSoftwareDecode = false,
                    containerFormat = item.containerFormat,
                    videoCodec = item.videoCodec,
                    audioCodec = item.audioCodec
                )
            }
        }
    }
}

package com.example.data.intelligence

import android.media.MediaCodecList
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import com.example.data.ConversionEligibility
import com.example.data.ConversionRecommendation
import com.example.data.db.PlaybackErrorLogEntity

/**
 * Intelligent advisor that determines if a playback failure can be remediated by conversion.
 */
object AuraConversionAdvisor {

    /**
     * Evaluates whether a recorded playback error is a candidate for transcoding.
     */
    fun evaluateEligibility(error: PlaybackErrorLogEntity): ConversionEligibility {
        return createRecommendation(error).eligibility
    }

    /**
     * Creates a detailed conversion recommendation for a playback error.
     */
    fun createRecommendation(error: PlaybackErrorLogEntity): ConversionRecommendation {
        val uriStr = error.mediaUri
        if (uriStr == null) {
            return ConversionRecommendation(
                eligibility = ConversionEligibility.UNAVAILABLE,
                reason = "Source URI missing",
                explanation = "Aura could not access the source media to determine whether conversion is appropriate."
            )
        }

        // 1. Filter by error codes that are typically codec/format related
        val isRemediableError = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> true
            else -> false
        }

        if (!isRemediableError) {
            return ConversionRecommendation(
                eligibility = ConversionEligibility.NOT_RECOMMENDED,
                reason = "Non-codec error",
                explanation = "This playback failure does not appear to be caused by a codec or container compatibility issue."
            )
        }

        // 2. Verify device has H.264 encoding capability (our target)
        if (!hasH264Encoder()) {
            return ConversionRecommendation(
                eligibility = ConversionEligibility.NOT_RECOMMENDED,
                reason = "Encoder unavailable",
                explanation = "This device does not support H.264 encoding, which is required for conversion."
            )
        }

        return ConversionRecommendation(
            eligibility = ConversionEligibility.CONVERTIBLE,
            reason = "Codec/Format incompatibility",
            sourceContainer = error.mimeType?.substringAfter("/"),
            sourceVideoCodec = error.codecName,
            explanation = "Playback failed because the source uses a format or codec that is not supported by this device. H.264/AAC MP4 is available as a compatible target."
        )
    }

    private fun hasH264Encoder(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        return codecList.codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { type ->
                type.equals(MimeTypes.VIDEO_H264, ignoreCase = true)
            }
        }
    }
}

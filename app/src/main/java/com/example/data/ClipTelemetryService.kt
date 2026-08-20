package com.example.data

import android.util.Log

object ClipTelemetryService {

    private const val TAG = "ClipTelemetryService"

    enum class InteractionType {
        PREVIEW,
        SELECT,
        EXPORT
    }

    fun logInteraction(
        repository: MediaRepository,
        mediaId: String,
        clip: ClipCandidate,
        type: InteractionType
    ) {
        Log.d(TAG, "Logging clip interaction [$type] for clip '${clip.title}' (MediaID: $mediaId, Range: ${clip.startTimeMs}ms - ${clip.endTimeMs}ms)")
        repository.logClipInteraction(
            mediaId = mediaId,
            clipTitle = clip.title,
            startTimeMs = clip.startTimeMs,
            endTimeMs = clip.endTimeMs,
            type = type
        )
    }
}

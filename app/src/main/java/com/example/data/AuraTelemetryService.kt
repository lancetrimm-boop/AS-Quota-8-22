package com.example.data

import android.util.Log

/**
 * Authoritative telemetry service for Aura behavioral event tracking.
 * Distinguishes between Exposure, Engagement, and Intent.
 */
object AuraTelemetryService {

    private const val TAG = "AuraTelemetry"

    enum class EventType {
        // LAYER 1: Discovery Feed
        OBSESSION_EXPOSURE,
        OBSESSION_OPENED,
        OBSESSION_ABANDONED,
        
        // LAYER 2: Obsession Detail
        MEDIA_EXPOSURE,
        MEDIA_ENGAGEMENT,      // Meaningful view (e.g., > 5s or play click)
        MEDIA_ABANDONED,       // Rapid skip
        MEDIA_REPLAY,
        MEDIA_COMPLETED,
        
        // LAYER 3: Expansion & Navigation
        BATCH_EXPANDED,
        TRY_SOMETHING_NEW,
        END_OF_BATCH_REACHED,
        NAVIGATED_BACK
    }

    /**
     * Logs a behavioral event. 
     * Determines if the event should trigger learning updates in the repository.
     */
    fun logEvent(
        repository: MediaRepository,
        type: EventType,
        itemId: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val message = "Event: $type | Item: ${itemId ?: "N/A"} | Metadata: $metadata"
        Log.i(TAG, message)

        // Map events to authoritative learning signals in MediaRepository
        when (type) {
            EventType.MEDIA_EXPOSURE -> {
                itemId?.let { repository.recordExposure(it) }
            }
            EventType.MEDIA_ENGAGEMENT -> {
                // Meaningful engagement triggers a "View" signal
                itemId?.let { repository.recordView(it) }
            }
            EventType.MEDIA_REPLAY -> {
                // Replay is also a view signal
                itemId?.let { repository.recordView(it) }
            }
            EventType.MEDIA_COMPLETED -> {
                itemId?.let { repository.recordMediaCompletion(it) }
            }
            EventType.MEDIA_ABANDONED -> {
                // Future negative signal implementation point
            }
            EventType.BATCH_EXPANDED -> {
                repository.injectEvidence(
                    tier = EvidenceTier.PRODUCTION,
                    sampleCount = 1,
                    score = 1.0, // Indication that current cluster is valuable
                    quality = 0.5
                )
            }
            EventType.TRY_SOMETHING_NEW -> {
                repository.injectEvidence(
                    tier = EvidenceTier.PRODUCTION,
                    sampleCount = 1,
                    score = 0.8, // Indication that novelty was desired but cluster was okay
                    quality = 0.3
                )
            }
            else -> {
                // Analytics-only
            }
        }
    }
}

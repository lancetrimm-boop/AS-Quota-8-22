package com.example.data.contribution

/**
 * Result enum for contribution upload operations to map transport outcomes
 * to queue lifecycle states.
 */
enum class UploadResult {
    SUCCESS,            // Maps to COMPLETED
    TRANSIENT_FAILURE,  // Maps to FAILED (eligible for retry)
    PERMANENT_FAILURE,  // Maps to FAILED (non-retryable metadata update)
    MALFORMED_PAYLOAD   // Maps to REJECTED
}

/**
 * Interface defining local or remote contribution upload abstraction (Phase 3B.1).
 * For Phase 3B.1, this interface operates exclusively on network-neutral [SanitizedContributionUploadDto]
 * items to decouple local database identifiers from the gateway boundary.
 */
interface ContributionUploadGateway {
    /**
     * Processes a batch of sanitized contribution DTOs.
     * Returns [UploadResult] based on processing outcome.
     */
    suspend fun uploadBatch(events: List<SanitizedContributionUploadDto>): UploadResult
}

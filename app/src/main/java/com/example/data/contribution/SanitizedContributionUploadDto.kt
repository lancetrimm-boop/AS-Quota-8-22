package com.example.data.contribution

import com.example.data.db.ContributionQueueEntity

/**
 * Pure network-neutral Data Transfer Object for sanitized contribution uploads (Phase 3B.1).
 *
 * PRIVACY & BOUNDARY CONSTRAINTS:
 * - MUST NOT contain local Room primary key `id`.
 * - MUST NOT contain media IDs, URIs, filenames, titles, file paths, user IDs, device IDs, advertising IDs, IP addresses, or exact timestamps.
 * - Contains ONLY sanitized Phase 1 event types, schema versions, and sanitized JSON payloads.
 */
data class SanitizedContributionUploadDto(
    val eventType: String,
    val schemaVersion: String,
    val payloadJson: String,
    val idempotencyKey: String = ""
)

/**
 * Extension function mapping a local Room [ContributionQueueEntity] to a network-neutral [SanitizedContributionUploadDto].
 * Explicitly drops local database `id`, `createdAt` timestamp, and local `status`.
 */
fun ContributionQueueEntity.toSanitizedUploadDto(): SanitizedContributionUploadDto {
    return SanitizedContributionUploadDto(
        eventType = this.eventType,
        schemaVersion = this.schemaVersion,
        payloadJson = this.payloadJson,
        idempotencyKey = this.idempotencyKey
    )
}

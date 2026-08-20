package com.example.data.contribution

import com.example.data.db.ContributionQueueEntity

/**
 * Result data class for local contribution sync operations.
 */
data class SyncResult(
    val processedCount: Int,
    val success: Boolean,
    val message: String
)

/**
 * Phase 3A — Local Contribution Sync Manager.
 *
 * STRICT LOCAL-ONLY SYNC PREPARATION ENGINE:
 * 1. Checks consent before and during local processing; fails closed and purges queue if consent is false.
 * 2. Runs crash recovery on startup/sync by resetting orphaned PROCESSING records to PENDING.
 * 3. Respects batch size limits when querying queued records.
 * 4. Marks records PROCESSING during dispatch, marks records COMPLETED on success, marks records FAILED on failure.
 * 5. Strictly local and offline: NO HTTP, NO WorkManager, NO network calls.
 */
class LocalContributionSyncManager(
    private val repository: ContributionQueueRepository,
    private val uploadGateway: ContributionUploadGateway
) {

    /**
     * Initializes the sync manager and performs crash recovery for orphaned PROCESSING records.
     */
    suspend fun initialize() {
        if (!repository.isConsentGranted()) {
            repository.purgeAllQueuedEvents()
        } else {
            repository.resetProcessingStatus()
        }
    }

    /**
     * Performs a local synchronization cycle for queued contribution events.
     *
     * @param batchSize Max number of records to process in a single batch (default 10).
     */
    suspend fun performSync(batchSize: Int = 10): SyncResult {
        // Step 1: Pre-check consent
        if (!repository.isConsentGranted()) {
            repository.purgeAllQueuedEvents()
            return SyncResult(
                processedCount = 0,
                success = false,
                message = "Consent not granted; queue purged"
            )
        }

        // Step 2: Crash recovery - reset orphaned PROCESSING records to PENDING
        repository.resetProcessingStatus()

        // Step 3: Fetch pending PENDING events
        val pendingEvents = repository.getPendingEvents()
        if (pendingEvents.isEmpty()) {
            return SyncResult(
                processedCount = 0,
                success = true,
                message = "No pending records to sync"
            )
        }

        // Step 4: Slice batch to batchSize limit
        val batch = pendingEvents.take(batchSize)
        val batchIds = batch.map { it.id }

        // Step 5: Transition batch status to PROCESSING
        repository.updateEventStatus(batchIds, ContributionQueueRepository.STATUS_PROCESSING)

        // Step 6: Re-check consent before dispatching
        if (!repository.isConsentGranted()) {
            repository.purgeAllQueuedEvents()
            return SyncResult(
                processedCount = 0,
                success = false,
                message = "Consent revoked during batch preparation; queue purged"
            )
        }

        // Step 7: Dispatch mapped DTO batch to gateway (excluding local Room IDs)
        val dtos = batch.map { it.toSanitizedUploadDto() }
        val uploadResult = uploadGateway.uploadBatch(dtos)

        // Step 8: Handle result and map transport outcomes to queue lifecycle states
        return when (uploadResult) {
            UploadResult.SUCCESS -> {
                // Success → COMPLETED
                repository.updateEventStatus(batchIds, ContributionQueueRepository.STATUS_COMPLETED)
                repository.cleanupCompletedRecords()
                SyncResult(
                    processedCount = batch.size,
                    success = true,
                    message = "Processed and marked ${batch.size} events as COMPLETED"
                )
            }
            UploadResult.MALFORMED_PAYLOAD -> {
                // Malformed payload → REJECTED
                repository.updateEventStatus(batchIds, ContributionQueueRepository.STATUS_REJECTED)
                SyncResult(
                    processedCount = 0,
                    success = false,
                    message = "Server rejected payload as malformed; marked ${batch.size} events as REJECTED"
                )
            }
            UploadResult.PERMANENT_FAILURE -> {
                // Permanent authorization failure → FAILED (non-retryable)
                // We set retry count to MAX to ensure it is not picked up again by getRetryableEvents
                repository.updateEventMetadataForced(
                    ids = batchIds, 
                    status = ContributionQueueRepository.STATUS_FAILED,
                    retryCount = ContributionQueueRepository.MAX_RETRIES_PER_RECORD
                )
                SyncResult(
                    processedCount = 0,
                    success = false,
                    message = "Gateway reported permanent failure; marked ${batch.size} events as FAILED (non-retryable)"
                )
            }
            UploadResult.TRANSIENT_FAILURE -> {
                // Temporary network failure → FAILED (eligible for retry)
                repository.updateEventRetryMetadata(batchIds, ContributionQueueRepository.STATUS_FAILED)
                SyncResult(
                    processedCount = 0,
                    success = false,
                    message = "Gateway reported transient failure; marked ${batch.size} events as FAILED (retryable)"
                )
            }
        }
    }
}

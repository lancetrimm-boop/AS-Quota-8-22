package com.example.data.contribution

import com.example.data.db.ContributionQueueDao
import com.example.data.db.ContributionQueueEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Global Aura Intelligence — Local Encrypted Contribution Queue Repository (Phase 2)
 *
 * CLIENT-SIDE PRIVACY BOUNDARY ENFORCEMENT:
 * 1. ONLY accepts sanitized Phase 1 contract payloads (ElemTasteVectorSnapshotV1, ElemPairwiseDeltaV1,
 *    ElemSkipCalibrationV1, ElemRecommendationFeedbackV1).
 * 2. Rejects any attempt to directly queue raw local entities (MediaEntity, AISkipEventEntity, etc.).
 * 3. Enforces consent gate: fail-closed if consent is not explicitly granted.
 * 4. Supports immediate revocation purging of queued contribution events without affecting local intelligence.
 * 5. Strictly local and offline: NO network transmission, NO WorkManager, NO backend communication.
 */
class ContributionQueueRepository(
    private val dao: ContributionQueueDao,
    val consentManager: ContributionConsentManager = ContributionConsentManager()
) {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    init {
        consentManager.setOnConsentRevokedListener {
            purgeAllQueuedEventsSync()
        }
    }

    /**
     * Updates consent state via [ContributionConsentManager].
     * If consent is revoked (false), immediately purges all queued events.
     */
    @Synchronized
    fun setConsentGranted(granted: Boolean) {
        if (granted) {
            consentManager.grantConsent()
        } else {
            consentManager.revokeConsent()
        }
    }

    /**
     * Returns current opt-in consent status (default false / fail-closed).
     */
    fun isConsentGranted(): Boolean = consentManager.isConsentGranted()

    /**
     * Returns current strongly-typed [ConsentState].
     */
    fun getConsentState(): ConsentState = consentManager.getConsentState()

    /**
     * Enqueues an ElemTasteVectorSnapshotV1 sanitized payload.
     * Fails closed if consent is false or payload is invalid.
     */
    suspend fun enqueueTasteVectorSnapshot(payload: ElemTasteVectorSnapshotV1): Long {
        if (!isConsentGranted()) return -1L
        val adapter = moshi.adapter(ElemTasteVectorSnapshotV1::class.java)
        val json = adapter.toJson(payload) ?: return -1L

        // AURA PHASE 3A: Deterministic idempotency key to prevent local duplicates
        val idempotencyKey = "taste_v1_${payload.timeWindowHour}_${json.hashCode()}"

        val entity = ContributionQueueEntity(
            eventType = payload.eventType,
            schemaVersion = payload.schemaVersion,
            payloadJson = json,
            createdAt = System.currentTimeMillis(),
            status = STATUS_PENDING,
            idempotencyKey = idempotencyKey
        )
        return dao.insert(entity)
    }

    /**
     * Enqueues an ElemPairwiseDeltaV1 sanitized payload.
     * Fails closed if consent is false or payload is invalid.
     */
    suspend fun enqueuePairwiseDelta(payload: ElemPairwiseDeltaV1): Long {
        if (!isConsentGranted()) return -1L
        val adapter = moshi.adapter(ElemPairwiseDeltaV1::class.java)
        val json = adapter.toJson(payload) ?: return -1L

        // AURA PHASE 3A: Deterministic idempotency key
        val idempotencyKey = "pairwise_v1_${payload.timeWindowHour}_${json.hashCode()}"

        val entity = ContributionQueueEntity(
            eventType = payload.eventType,
            schemaVersion = payload.schemaVersion,
            payloadJson = json,
            createdAt = System.currentTimeMillis(),
            status = STATUS_PENDING,
            idempotencyKey = idempotencyKey
        )
        return dao.insert(entity)
    }

    /**
     * Enqueues an ElemSkipCalibrationV1 sanitized payload.
     * Fails closed if consent is false or payload is invalid.
     */
    suspend fun enqueueSkipCalibration(payload: ElemSkipCalibrationV1): Long {
        if (!isConsentGranted()) return -1L
        val adapter = moshi.adapter(ElemSkipCalibrationV1::class.java)
        val json = adapter.toJson(payload) ?: return -1L

        // AURA PHASE 3A: Deterministic idempotency key
        val idempotencyKey = "skip_v1_${payload.timeWindowHour}_${json.hashCode()}"

        val entity = ContributionQueueEntity(
            eventType = payload.eventType,
            schemaVersion = payload.schemaVersion,
            payloadJson = json,
            createdAt = System.currentTimeMillis(),
            status = STATUS_PENDING,
            idempotencyKey = idempotencyKey
        )
        return dao.insert(entity)
    }

    /**
     * Enqueues an ElemRecommendationFeedbackV1 sanitized payload.
     * Fails closed if consent is false or payload is invalid.
     */
    suspend fun enqueueRecommendationFeedback(payload: ElemRecommendationFeedbackV1): Long {
        if (!isConsentGranted()) return -1L
        val adapter = moshi.adapter(ElemRecommendationFeedbackV1::class.java)
        val json = adapter.toJson(payload) ?: return -1L

        // AURA PHASE 3A: Deterministic idempotency key
        val idempotencyKey = "feedback_v1_${payload.timeWindowHour}_${json.hashCode()}"

        val entity = ContributionQueueEntity(
            eventType = payload.eventType,
            schemaVersion = payload.schemaVersion,
            payloadJson = json,
            createdAt = System.currentTimeMillis(),
            status = STATUS_PENDING,
            idempotencyKey = idempotencyKey
        )
        return dao.insert(entity)
    }

    /**
     * Backfills missing idempotency keys for legacy records.
     * Ensures every contribution eligible for upload has a unique UUID v4 idempotency key.
     */
    suspend fun backfillMissingIdempotencyKeys() {
        val records = dao.getRecordsMissingIdempotencyKey()
        for (record in records) {
            val key = java.util.UUID.randomUUID().toString()
            dao.updateIdempotencyKey(record.id, key)
        }
    }

    /**
     * Retrieves all pending queued contribution events after ensuring idempotency keys are backfilled.
     */
    suspend fun getPendingEvents(): List<ContributionQueueEntity> {
        backfillMissingIdempotencyKeys()
        // AURA PHASE 3B: Include PENDING and retryable FAILED records
        return dao.getRetryableEvents(maxRetries = MAX_RETRIES_PER_RECORD)
    }

    /**
     * Retrieves bounded batch of pending queued contribution events.
     */
    suspend fun getPendingEventsBounded(limit: Int): List<ContributionQueueEntity> {
        backfillMissingIdempotencyKeys()
        // For simplicity, we just filter the list for now or we could add a bounded query to DAO
        return getPendingEvents().take(limit)
    }

    /**
     * Retrieves all queued contribution events regardless of status.
     */
    suspend fun getAllQueuedEvents(): List<ContributionQueueEntity> {
        backfillMissingIdempotencyKeys()
        return dao.getAll()
    }

    /**
     * Returns count of PENDING items in contribution queue.
     */
    suspend fun getPendingCount(): Int {
        return dao.getByStatus(STATUS_PENDING).size
    }

    /**
     * Returns total count of all items in contribution queue regardless of status.
     */
    suspend fun getTotalQueuedCount(): Int {
        return dao.getCount()
    }

    /**
     * Returns total count of items in contribution queue.
     * @deprecated Use [getPendingCount] or [getTotalQueuedCount]
     */
    suspend fun getQueuedCount(): Int {
        return getPendingCount()
    }

    /**
     * Updates status for specified queue IDs (e.g. PENDING, PROCESSING, COMPLETED, FAILED).
     */
    suspend fun updateEventStatus(ids: List<Long>, status: String) {
        if (ids.isNotEmpty()) {
            dao.updateStatus(ids, status)
        }
    }

    /**
     * Updates status and retry metadata (increments retryCount and updates lastAttemptTimestamp) for specified queue IDs.
     */
    suspend fun updateEventRetryMetadata(ids: List<Long>, status: String, timestamp: Long = System.currentTimeMillis()) {
        if (ids.isNotEmpty()) {
            dao.updateStatusAndRetry(ids, status, timestamp)
        }
    }

    /**
     * Forces specific retry count and status (e.g. for non-retryable failures).
     */
    suspend fun updateEventMetadataForced(ids: List<Long>, status: String, retryCount: Int, timestamp: Long = System.currentTimeMillis()) {
        if (ids.isNotEmpty()) {
            dao.updateStatusAndMetadata(ids, status, retryCount, timestamp)
        }
    }

    /**
     * Resets any orphaned PROCESSING records back to PENDING status (for crash recovery).
     */
    suspend fun resetProcessingStatus() {
        val processingEvents = dao.getByStatus(STATUS_PROCESSING)
        if (processingEvents.isNotEmpty()) {
            val ids = processingEvents.map { it.id }
            dao.updateStatus(ids, STATUS_PENDING)
        }
    }

    /**
     * Deletes specified contribution events from queue.
     */
    suspend fun deleteEvents(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            dao.deleteByIds(ids)
        }
    }

    /**
     * Immediately purges all queued contribution records.
     * Note: This ONLY clears the outbound contribution queue; local learning and TasteDNA are untouched.
     */
    suspend fun purgeAllQueuedEvents() {
        dao.clearAll()
    }

    /**
     * Cleans up COMPLETED records older than the specified age.
     */
    suspend fun cleanupCompletedRecords(olderThanMs: Long = 86400000L * 7) { // Default 7 days
        val allCompleted = dao.getByStatus(STATUS_COMPLETED)
        val now = System.currentTimeMillis()
        val toDelete = allCompleted.filter { now - it.createdAt > olderThanMs }.map { it.id }
        if (toDelete.isNotEmpty()) {
            dao.deleteByIds(toDelete)
        }
    }

    /**
     * Synchronous/blocking variant for consent revocation fallback.
     */
    private fun purgeAllQueuedEventsSync() {
        kotlinx.coroutines.runBlocking {
            dao.clearAll()
        }
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_REJECTED = "REJECTED"

        const val MAX_RETRIES_PER_RECORD = 3
    }
}

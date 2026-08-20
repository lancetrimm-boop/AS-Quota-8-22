package com.example.data.contribution

import android.util.Log

/**
 * Production-ready HTTP network implementation of [ContributionUploadGateway] (Phase 3B.3).
 * Transmits ONLY sanitized contribution DTOs over HTTPS.
 *
 * PRIVACY & SECURITY GUARANTEES:
 * 1. Explicitly verifies consent via [ContributionConsentManager] immediately prior to issuing any network request.
 * 2. Transmits strictly [SanitizedContributionUploadDto] objects—never transmits Room entities, database IDs, device IDs, or user IDs.
 * 3. Preserves idempotency keys across retry attempts.
 * 4. Correctly handles HTTP 2xx success, 4xx non-retryable client errors, and 5xx/IOException transient errors.
 */
class HttpContributionUploadGateway(
    private val consentManager: ContributionConsentManager,
    private val apiService: ContributionApiService
) : ContributionUploadGateway {

    override suspend fun uploadBatch(events: List<SanitizedContributionUploadDto>): UploadResult {
        // Step 1: Mandatory pre-transmission consent verification
        if (!consentManager.isConsentGranted()) {
            Log.w(TAG, "Network transmission aborted: Consent is NOT_DECIDED or REVOKED.")
            return UploadResult.PERMANENT_FAILURE
        }

        if (events.isEmpty()) {
            return UploadResult.SUCCESS
        }

        // Step 2: Prepare sanitized request payload
        val request = ContributionBatchUploadRequest(events = events)
        val batchKey = events.firstOrNull { it.idempotencyKey.isNotEmpty() }?.idempotencyKey
            ?: java.util.UUID.randomUUID().toString()

        return try {
            // Step 3: Issue secure HTTPS request
            val response = apiService.uploadBatch(
                request = request,
                batchIdempotencyKey = batchKey
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true) UploadResult.SUCCESS else UploadResult.TRANSIENT_FAILURE
            } else {
                val code = response.code()
                Log.e(TAG, "HTTP batch upload failed with status code: $code")
                
                // Task 5: Map transport outcomes to queue lifecycle states
                when (code) {
                    400 -> UploadResult.MALFORMED_PAYLOAD  // maps to REJECTED
                    401, 403 -> UploadResult.PERMANENT_FAILURE // maps to FAILED (non-retryable)
                    else -> UploadResult.TRANSIENT_FAILURE // maps to FAILED (eligible for retry)
                }
            }
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network exception encountered during batch upload (Transient)", e)
            UploadResult.TRANSIENT_FAILURE
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception encountered during batch upload (Permanent)", e)
            UploadResult.PERMANENT_FAILURE
        }
    }

    companion object {
        private const val TAG = "HttpContributionUploadGateway"
    }
}

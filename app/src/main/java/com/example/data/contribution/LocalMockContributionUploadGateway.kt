package com.example.data.contribution

/**
 * Local-only mock implementation of [ContributionUploadGateway] for Phase 3B.1.
 * Keeps uploaded DTOs strictly in memory for local testing and verification without any network calls.
 */
class LocalMockContributionUploadGateway(
    private var shouldSucceed: Boolean = true
) : ContributionUploadGateway {

    private val uploadedBatches = mutableListOf<List<SanitizedContributionUploadDto>>()

    fun setShouldSucceed(succeed: Boolean) {
        shouldSucceed = succeed
    }

    fun getUploadedBatches(): List<List<SanitizedContributionUploadDto>> {
        return uploadedBatches.toList()
    }

    fun getUploadedEvents(): List<SanitizedContributionUploadDto> {
        return uploadedBatches.flatten()
    }

    fun clearHistory() {
        uploadedBatches.clear()
    }

    override suspend fun uploadBatch(events: List<SanitizedContributionUploadDto>): UploadResult {
        return if (shouldSucceed) {
            uploadedBatches.add(events.toList())
            UploadResult.SUCCESS
        } else {
            UploadResult.TRANSIENT_FAILURE
        }
    }
}

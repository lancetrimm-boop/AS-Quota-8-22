package com.example.data.contribution

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request payload model for batch upload of sanitized contribution DTOs.
 */
@JsonClass(generateAdapter = true)
data class ContributionBatchUploadRequest(
    @Json(name = "events") val events: List<SanitizedContributionUploadDto>
)

/**
 * Response model received from contribution backend service.
 */
@JsonClass(generateAdapter = true)
data class ContributionUploadResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "processedCount") val processedCount: Int = 0,
    @Json(name = "message") val message: String? = null
)

package com.example.data.contribution

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit API interface defining secure HTTPS batch transmission endpoint for Phase 3B.3.
 */
interface ContributionApiService {
    @POST("v1/contributions/batch")
    suspend fun uploadBatch(
        @Body request: ContributionBatchUploadRequest,
        @Header("X-Batch-Idempotency-Key") batchIdempotencyKey: String? = null
    ): Response<ContributionUploadResponse>
}

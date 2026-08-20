package com.example.data.contribution

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HttpContributionUploadGatewayTest {

    private lateinit var consentManager: ContributionConsentManager
    private lateinit var fakeApiService: FakeContributionApiService
    private lateinit var gateway: HttpContributionUploadGateway

    @Before
    fun setUp() {
        consentManager = ContributionConsentManager()
        fakeApiService = FakeContributionApiService()
        gateway = HttpContributionUploadGateway(consentManager, fakeApiService)
    }

    @Test
    fun testUploadBatch_ConsentGranted_Http200Success_ReturnsTrue() = runBlocking {
        consentManager.grantConsent()
        fakeApiService.nextResponse = Response.success(
            ContributionUploadResponse(success = true, processedCount = 1, message = "Accepted")
        )

        val dto = SanitizedContributionUploadDto(
            eventType = "ELEM_TASTE_VECTOR_SNAPSHOT_V1",
            schemaVersion = "1.0",
            payloadJson = "{\"vibrancy\":0.8}",
            idempotencyKey = "uuid-test-1234"
        )

        val result = gateway.uploadBatch(listOf(dto))

        assertEquals(UploadResult.SUCCESS, result)
        assertEquals(1, fakeApiService.callCount)
        assertNotNull(fakeApiService.lastRequest)
        assertEquals(1, fakeApiService.lastRequest?.events?.size)
        assertEquals("ELEM_TASTE_VECTOR_SNAPSHOT_V1", fakeApiService.lastRequest?.events?.get(0)?.eventType)
        assertEquals("uuid-test-1234", fakeApiService.lastRequest?.events?.get(0)?.idempotencyKey)
        assertEquals("uuid-test-1234", fakeApiService.lastBatchIdempotencyKey)
    }

    @Test
    fun testUploadBatch_ConsentNotGranted_NotDecided_AbortsNetworkCall() = runBlocking {
        consentManager.resetConsent() // ConsentState.NOT_DECIDED
        assertFalse(consentManager.isConsentGranted())

        val dto = SanitizedContributionUploadDto(
            eventType = "ELEM_PAIRWISE_DELTA_V1",
            schemaVersion = "1.0",
            payloadJson = "{}",
            idempotencyKey = "uuid-test-5678"
        )

        val result = gateway.uploadBatch(listOf(dto))

        assertEquals(UploadResult.PERMANENT_FAILURE, result)
        assertEquals(0, fakeApiService.callCount)
        assertNull(fakeApiService.lastRequest)
    }

    @Test
    fun testUploadBatch_ConsentRevoked_AbortsNetworkCall() = runBlocking {
        consentManager.grantConsent()
        consentManager.revokeConsent() // ConsentState.REVOKED
        assertFalse(consentManager.isConsentGranted())

        val dto = SanitizedContributionUploadDto(
            eventType = "ELEM_PAIRWISE_DELTA_V1",
            schemaVersion = "1.0",
            payloadJson = "{}",
            idempotencyKey = "uuid-test-5678"
        )

        val result = gateway.uploadBatch(listOf(dto))

        assertEquals(UploadResult.PERMANENT_FAILURE, result)
        assertEquals(0, fakeApiService.callCount)
        assertNull(fakeApiService.lastRequest)
    }

    @Test
    fun testUploadBatch_Http400ClientError_ReturnsMalformed() = runBlocking {
        consentManager.grantConsent()
        val errorBody = "{\"error\":\"Invalid request\"}".toResponseBody("application/json".toMediaTypeOrNull())
        fakeApiService.nextResponse = Response.error(400, errorBody)

        val dto = SanitizedContributionUploadDto(
            eventType = "ELEM_RECOMMENDATION_FEEDBACK_V1",
            schemaVersion = "1.0",
            payloadJson = "{}",
            idempotencyKey = "uuid-test-4000"
        )

        val result = gateway.uploadBatch(listOf(dto))

        assertEquals(UploadResult.MALFORMED_PAYLOAD, result)
        assertEquals(1, fakeApiService.callCount)
    }

    @Test
    fun testUploadBatch_Http5xxServerError_ReturnsTransient() = runBlocking {
        consentManager.grantConsent()
        val errorBody = "{\"error\":\"Internal error\"}".toResponseBody("application/json".toMediaTypeOrNull())
        fakeApiService.nextResponse = Response.error(500, errorBody)

        val dto = SanitizedContributionUploadDto(
            eventType = "ELEM_SKIP_CALIBRATION_V1",
            schemaVersion = "1.0",
            payloadJson = "{}",
            idempotencyKey = "uuid-test-5000"
        )

        val result = gateway.uploadBatch(listOf(dto))

        assertEquals(UploadResult.TRANSIENT_FAILURE, result)
        assertEquals(1, fakeApiService.callCount)
    }

    @Test
    fun testUploadBatch_NetworkException_ReturnsTransient() = runBlocking {
        consentManager.grantConsent()
        fakeApiService.nextException = IOException("Connection timed out")

        val dto = SanitizedContributionUploadDto(
            eventType = "ELEM_SKIP_CALIBRATION_V1",
            schemaVersion = "1.0",
            payloadJson = "{}",
            idempotencyKey = "uuid-test-9000"
        )

        val result = gateway.uploadBatch(listOf(dto))

        assertEquals(UploadResult.TRANSIENT_FAILURE, result)
        assertEquals(1, fakeApiService.callCount)
    }

    @Test
    fun testUploadBatch_EmptyList_ReturnsSuccessWithoutNetworkCall() = runBlocking {
        consentManager.grantConsent()

        val result = gateway.uploadBatch(emptyList())

        assertEquals(UploadResult.SUCCESS, result)
        assertEquals(0, fakeApiService.callCount)
    }
}

private class FakeContributionApiService : ContributionApiService {
    var nextResponse: Response<ContributionUploadResponse>? = null
    var nextException: Exception? = null
    var lastRequest: ContributionBatchUploadRequest? = null
    var lastBatchIdempotencyKey: String? = null
    var callCount = 0

    override suspend fun uploadBatch(
        request: ContributionBatchUploadRequest,
        batchIdempotencyKey: String?
    ): Response<ContributionUploadResponse> {
        callCount++
        lastRequest = request
        lastBatchIdempotencyKey = batchIdempotencyKey

        nextException?.let { throw it }

        return nextResponse ?: Response.success(ContributionUploadResponse(success = true))
    }
}

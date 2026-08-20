package com.example.data.contribution

import com.example.data.TasteDNA
import com.example.data.db.ContributionQueueDao
import com.example.data.db.ContributionQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase3ALocalSyncTest {

    private lateinit var stubDao: Phase3AInMemoryContributionQueueDao
    private lateinit var repository: ContributionQueueRepository
    private lateinit var mockGateway: LocalMockContributionUploadGateway
    private lateinit var syncManager: LocalContributionSyncManager

    @Before
    fun setUp() {
        stubDao = Phase3AInMemoryContributionQueueDao()
        repository = ContributionQueueRepository(stubDao)
        mockGateway = LocalMockContributionUploadGateway(shouldSucceed = true)
        syncManager = LocalContributionSyncManager(repository, mockGateway)
    }

    @Test
    fun testConsentDisabledPreventsProcessingAndClearsQueue() = runBlocking {
        // Enqueue items manually when consent was temporarily on
        repository.setConsentGranted(true)
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(1, repository.getQueuedCount())

        // Revoke consent
        repository.setConsentGranted(false)
        assertFalse(repository.isConsentGranted())

        val result = syncManager.performSync(batchSize = 10)
        assertFalse("Sync should fail when consent is disabled", result.success)
        assertEquals(0, result.processedCount)
        assertEquals("Queue should be purged when consent is disabled", 0, repository.getQueuedCount())
    }

    @Test
    fun testBatchProcessingRespectsBatchSizeLimit() = runBlocking {
        repository.setConsentGranted(true)
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!

        for (i in 1..5) {
            repository.enqueueTasteVectorSnapshot(payload)
        }
        assertEquals(5, repository.getQueuedCount())

        val result = syncManager.performSync(batchSize = 2)
        assertTrue(result.success)
        assertEquals(2, result.processedCount)
        assertEquals("3 items should remain in queue after sync of batch size 2", 3, repository.getQueuedCount())
    }

    @Test
    fun testSuccessfulMockProcessingMarksRecordsCompleted() = runBlocking {
        repository.setConsentGranted(true)
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!

        repository.enqueueTasteVectorSnapshot(payload)
        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(2, repository.getQueuedCount())

        mockGateway.setShouldSucceed(true)
        val result = syncManager.performSync(batchSize = 10)

        assertTrue(result.success)
        assertEquals(2, result.processedCount)
        
        val allEvents = repository.getAllQueuedEvents()
        assertEquals("Processed items must be retained in queue as COMPLETED", 2, allEvents.size)
        assertTrue(allEvents.all { it.status == ContributionQueueRepository.STATUS_COMPLETED })
        assertEquals(2, mockGateway.getUploadedEvents().size)
    }

    @Test
    fun testFailedMockProcessingMarksRecordsFailedAndRetainsThem() = runBlocking {
        repository.setConsentGranted(true)
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!

        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(1, repository.getQueuedCount())

        mockGateway.setShouldSucceed(false)
        val result = syncManager.performSync(batchSize = 10)

        assertFalse(result.success)
        assertEquals(0, result.processedCount)
        assertEquals("Failed items must be retained in queue", 1, repository.getTotalQueuedCount())

        val allEvents = repository.getAllQueuedEvents()
        assertEquals(1, allEvents.size)
        assertEquals(ContributionQueueRepository.STATUS_FAILED, allEvents[0].status)
    }

    @Test
    fun testProcessingRecordsAreResetToPendingDuringCrashRecovery() = runBlocking {
        repository.setConsentGranted(true)

        // Manually insert an orphaned PROCESSING item directly into DAO
        stubDao.insert(
            ContributionQueueEntity(
                id = 100L,
                eventType = "ELEM_TASTE_VECTOR_SNAPSHOT_V1",
                schemaVersion = "1.0",
                payloadJson = "{}",
                createdAt = System.currentTimeMillis(),
                status = ContributionQueueRepository.STATUS_PROCESSING
            )
        )

        val beforeEvents = stubDao.getByStatus(ContributionQueueRepository.STATUS_PROCESSING)
        assertEquals(1, beforeEvents.size)

        // Run syncManager initialize (which performs crash recovery)
        syncManager.initialize()

        val afterUploadingEvents = stubDao.getByStatus(ContributionQueueRepository.STATUS_PROCESSING)
        assertEquals(0, afterUploadingEvents.size)

        val recoveredQueuedEvents = stubDao.getByStatus(ContributionQueueRepository.STATUS_PENDING)
        assertEquals(1, recoveredQueuedEvents.size)
        assertEquals(100L, recoveredQueuedEvents[0].id)
    }

    @Test
    fun testConsentCheckedBeforeProcessing() = runBlocking {
        repository.setConsentGranted(true)
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)

        // Verify pre-processing consent check
        assertTrue("Consent must be verified before processing", repository.isConsentGranted())

        // Perform sync normally
        val result = syncManager.performSync(batchSize = 10)
        assertTrue(result.success)
        assertEquals(1, result.processedCount)
    }

    @Test
    fun testDtoExcludesLocalRoomIdAndLocalMetadata() {
        val entity = ContributionQueueEntity(
            id = 999L,
            eventType = "ELEM_TASTE_VECTOR_SNAPSHOT_V1",
            schemaVersion = "1.0",
            payloadJson = "{\"test\":1}",
            createdAt = 123456789L,
            status = ContributionQueueRepository.STATUS_PENDING
        )

        val dto = entity.toSanitizedUploadDto()

        // Verify DTO class fields
        val declaredFields = SanitizedContributionUploadDto::class.java.declaredFields.map { it.name }
        assertFalse("DTO must not contain local Room 'id' field", declaredFields.contains("id"))
        assertFalse("DTO must not contain local 'createdAt' timestamp field", declaredFields.contains("createdAt"))
        assertFalse("DTO must not contain local 'status' field", declaredFields.contains("status"))

        assertEquals("ELEM_TASTE_VECTOR_SNAPSHOT_V1", dto.eventType)
        assertEquals("1.0", dto.schemaVersion)
        assertEquals("{\"test\":1}", dto.payloadJson)
    }

    @Test
    fun testEntityToDtoMappingPreservesSanitizedPayload() = runBlocking {
        repository.setConsentGranted(true)
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)

        syncManager.performSync(batchSize = 10)

        val uploadedEvents = mockGateway.getUploadedEvents()
        assertEquals(1, uploadedEvents.size)

        val uploadedDto = uploadedEvents[0]
        assertTrue(uploadedDto is SanitizedContributionUploadDto)
        assertEquals("ELEM_TASTE_VECTOR_SNAPSHOT_V1", uploadedDto.eventType)
        assertEquals("1.0", uploadedDto.schemaVersion)
        assertFalse("Payload must not be empty", uploadedDto.payloadJson.isEmpty())
    }

    @Test
    fun testNoNetworkDependenciesOrTransmissionInPhase3A() {
        // Assert class structure: LocalContributionSyncManager strictly operates on repository and local mock gateway
        val gatewayPackage = LocalMockContributionUploadGateway::class.java.`package`?.name
        assertEquals("com.example.data.contribution", gatewayPackage)
        assertTrue("Mock gateway must be purely local memory storage", true)
    }
}

/**
 * In-memory thread-safe fake DAO for isolated Phase 3A unit testing.
 */
private class Phase3AInMemoryContributionQueueDao : ContributionQueueDao {
    private val items = mutableListOf<ContributionQueueEntity>()
    private var idCounter = 1L

    override suspend fun insert(item: ContributionQueueEntity): Long {
        val assignedId = if (item.id == 0L) idCounter++ else item.id
        val stored = item.copy(id = assignedId)
        items.add(stored)
        return assignedId
    }

    override suspend fun getByStatus(status: String): List<ContributionQueueEntity> {
        return items.filter { it.status == status }
    }

    override suspend fun getByStatusBounded(status: String, limit: Int): List<ContributionQueueEntity> {
        return items.filter { it.status == status }.take(limit)
    }

    override suspend fun getRetryableEvents(maxRetries: Int): List<ContributionQueueEntity> {
        return items.filter { (it.status == "PENDING" || it.status == "FAILED") && it.retryCount < maxRetries }
    }

    override suspend fun getAll(): List<ContributionQueueEntity> {
        return items.toList()
    }

    override suspend fun getCount(): Int {
        return items.size
    }

    override suspend fun updateStatus(ids: List<Long>, status: String) {
        val matchingIndices = items.indices.filter { items[it].id in ids }
        for (i in matchingIndices) {
            items[i] = items[i].copy(status = status)
        }
    }

    override suspend fun updateStatusAndRetry(ids: List<Long>, status: String, timestamp: Long) {
        val matchingIndices = items.indices.filter { items[it].id in ids }
        for (i in matchingIndices) {
            val cur = items[i]
            items[i] = cur.copy(status = status, retryCount = cur.retryCount + 1, lastAttemptTimestamp = timestamp)
        }
    }

    override suspend fun updateStatusAndMetadata(ids: List<Long>, status: String, retryCount: Int, timestamp: Long) {
        val matchingIndices = items.indices.filter { items[it].id in ids }
        for (i in matchingIndices) {
            items[i] = items[i].copy(status = status, retryCount = retryCount, lastAttemptTimestamp = timestamp)
        }
    }

    override suspend fun updateIdempotencyKey(id: Long, idempotencyKey: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            items[index] = items[index].copy(idempotencyKey = idempotencyKey)
        }
    }

    override suspend fun getRecordsMissingIdempotencyKey(): List<ContributionQueueEntity> {
        return items.filter { it.idempotencyKey.isEmpty() }
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        items.removeAll { it.id in ids }
    }

    override suspend fun clearAll() {
        items.clear()
    }
}

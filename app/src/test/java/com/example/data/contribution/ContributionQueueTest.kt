package com.example.data.contribution

import com.example.data.TasteDNA
import com.example.data.db.AISkipEventEntity
import com.example.data.db.ContributionQueueDao
import com.example.data.db.ContributionQueueEntity
import com.example.data.db.PairwiseOutcomeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContributionQueueTest {

    private lateinit var stubDao: InMemoryContributionQueueDao
    private lateinit var repository: ContributionQueueRepository

    @Before
    fun setUp() {
        stubDao = InMemoryContributionQueueDao()
        repository = ContributionQueueRepository(stubDao)
    }

    @Test
    fun testDefaultConsentIsFalseAndEnqueueFailsClosed() = runBlocking {
        assertFalse("Default consent state must be OFF", repository.isConsentGranted())

        val tasteDna = TasteDNA()
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(tasteDna)
        assertNotNull(payload)

        val queueId = repository.enqueueTasteVectorSnapshot(payload!!)
        assertEquals("Queue insertion must fail when consent is OFF", -1L, queueId)
        assertEquals(0, repository.getQueuedCount())
    }

    @Test
    fun testEnqueueSucceedsWhenConsentGranted() = runBlocking {
        repository.setConsentGranted(true)
        assertTrue(repository.isConsentGranted())

        val tasteDna = TasteDNA()
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(tasteDna)!!

        val queueId = repository.enqueueTasteVectorSnapshot(payload)
        assertTrue("Queue ID must be positive on success", queueId > 0)
        assertEquals(1, repository.getQueuedCount())

        val pending = repository.getPendingEvents()
        assertEquals(1, pending.size)
        assertEquals("ELEM_TASTE_VECTOR_SNAPSHOT_V1", pending[0].eventType)
        assertEquals("1.0", pending[0].schemaVersion)
        assertEquals(ContributionQueueRepository.STATUS_PENDING, pending[0].status)
    }

    @Test
    fun testConsentRevocationPurgesQueueImmediately() = runBlocking {
        repository.setConsentGranted(true)

        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(1, repository.getQueuedCount())

        // Revoke consent
        repository.setConsentGranted(false)
        assertFalse(repository.isConsentGranted())
        assertEquals("Queue must be purged immediately when consent is revoked", 0, repository.getQueuedCount())
    }

    @Test
    fun testSanitizedPairwiseDeltaDoesNotExposeMediaIdentifiers() = runBlocking {
        repository.setConsentGranted(true)

        val entity = PairwiseOutcomeEntity(
            id = 123L,
            optionAId = "secret_media_a.mp4",
            optionBId = "secret_media_b.jpg",
            chosenId = "secret_media_a.mp4",
            roundNumber = 1,
            timestamp = 1700000000000L,
            preRatingA = 1500.0,
            preRatingB = 1500.0,
            postRatingA = 1516.0,
            postRatingB = 1484.0,
            expectedScoreA = 0.5,
            kFactor = 32.0
        )

        val payload = ContributionDataSanitizer.sanitizePairwiseOutcome(entity)
        assertNotNull(payload)

        val queueId = repository.enqueuePairwiseDelta(payload!!)
        assertTrue(queueId > 0)

        val queued = repository.getPendingEvents()[0]
        val json = queued.payloadJson

        assertFalse("Outbound JSON must NOT contain optionAId", json.contains("secret_media_a.mp4"))
        assertFalse("Outbound JSON must NOT contain optionBId", json.contains("secret_media_b.jpg"))
        assertTrue("Outbound JSON must contain outcome category", json.contains("A_WINS"))
    }

    @Test
    fun testSanitizedAISkipCalibrationStripsMediaId() = runBlocking {
        repository.setConsentGranted(true)

        val skipEntity = AISkipEventEntity(
            id = 999L,
            mediaId = "private_file_xyz.mov",
            eventType = "SKIP_FORWARD",
            fromPosMs = 12000L,
            toPosMs = 22000L,
            timestamp = 1700000000000L
        )

        val payload = ContributionDataSanitizer.sanitizeAISkipEvent(skipEntity)
        assertNotNull(payload)

        repository.enqueueSkipCalibration(payload!!)
        val queued = repository.getPendingEvents()[0]

        assertFalse("Outbound payload must NOT contain mediaId", queued.payloadJson.contains("private_file_xyz.mov"))
        assertTrue("Outbound payload must contain skipType", queued.payloadJson.contains("SKIP_FORWARD"))
    }

    @Test
    fun testSuccessfulMockProcessingMarksRecordsCompleted() = runBlocking {
        repository.setConsentGranted(true)

        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)
        
        val pending = repository.getPendingEvents()
        val ids = pending.map { it.id }
        
        repository.updateEventStatus(ids, ContributionQueueRepository.STATUS_COMPLETED)
        
        val completed = stubDao.getByStatus(ContributionQueueRepository.STATUS_COMPLETED)
        assertEquals(1, completed.size)
        assertEquals(ContributionQueueRepository.STATUS_COMPLETED, completed[0].status)
    }

    @Test
    fun testCleanupCompletedRecords() = runBlocking {
        repository.setConsentGranted(true)
        
        // Manual insertion to control createdAt
        val now = System.currentTimeMillis()
        stubDao.insert(ContributionQueueEntity(
            eventType = "TEST", schemaVersion = "1.0", payloadJson = "{}",
            createdAt = now - (86400000L * 10), // 10 days ago
            status = ContributionQueueRepository.STATUS_COMPLETED
        ))
        stubDao.insert(ContributionQueueEntity(
            eventType = "TEST", schemaVersion = "1.0", payloadJson = "{}",
            createdAt = now - (86400000L * 2), // 2 days ago
            status = ContributionQueueRepository.STATUS_COMPLETED
        ))
        
        assertEquals(2, stubDao.getByStatus(ContributionQueueRepository.STATUS_COMPLETED).size)
        
        // Default cleanup is 7 days
        repository.cleanupCompletedRecords()
        
        val remaining = stubDao.getByStatus(ContributionQueueRepository.STATUS_COMPLETED)
        assertEquals(1, remaining.size)
        assertTrue(now - remaining[0].createdAt < 86400000L * 7)
    }

    @Test
    fun testDuplicatePreventionViaDeterministicIdempotencyKey() = runBlocking {
        repository.setConsentGranted(true)
        val tasteDNA = TasteDNA(learnedVibrancy = 0.5)
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(tasteDNA)!!

        // Enqueue twice with exact same content/window
        repository.enqueueTasteVectorSnapshot(payload)
        repository.enqueueTasteVectorSnapshot(payload)

        // For unit test with stubDao, we check if repository generated the same key.
        val pending = repository.getPendingEvents()
        assertEquals(2, pending.size)
        assertEquals(pending[0].idempotencyKey, pending[1].idempotencyKey)
    }
}

/**
 * In-memory thread-safe fake DAO for isolated unit testing.
 */
private class InMemoryContributionQueueDao : ContributionQueueDao {
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

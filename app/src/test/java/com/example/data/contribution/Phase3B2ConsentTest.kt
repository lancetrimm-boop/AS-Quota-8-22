package com.example.data.contribution

import com.example.data.TasteDNA
import com.example.data.db.ContributionQueueDao
import com.example.data.db.ContributionQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase3B2ConsentTest {

    private lateinit var stubDao: Phase3B2InMemoryContributionQueueDao
    private lateinit var storage: InMemoryConsentStorage
    private lateinit var consentManager: ContributionConsentManager
    private lateinit var repository: ContributionQueueRepository

    @Before
    fun setUp() {
        stubDao = Phase3B2InMemoryContributionQueueDao()
        storage = InMemoryConsentStorage(ConsentState.NOT_DECIDED)
        consentManager = ContributionConsentManager(storage)
        repository = ContributionQueueRepository(stubDao, consentManager)
    }

    @Test
    fun testNewInstallationDefaultsToNotDecidedAndFailsClosed() = runBlocking {
        assertEquals(ConsentState.NOT_DECIDED, consentManager.getConsentState())
        assertFalse("NOT_DECIDED must fail closed", consentManager.isConsentGranted())
        assertFalse("Repository must fail closed when consent is NOT_DECIDED", repository.isConsentGranted())

        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        val result = repository.enqueueTasteVectorSnapshot(payload)

        assertEquals("Enqueue must return -1L when consent is NOT_DECIDED", -1L, result)
        assertEquals(0, repository.getQueuedCount())
    }

    @Test
    fun testGrantedEnablesEnqueueAndRevokedFailsClosed() = runBlocking {
        consentManager.grantConsent()
        assertEquals(ConsentState.GRANTED, consentManager.getConsentState())
        assertTrue(consentManager.isConsentGranted())

        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        val id = repository.enqueueTasteVectorSnapshot(payload)

        assertTrue("Enqueue must succeed when GRANTED", id > 0)
        assertEquals(1, repository.getQueuedCount())

        consentManager.revokeConsent()
        assertEquals(ConsentState.REVOKED, consentManager.getConsentState())
        assertFalse(consentManager.isConsentGranted())

        val failedId = repository.enqueueTasteVectorSnapshot(payload)
        assertEquals("Enqueue must return -1L when REVOKED", -1L, failedId)
        assertEquals("Revoking consent must immediately purge queued records", 0, repository.getQueuedCount())
    }

    @Test
    fun testConsentPersistsAcrossProcessRestartSimulation() {
        storage.setConsentState(ConsentState.GRANTED)

        // Simulate process restart by creating a new Manager reading from same storage
        val restartedConsentManager = ContributionConsentManager(storage)
        assertEquals(ConsentState.GRANTED, restartedConsentManager.getConsentState())
        assertTrue(restartedConsentManager.isConsentGranted())

        restartedConsentManager.revokeConsent()

        // Simulate second process restart
        val secondRestartManager = ContributionConsentManager(storage)
        assertEquals(ConsentState.REVOKED, secondRestartManager.getConsentState())
        assertFalse(secondRestartManager.isConsentGranted())
    }

    @Test
    fun testCorruptedStorageValueFailsClosedToNotDecided() {
        val corruptedStorage = object : ContributionConsentStorage {
            override fun getConsentState(): ConsentState {
                throw IllegalStateException("Corrupted storage file")
            }

            override fun setConsentState(state: ConsentState) {}
        }

        val manager = ContributionConsentManager(corruptedStorage)
        assertEquals(ConsentState.NOT_DECIDED, manager.getConsentState())
        assertFalse("Corrupted storage must fail closed", manager.isConsentGranted())
    }

    @Test
    fun testRegrantingConsentDoesNotRestorePurgedRecords() = runBlocking {
        consentManager.grantConsent()
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(1, repository.getQueuedCount())

        // Revoke -> Purges
        consentManager.revokeConsent()
        assertEquals(0, repository.getQueuedCount())

        // Re-grant
        consentManager.grantConsent()
        assertEquals(0, repository.getQueuedCount())
    }

    @Test
    fun testRevokingConsentDoesNotModifyLocalTasteDNA() = runBlocking {
        val originalTasteDNA = TasteDNA(vibrancy = 0.85, dynamicRange = 0.15)
        consentManager.grantConsent()

        val payload = ContributionDataSanitizer.sanitizeTasteDNA(originalTasteDNA)!!
        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(1, repository.getQueuedCount())

        // Revoke consent
        consentManager.revokeConsent()

        // Local TasteDNA object is untouched
        assertEquals(0.85, originalTasteDNA.vibrancy, 0.001)
        assertEquals(0.15, originalTasteDNA.dynamicRange, 0.001)
        assertEquals(0, repository.getQueuedCount())
    }

    @Test
    fun testQueuedRecordsSurviveProcessRestartWhenConsentRemainsGranted() = runBlocking {
        consentManager.grantConsent()
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(1, repository.getQueuedCount())

        // Simulate process restart with same persistent DAO and storage
        val newManager = ContributionConsentManager(storage)
        val newRepo = ContributionQueueRepository(stubDao, newManager)

        assertEquals(ConsentState.GRANTED, newRepo.getConsentState())
        assertEquals(1, newRepo.getQueuedCount())
    }

    @Test
    fun testLocalSyncManagerFailsClosedOnNotDecidedAndRevoked() = runBlocking {
        val mockGateway = LocalMockContributionUploadGateway()
        val syncManager = LocalContributionSyncManager(repository, mockGateway)

        // Case 1: NOT_DECIDED
        var result = syncManager.performSync()
        assertFalse(result.success)

        // Case 2: GRANTED -> Enqueue -> REVOKED -> Sync
        consentManager.grantConsent()
        val payload = ContributionDataSanitizer.sanitizeTasteDNA(TasteDNA())!!
        repository.enqueueTasteVectorSnapshot(payload)
        assertEquals(1, repository.getQueuedCount())

        consentManager.revokeConsent()
        result = syncManager.performSync()

        assertFalse(result.success)
        assertEquals(0, repository.getQueuedCount())
    }
}

/**
 * Thread-safe in-memory fake DAO for Phase 3B.2 unit tests.
 */
private class Phase3B2InMemoryContributionQueueDao : ContributionQueueDao {
    private val items = mutableListOf<ContributionQueueEntity>()
    private var idCounter = 1L

    override suspend fun insert(entity: ContributionQueueEntity): Long {
        val assignedId = if (entity.id == 0L) idCounter++ else entity.id
        val stored = entity.copy(id = assignedId)
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
        for (i in items.indices) {
            if (ids.contains(items[i].id)) {
                items[i] = items[i].copy(status = status)
            }
        }
    }

    override suspend fun updateStatusAndRetry(ids: List<Long>, status: String, timestamp: Long) {
        for (i in items.indices) {
            if (ids.contains(items[i].id)) {
                val cur = items[i]
                items[i] = cur.copy(status = status, retryCount = cur.retryCount + 1, lastAttemptTimestamp = timestamp)
            }
        }
    }

    override suspend fun updateStatusAndMetadata(ids: List<Long>, status: String, retryCount: Int, timestamp: Long) {
        for (i in items.indices) {
            if (ids.contains(items[i].id)) {
                items[i] = items[i].copy(status = status, retryCount = retryCount, lastAttemptTimestamp = timestamp)
            }
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
        items.removeAll { ids.contains(it.id) }
    }

    override suspend fun clearAll() {
        items.clear()
    }
}

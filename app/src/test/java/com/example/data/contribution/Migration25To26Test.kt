package com.example.data.contribution

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.ContributionQueueDao
import com.example.data.db.ContributionQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Migration25To26Test {

    private lateinit var context: Context
    private val testDbName = "test_migration_25_26.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(testDbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun testRoomMigration25To26_NormalizesPendingToQueuedAndAddsNewColumns() {
        val dbFile = context.getDatabasePath(testDbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create SQLite DB with version 25 schema
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(25) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `contribution_queue` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `eventType` TEXT NOT NULL, 
                            `schemaVersion` TEXT NOT NULL, 
                            `payloadJson` TEXT NOT NULL, 
                            `createdAt` INTEGER NOT NULL, 
                            `status` TEXT NOT NULL
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(config)
        val db = openHelper.writableDatabase

        // 2. Insert legacy rows (one PENDING, one QUEUED)
        db.execSQL("""
            INSERT INTO contribution_queue (eventType, schemaVersion, payloadJson, createdAt, status)
            VALUES ('ELEM_TASTE_VECTOR_SNAPSHOT_V1', '1.0', '{"taste": "test1"}', 1000000, 'PENDING')
        """.trimIndent())

        db.execSQL("""
            INSERT INTO contribution_queue (eventType, schemaVersion, payloadJson, createdAt, status)
            VALUES ('ELEM_PAIRWISE_DELTA_V1', '1.0', '{"delta": "test2"}', 2000000, 'QUEUED')
        """.trimIndent())

        // 3. Execute MIGRATION_25_26
        AuraDatabase.MIGRATION_25_26.migrate(db)

        // 4. Query migrated database to verify schema and data
        val cursor = db.query("SELECT id, eventType, schemaVersion, payloadJson, createdAt, status, idempotencyKey, retryCount, lastAttemptTimestamp FROM contribution_queue ORDER BY id ASC")

        assertTrue(cursor.moveToNext())
        val id1 = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        val eventType1 = cursor.getString(cursor.getColumnIndexOrThrow("eventType"))
        val payload1 = cursor.getString(cursor.getColumnIndexOrThrow("payloadJson"))
        val status1 = cursor.getString(cursor.getColumnIndexOrThrow("status"))
        val key1 = cursor.getString(cursor.getColumnIndexOrThrow("idempotencyKey"))
        val retry1 = cursor.getInt(cursor.getColumnIndexOrThrow("retryCount"))
        val timestamp1 = cursor.getLong(cursor.getColumnIndexOrThrow("lastAttemptTimestamp"))

        assertEquals(1L, id1)
        assertEquals("ELEM_TASTE_VECTOR_SNAPSHOT_V1", eventType1)
        assertEquals("{\"taste\": \"test1\"}", payload1)
        assertEquals("QUEUED", status1) // PENDING normalized to QUEUED
        assertEquals("", key1) // Initially empty before Kotlin backfill
        assertEquals(0, retry1)
        assertEquals(0L, timestamp1)

        assertTrue(cursor.moveToNext())
        val id2 = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        val status2 = cursor.getString(cursor.getColumnIndexOrThrow("status"))

        assertEquals(2L, id2)
        assertEquals("QUEUED", status2)

        assertFalse(cursor.moveToNext())
        cursor.close()
        db.close()
    }

    @Test
    fun testIdempotencyKeyBackfill_GeneratesUniqueUuidV4PerRecord() = runBlocking {
        val fakeDao = TestMigrationFakeDao()
        val repository = ContributionQueueRepository(fakeDao)

        // Insert records with empty idempotency keys
        fakeDao.insert(
            ContributionQueueEntity(
                id = 101L,
                eventType = "ELEM_TASTE_VECTOR_SNAPSHOT_V1",
                schemaVersion = "1.0",
                payloadJson = "{}",
                createdAt = 1000L,
                status = ContributionQueueRepository.STATUS_PENDING,
                idempotencyKey = ""
            )
        )
        fakeDao.insert(
            ContributionQueueEntity(
                id = 102L,
                eventType = "ELEM_PAIRWISE_DELTA_V1",
                schemaVersion = "1.0",
                payloadJson = "{}",
                createdAt = 2000L,
                status = ContributionQueueRepository.STATUS_PENDING,
                idempotencyKey = ""
            )
        )

        // Perform backfill
        repository.backfillMissingIdempotencyKeys()

        val allEvents = fakeDao.getAll()
        assertEquals(2, allEvents.size)

        val key1 = allEvents[0].idempotencyKey
        val key2 = allEvents[1].idempotencyKey

        assertTrue("Idempotency key 1 must not be empty", key1.isNotEmpty())
        assertTrue("Idempotency key 2 must not be empty", key2.isNotEmpty())
        assertNotEquals("Idempotency keys must be unique", key1, key2)

        // Verify UUID v4 format
        assertTrue("Key 1 must be UUID format", key1.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))
        assertTrue("Key 2 must be UUID format", key2.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")))
    }

    @Test
    fun testQueueLifecycle_StateTransitionsAndRetryMetadata() = runBlocking {
        val fakeDao = TestMigrationFakeDao()
        val repository = ContributionQueueRepository(fakeDao)
        repository.setConsentGranted(true)

        val payload = ContributionDataSanitizer.sanitizeTelemetryEvent("CLICK", "PREFERENCE")!!
        val queueId = repository.enqueueRecommendationFeedback(payload)
        assertTrue(queueId > 0)

        // 1. Initial status PENDING
        val pending = repository.getPendingEvents()
        assertEquals(1, pending.size)
        assertEquals(ContributionQueueRepository.STATUS_PENDING, pending[0].status)
        assertEquals(0, pending[0].retryCount)
        assertEquals(0L, pending[0].lastAttemptTimestamp)

        // 2. Transition to PROCESSING
        repository.updateEventStatus(listOf(queueId), ContributionQueueRepository.STATUS_PROCESSING)
        val uploading = fakeDao.getByStatus(ContributionQueueRepository.STATUS_PROCESSING)
        assertEquals(1, uploading.size)

        // 3. Update retry metadata on failure
        val now = System.currentTimeMillis()
        repository.updateEventRetryMetadata(listOf(queueId), ContributionQueueRepository.STATUS_FAILED, now)
        val failed = fakeDao.getByStatus(ContributionQueueRepository.STATUS_FAILED)
        assertEquals(1, failed.size)
        assertEquals(1, failed[0].retryCount)
        assertEquals(now, failed[0].lastAttemptTimestamp)

        // 4. Stale/Crash recovery: reset PROCESSING to PENDING
        repository.updateEventStatus(listOf(queueId), ContributionQueueRepository.STATUS_PROCESSING)
        repository.resetProcessingStatus()
        val recovered = repository.getPendingEvents()
        assertEquals(1, recovered.size)
        assertEquals(ContributionQueueRepository.STATUS_PENDING, recovered[0].status)

        // 5. Successful deletion
        repository.deleteEvents(listOf(queueId))
        assertEquals(0, repository.getQueuedCount())
    }

    @Test
    fun testConsentArchitecturePreservation() = runBlocking {
        val fakeDao = TestMigrationFakeDao()
        val repository = ContributionQueueRepository(fakeDao)

        // Default state NOT_DECIDED (isConsentGranted == false)
        assertFalse(repository.isConsentGranted())
        val payload = ContributionDataSanitizer.sanitizeTelemetryEvent("CLICK", "PREFERENCE")!!

        val queueId = repository.enqueueRecommendationFeedback(payload)
        assertEquals(-1L, queueId)
        assertEquals(0, repository.getQueuedCount())

        // Grant consent
        repository.setConsentGranted(true)
        assertTrue(repository.isConsentGranted())
        val validId = repository.enqueueRecommendationFeedback(payload)
        assertTrue(validId > 0)
        assertEquals(1, repository.getQueuedCount())

        // Revoke consent purges queue
        repository.setConsentGranted(false)
        assertFalse(repository.isConsentGranted())
        assertEquals(0, repository.getQueuedCount())
    }
}

private class TestMigrationFakeDao : ContributionQueueDao {
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

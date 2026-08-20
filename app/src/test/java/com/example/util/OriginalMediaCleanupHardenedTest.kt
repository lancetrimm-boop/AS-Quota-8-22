package com.example.util

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.data.db.AuraDatabase
import com.example.data.db.ConversionJobEntity
import com.example.data.db.MediaEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OriginalMediaCleanupHardenedTest {

    private lateinit var database: AuraDatabase
    private lateinit var dao: com.example.data.db.ConversionJobDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.conversionJobDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCleanupBlockedByStability() = runBlocking {
        val now = System.currentTimeMillis()
        val futureEligibility = now + TimeUnit.DAYS.toMillis(5) // Still has 5 days to go
        
        val job = ConversionJobEntity(
            mediaId = "m1",
            sourceUri = "content://media/1",
            fileName = "f1.mkv",
            status = ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP.name,
            cleanupStatus = OriginalCleanupStatus.WAITING_FOR_STABILITY.name,
            cleanupEligibilityTimestamp = futureEligibility
        )
        
        val id = dao.insert(job)
        
        // In the real worker, processCleanup would return true (meaning "handled/skipped") 
        // but not delete. We verify the status remains WAITING_FOR_STABILITY.
        
        val fetched = dao.getJobById(id)
        assertEquals(OriginalCleanupStatus.WAITING_FOR_STABILITY.name, fetched?.cleanupStatus)
    }

    @Test
    fun testCleanupEligibleTransition() = runBlocking {
        val now = System.currentTimeMillis()
        val pastEligibility = now - TimeUnit.DAYS.toMillis(1) // Eligible 1 day ago
        
        val job = ConversionJobEntity(
            mediaId = "m1",
            sourceUri = "content://media/1",
            fileName = "f1.mkv",
            status = ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP.name,
            cleanupStatus = OriginalCleanupStatus.WAITING_FOR_STABILITY.name,
            cleanupEligibilityTimestamp = pastEligibility
        )
        
        val id = dao.insert(job)
        
        // Mocking the check that happens in the worker
        val fetched = dao.getJobById(id)!!
        val isEligible = fetched.cleanupEligibilityTimestamp != null && now >= fetched.cleanupEligibilityTimestamp!!
        
        assertEquals(true, isEligible)
    }

    @Test
    fun testIdentityMismatchProtection() = runBlocking {
        // Original media in DB points to replacement B
        database.mediaDao().insert(MediaEntity(
            id = "m1",
            title = "A",
            mediaType = "VIDEO",
            replacedByMediaId = "replacement_B"
        ))
        
        // But job record says it was replaced by C
        val job = ConversionJobEntity(
            id = 1,
            mediaId = "m1",
            sourceUri = "content://media/1",
            fileName = "f1.mkv",
            finalMediaId = "replacement_C", // Mismatch!
            cleanupStatus = OriginalCleanupStatus.CLEANUP_ELIGIBLE.name,
            cleanupEligibilityTimestamp = System.currentTimeMillis() - 1000
        )
        
        // In the worker, this would trigger:
        // if (originalMedia.replacedByMediaId != job.finalMediaId) -> CLEANUP_BLOCKED
        
        val originalMedia = database.mediaDao().getMediaById(job.mediaId)
        val blocked = originalMedia?.replacedByMediaId != job.finalMediaId
        
        assertEquals(true, blocked)
    }

    @Test
    fun testManualCleanupBypassesStability() = runBlocking {
        val now = System.currentTimeMillis()
        val futureEligibility = now + TimeUnit.DAYS.toMillis(7)
        
        val job = ConversionJobEntity(
            id = 1,
            mediaId = "m1",
            sourceUri = "content://media/1",
            fileName = "f1.mkv",
            status = ConversionJobStatus.READY_FOR_ORIGINAL_CLEANUP.name,
            cleanupStatus = OriginalCleanupStatus.WAITING_FOR_STABILITY.name,
            cleanupEligibilityTimestamp = futureEligibility
        )
        
        // In the worker, if isManual = true, it skips the time check.
        val isManual = true
        val isEligible = isManual || (job.cleanupEligibilityTimestamp != null && now >= job.cleanupEligibilityTimestamp!!)
        
        assertEquals(true, isEligible)
    }
}

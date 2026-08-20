package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.ConversionJobEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConversionQueueTest {

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
    fun testInsertAndObserveJobs() = runBlocking {
        val job = ConversionJobEntity(
            mediaId = "m1",
            sourceUri = "uri1",
            fileName = "file1.mkv",
            status = ConversionJobStatus.QUEUED.name
        )
        
        dao.insert(job)
        
        val jobs = dao.observeAllJobs().first()
        assertEquals(1, jobs.size)
        assertEquals("m1", jobs[0].mediaId)
        assertEquals("QUEUED", jobs[0].status)
    }

    @Test
    fun testUpdateProgressAndStatus() = runBlocking {
        val id = dao.insert(ConversionJobEntity(
            mediaId = "m1",
            sourceUri = "uri1",
            fileName = "file1.mkv",
            status = ConversionJobStatus.QUEUED.name
        ))
        
        dao.updateStatus(id, ConversionJobStatus.CONVERTING.name)
        dao.updateProgress(id, 45)
        
        val updated = dao.getJobById(id)
        assertNotNull(updated)
        assertEquals("CONVERTING", updated?.status)
        assertEquals(45, updated?.progress)
    }

    @Test
    fun testObserveActiveJobs() = runBlocking {
        dao.insert(ConversionJobEntity(mediaId = "m1", sourceUri = "u1", fileName = "f1", status = "QUEUED"))
        dao.insert(ConversionJobEntity(mediaId = "m2", sourceUri = "u2", fileName = "f2", status = "COMPLETED"))
        
        val active = dao.observeActiveJobs().first()
        assertEquals(1, active.size)
        assertEquals("m1", active[0].mediaId)
    }
}

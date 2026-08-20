package com.example.data

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.PlaybackErrorLogDao
import com.example.data.db.PlaybackErrorLogEntity
import com.example.util.AuraPlaybackDiagnostics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class PlaybackErrorLogTest {

    private lateinit var database: AuraDatabase
    private lateinit var dao: PlaybackErrorLogDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.playbackErrorLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveError() = runBlocking {
        val error = PlaybackErrorLogEntity(
            mediaItemId = "test_id",
            mediaUri = "content://test",
            mediaTitle = "Test Video",
            fileName = "test.mp4",
            mimeType = "video/mp4",
            isLocalFile = true,
            diagnosticSummary = "Test Summary",
            errorCode = 1000,
            exceptionClass = "TestException",
            sessionId = "session_1"
        )
        dao.insert(error)

        val logs = dao.observeRecentErrors().first()
        assertEquals(1, logs.size)
        assertEquals("test_id", logs[0].mediaItemId)
        assertEquals("Test Summary", logs[0].diagnosticSummary)
    }

    @Test
    fun testDeduplication() = runBlocking {
        val repository = PlaybackErrorLogRepository(dao)
        
        val error1 = PlaybackErrorLogEntity(
            mediaItemId = "m1",
            errorCode = 1001,
            exceptionClass = "Error",
            sessionId = "s1",
            diagnosticSummary = "Sum 1"
        )
        
        repository.recordError(error1)
        delay(100)
        repository.recordError(error1)
        
        delay(200) // wait for coroutine
        val logs = dao.observeRecentErrors().first()
        assertEquals("Should deduplicate identical errors in same session", 1, logs.size)
        assertEquals(2, logs[0].occurrenceCount)
    }

    @Test
    fun testDistinctErrors() = runBlocking {
        val repository = PlaybackErrorLogRepository(dao)
        
        repository.recordError(PlaybackErrorLogEntity(mediaItemId = "m1", errorCode = 1, sessionId = "s1"))
        repository.recordError(PlaybackErrorLogEntity(mediaItemId = "m2", errorCode = 1, sessionId = "s1"))
        
        delay(200)
        val logs = dao.observeRecentErrors().first()
        assertEquals("Different media IDs should be distinct", 2, logs.size)
    }

    @Test
    fun testDeleteError() = runBlocking {
        val error = PlaybackErrorLogEntity(mediaItemId = "to_delete", isLocalFile = true)
        dao.insert(error)
        
        var logs = dao.observeRecentErrors().first()
        assertEquals(1, logs.size)
        val id = logs[0].id

        dao.delete(id)
        logs = dao.observeRecentErrors().first()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun testDeleteAll() = runBlocking {
        dao.insert(PlaybackErrorLogEntity(mediaItemId = "1", isLocalFile = true))
        dao.insert(PlaybackErrorLogEntity(mediaItemId = "2", isLocalFile = true))
        
        assertEquals(2, dao.observeRecentErrors().first().size)
        
        dao.deleteAll()
        assertTrue(dao.observeRecentErrors().first().isEmpty())
    }

    @Test
    fun testQueryByMedia() = runBlocking {
        dao.insert(PlaybackErrorLogEntity(mediaItemId = "media_a", isLocalFile = true))
        dao.insert(PlaybackErrorLogEntity(mediaItemId = "media_b", isLocalFile = true))
        
        val logsA = dao.getErrorsForMedia("media_a").first()
        assertEquals(1, logsA.size)
        assertEquals("media_a", logsA[0].mediaItemId)
    }

    @Test
    fun testQueryBySession() = runBlocking {
        dao.insert(PlaybackErrorLogEntity(mediaItemId = "m1", sessionId = "session_1", isLocalFile = true))
        dao.insert(PlaybackErrorLogEntity(mediaItemId = "m2", sessionId = "session_2", isLocalFile = true))
        
        val logs1 = dao.getErrorsForSession("session_1").first()
        assertEquals(1, logs1.size)
        assertEquals("session_1", logs1[0].sessionId)
    }

    @Test
    fun testCausalChainExtraction() {
        val root = IOException("Root cause")
        val nested = RuntimeException("Nested error", root)
        val playbackException = PlaybackException("Top error", nested, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        
        val mockPlayer: Player = mock()
        whenever(mockPlayer.playbackState).thenReturn(Player.STATE_IDLE)
        
        val entity = AuraPlaybackDiagnostics.captureError(
            error = playbackException,
            player = mockPlayer,
            mediaItem = null,
            sessionId = "test_session"
        )
        
        assertTrue(entity.causeChain?.contains("IOException: Root cause") == true)
        assertTrue(entity.causeChain?.contains("RuntimeException: Nested error") == true)
        assertTrue(entity.causeChain?.contains("PlaybackException: Top error") == true)
    }

    @Test
    fun testTrimLog() = runBlocking {
        for (i in 1..10) {
            dao.insert(PlaybackErrorLogEntity(mediaItemId = "media_$i", isLocalFile = true))
        }
        
        assertEquals(10, dao.observeRecentErrors().first().size)
        
        dao.trimLog(5)
        val trimmedLogs = dao.observeRecentErrors().first()
        assertEquals(5, trimmedLogs.size)
        // Should keep newest
        assertEquals("media_10", trimmedLogs[0].mediaItemId)
        assertEquals("media_6", trimmedLogs[4].mediaItemId)
    }
}

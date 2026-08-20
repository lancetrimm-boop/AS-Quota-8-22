package com.example.data

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AuraDatabase
import com.example.data.db.PlaybackErrorLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackErrorIntegrationTest {

    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository
    private lateinit var errorRepo: PlaybackErrorLogRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        repository = MediaRepository.instance
        repository.setDatabaseForTesting(database)
        repository.initDatabase(context)
        
        // Wait for repository initialization
        runBlocking {
            while (repository.playbackErrorLogRepository == null) {
                kotlinx.coroutines.delay(10)
            }
            errorRepo = repository.playbackErrorLogRepository!!
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testEndToEndErrorReporting() = runBlocking {
        val mediaItem = MediaItem(id = "error_media", title = "Corrupt Video", mediaType = "VIDEO", uriPath = "local://corrupt.mp4")
        val playbackException = PlaybackException("Source error", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        
        val mockPlayer: Player = mock()
        whenever(mockPlayer.playbackState).thenReturn(Player.STATE_IDLE)
        whenever(mockPlayer.currentPosition).thenReturn(5000L)
        
        repository.recordPlaybackError(playbackException, mockPlayer, mediaItem)
        
        // Repository uses background scope, so wait a bit
        var logs: List<PlaybackErrorLogEntity> = emptyList()
        for (i in 1..10) {
            logs = errorRepo.observeRecentErrors().first()
            if (logs.isNotEmpty()) break
            kotlinx.coroutines.delay(100)
        }
        
        assertEquals(1, logs.size)
        val log = logs[0]
        assertEquals("error_media", log.mediaItemId)
        assertEquals("Corrupt Video", log.mediaTitle)
        assertEquals(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, log.errorCode)
        assertEquals(5000L, log.playbackPositionMs)
        assertEquals("Media file not found or inaccessible", log.diagnosticSummary)
    }
}

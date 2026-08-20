package com.example

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.PlaylistState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuraPhase2PlaybackLifecycleTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createMediaItem(id: String): MediaItem {
        return MediaItem(
            id = id,
            title = "Media $id",
            mediaType = "VIDEO",
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testPlaybackSessionPersistence() = runTest(testDispatcher) {
        val items = listOf(createMediaItem("1"), createMediaItem("2"))
        repository.setPlaylist(items, 0, "Test")
        
        // Simulate backgrounding
        repository.updatePlaybackPosition(5000L)
        repository.setResumingFromBackground(true)
        
        assertEquals(5000L, repository.lastPlaybackPositionMs)
        assertTrue(repository.isResumingFromBackground)
        assertEquals("1", repository.activePlaylist.value?.currentItem?.id)
    }

    @Test
    fun testSessionClearingOnBack() = runTest(testDispatcher) {
        val items = listOf(createMediaItem("1"))
        repository.setPlaylist(items, 0, "Test")
        repository.updatePlaybackPosition(5000L)
        repository.setResumingFromBackground(true)
        
        // Simulate Back button (clearPlaylist)
        repository.clearPlaylist()
        
        assertEquals(0L, repository.lastPlaybackPositionMs)
        assertFalse(repository.isResumingFromBackground)
        assertEquals(null, repository.activePlaylist.value)
    }

    @Test
    fun testNewSelectionResetsSession() = runTest(testDispatcher) {
        val items1 = listOf(createMediaItem("1"))
        repository.setPlaylist(items1, 0, "Test 1")
        repository.updatePlaybackPosition(5000L)
        repository.setResumingFromBackground(true)
        
        // Select new media
        val items2 = listOf(createMediaItem("2"))
        repository.setPlaylist(items2, 0, "Test 2")
        
        assertEquals(0L, repository.lastPlaybackPositionMs)
        assertFalse(repository.isResumingFromBackground)
        assertEquals("2", repository.activePlaylist.value?.currentItem?.id)
    }
}

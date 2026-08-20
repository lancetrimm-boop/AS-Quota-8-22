package com.example

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuraNavigationFixTest {

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
    fun testPlayerActiveOnSelection() = runTest(testDispatcher) {
        val items = listOf(createMediaItem("1"), createMediaItem("2"))
        
        assertFalse("Player should be inactive initially", repository.isPlayerActive.value)
        
        repository.setPlaylist(items, 0, "Library")
        
        assertTrue("Player should be active after selection", repository.isPlayerActive.value)
        assertEquals("1", repository.activePlaylist.value?.currentItem?.id)
    }

    @Test
    fun testPlayerInactiveOnClear() = runTest(testDispatcher) {
        val items = listOf(createMediaItem("1"))
        repository.setPlaylist(items, 0, "Library")
        assertTrue(repository.isPlayerActive.value)
        
        repository.clearPlaylist()
        
        assertFalse("Player should be inactive after clearPlaylist", repository.isPlayerActive.value)
        assertEquals(null, repository.activePlaylist.value)
    }

    @Test
    fun testPlayerStaysActiveOnDeletionWithRemainingItems() = runTest(testDispatcher) {
        val items = listOf(createMediaItem("1"), createMediaItem("2"))
        repository.setMediaItemsForTesting(items)
        repository.setPlaylist(items, 0, "Library")
        assertTrue(repository.isPlayerActive.value)
        
        // Delete current item
        repository.deleteMediaItem("1")
        
        assertTrue("Player should STAY active after deletion if items remain", repository.isPlayerActive.value)
        assertEquals("2", repository.activePlaylist.value?.currentItem?.id)
    }

    @Test
    fun testPlayerInactivatesOnLastItemDeletion() = runTest(testDispatcher) {
        val items = listOf(createMediaItem("1"))
        repository.setMediaItemsForTesting(items)
        repository.setPlaylist(items, 0, "Library")
        assertTrue(repository.isPlayerActive.value)
        
        // Delete the only item
        repository.deleteMediaItem("1")
        
        assertFalse("Player should become inactive if playlist becomes empty after deletion", repository.isPlayerActive.value)
        assertEquals(null, repository.activePlaylist.value)
    }

    @Test
    fun testIntentDecoupledFromPlaylistAfterClear() = runTest(testDispatcher) {
        val items = listOf(createMediaItem("1"), createMediaItem("2"))
        repository.setMediaItemsForTesting(items)
        repository.setPlaylist(items, 0, "Library")
        
        // Delete item 1 -> Advances to item 2
        repository.deleteMediaItem("1")
        assertTrue("Player active after deletion", repository.isPlayerActive.value)
        assertEquals("2", repository.activePlaylist.value?.currentItem?.id)
        
        // Press Back (clearPlaylist)
        repository.clearPlaylist()
        assertFalse("Player inactive after Back", repository.isPlayerActive.value)
        
        // Verify that even if we call some repository update, player stays closed
        // (Simulating why we need the flag)
        repository.selectPlaylistItem(0) // Does nothing since _activePlaylist is null
        assertFalse("Player stays inactive even if other state changes", repository.isPlayerActive.value)
    }
}

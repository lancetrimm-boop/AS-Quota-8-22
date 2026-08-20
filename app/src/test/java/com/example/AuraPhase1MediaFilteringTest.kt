package com.example

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.StandardSortOption
import com.example.data.IntelligentSortOption
import com.example.data.SortCategory
import com.example.data.TasteDNA
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuraPhase1MediaFilteringTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createMediaItem(id: String, status: CompatibilityStatus, isDeleted: Boolean = false): MediaItem {
        return MediaItem(
            id = id,
            title = "Media $id",
            mediaType = "VIDEO",
            compatibilityStatus = status,
            isDeleted = isDeleted
        )
    }

    @Test
    fun testMediaValidityFiltering() {
        val items = listOf(
            createMediaItem("1", CompatibilityStatus.PLAYABLE),
            createMediaItem("2", CompatibilityStatus.CORRUPT),
            createMediaItem("3", CompatibilityStatus.UNSUPPORTED),
            createMediaItem("4", CompatibilityStatus.DELETED),
            createMediaItem("5", CompatibilityStatus.UNTESTED),
            createMediaItem("6", CompatibilityStatus.NEEDS_TRANSCODE),
            createMediaItem("7", CompatibilityStatus.PLAYABLE, isDeleted = true)
        )

        val filtered = repository.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.STANDARD,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.PERSONALIZED,
            inputItems = items,
            tasteDNA = TasteDNA()
        )

        // Should exclude: 2 (CORRUPT), 3 (UNSUPPORTED), 4 (DELETED), 5 (UNTESTED), 7 (isDeleted=true)
        // Should preserve: 1 (PLAYABLE), 6 (NEEDS_TRANSCODE)
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == "1" })
        assertTrue(filtered.any { it.id == "6" })
        assertFalse(filtered.any { it.id == "2" })
        assertFalse(filtered.any { it.id == "3" })
        assertFalse(filtered.any { it.id == "4" })
        assertFalse(filtered.any { it.id == "5" })
        assertFalse(filtered.any { it.id == "7" })
    }

    @Test
    fun testPlaylistSanitization() = runTest(testDispatcher) {
        val items = listOf(
            createMediaItem("1", CompatibilityStatus.PLAYABLE),
            createMediaItem("2", CompatibilityStatus.CORRUPT), // Should be removed
            createMediaItem("3", CompatibilityStatus.PLAYABLE)
        )

        // Start at item "3" (index 2)
        repository.setPlaylist(items, 2, "Test Playlist")

        val playlist = repository.activePlaylist.value
        assertNotNull(playlist)
        assertEquals(2, playlist?.items?.size) // Item "2" removed
        assertEquals("1", playlist?.items?.get(0)?.id)
        assertEquals("3", playlist?.items?.get(1)?.id)
        
        // Index should be corrected from 2 to 1 because item "2" was removed
        assertEquals(1, playlist?.currentIndex)
    }

    @Test
    fun testPlaylistSanitizationSelectionFilteredOut() = runTest(testDispatcher) {
        val items = listOf(
            createMediaItem("1", CompatibilityStatus.PLAYABLE),
            createMediaItem("2", CompatibilityStatus.CORRUPT), // Selected but filtered out
            createMediaItem("3", CompatibilityStatus.PLAYABLE)
        )

        // Start at item "2" (index 1) which is invalid
        repository.setPlaylist(items, 1, "Test Playlist")

        val playlist = repository.activePlaylist.value
        assertNotNull(playlist)
        assertEquals(2, playlist?.items?.size)
        // Selected item "2" was filtered out, should default to 0
        assertEquals(0, playlist?.currentIndex)
        assertEquals("1", playlist?.items?.get(0)?.id)
    }
}

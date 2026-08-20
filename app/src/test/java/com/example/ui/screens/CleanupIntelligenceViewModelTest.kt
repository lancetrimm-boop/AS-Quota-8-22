package com.example.ui.screens

import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.cleanup.CleanupCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class CleanupIntelligenceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MediaRepository
    private lateinit var viewModel: CleanupIntelligenceViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = MediaRepository(testDispatcher)
        
        // Mock media items
        val items = listOf(
            MediaItem(id = "1", title = "Protected", mediaType = "PHOTO", isFavorite = true, sizeBytes = 1000L),
            MediaItem(id = "2", title = "Forgotten", mediaType = "PHOTO", exposureCount = 50, viewCount = 0, sizeBytes = 5000L),
            MediaItem(id = "3", title = "SpaceHog", mediaType = "VIDEO", sizeBytes = 200 * 1024 * 1024L, viewCount = 0)
        )
        repository.setMediaItemsForTesting(items)
        
        viewModel = CleanupIntelligenceViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModel_InitialLoad_GeneratesStats() = runTest {
        // Advance time to allow internal launch to complete
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.totalRecommendations > 0)
        assertEquals(1, state.forgottenCount)
        assertEquals(5000L + 200 * 1024 * 1024L, state.potentialStorageRecovery)
    }

    @Test
    fun testViewModel_SortByImpact_ReordersItems() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        viewModel.updateSort(CleanupSort.LARGEST_STORAGE_IMPACT)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals("3", state.lowestScoreItems[0].mediaId) // Space hog has largest impact
    }

    @Test
    fun testViewModel_HighValueProtection_ExcludesFromCleanup() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        // Verify protected item is in highestScoreItems list
        assertTrue(state.highestScoreItems.any { it.id == "1" })
        // Verify protected item is NOT in cleanup recommendations
        assertFalse(state.lowestScoreItems.any { it.mediaId == "1" })
    }
}

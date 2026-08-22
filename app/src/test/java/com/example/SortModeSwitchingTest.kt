package com.example

import com.example.data.*
import com.example.ui.models.LibraryItemUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SortModeSwitchingTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        repository = MediaRepository(dispatcher = testDispatcher)
        // Set database state to READY
        val stateField = MediaRepository::class.java.getDeclaredField("_databaseState")
        stateField.isAccessible = true
        (stateField.get(repository) as kotlinx.coroutines.flow.MutableStateFlow<DatabaseState>).value = DatabaseState.READY
    }

    @Test
    fun testSwitchingModesEmitsNewStateEvenIfItemsIdentical() = runTest(testDispatcher) {
        val item1 = MediaItem(id = "1", title = "Item 1", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
        repository.setMediaItemsForTesting(listOf(item1))

        // Collect the flow to ensure it's active and recording emissions
        val results = mutableListOf<List<LibraryItemUi>>()
        val job = launch {
            repository.latestAiSortRecommendation.collect {
                results.add(it)
            }
        }

        // 1. Initial Mode: Personalized
        repository.sortCategory = SortCategory.INTELLIGENT
        repository.intelligentSort = IntelligentSortOption.PERSONALIZED
        advanceUntilIdle()
        
        val firstResults = results.lastOrNull() ?: repository.latestAiSortRecommendation.value
        assertEquals(1, firstResults.size)
        assertEquals("1", firstResults[0].id)

        // 2. Switch to Surprise Me
        // Even though results contain the same item, the emission should happen
        // because we updated the mode-aware identity in the Flow pipeline.
        repository.intelligentSort = IntelligentSortOption.SURPRISE_ME
        advanceUntilIdle()

        val secondResults = results.lastOrNull() ?: repository.latestAiSortRecommendation.value
        assertEquals(1, secondResults.size)
        // Note: In our implementation, selectionReason should be different
        assertNotEquals("Reasons should differ between modes", 
            firstResults[0].selectionReason, secondResults[0].selectionReason)
            
        job.cancel()
    }
}

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
    fun testSwitchingFromSurpriseToTitleAscClearsLabelsAndChangesOrder() = runTest(testDispatcher) {
        val itemA = MediaItem(id = "a", title = "B-Item", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
        val itemB = MediaItem(id = "b", title = "A-Item", mediaType = "PHOTO", compatibilityStatus = CompatibilityStatus.PLAYABLE)
        repository.setMediaItemsForTesting(listOf(itemA, itemB))

        // 1. Set to Surprise Me
        repository.sortCategory = SortCategory.INTELLIGENT
        repository.intelligentSort = IntelligentSortOption.SURPRISE_ME
        advanceUntilIdle()
        
        val surpriseResults = repository.latestAiSortRecommendation.value
        assertTrue(surpriseResults.any { it.selectionReason == "SURPRISE!" })

        // 2. Switch to Standard Title A-Z
        repository.sortCategory = SortCategory.STANDARD
        repository.standardSort = StandardSortOption.TITLE_ASC
        advanceUntilIdle()

        val standardResults = repository.latestAiSortRecommendation.value
        assertEquals("A-Item", standardResults[0].title)
        assertEquals("B-Item", standardResults[1].title)
        assertNull("Labels should be cleared in standard sort", standardResults[0].selectionReason)
        assertNull("Labels should be cleared in standard sort", standardResults[1].selectionReason)
    }

    @Test
    fun testSystemStatusPreservedInStandardSort() = runTest(testDispatcher) {
        val item1 = MediaItem(
            id = "1", 
            title = "Failed Item", 
            mediaType = "PHOTO", 
            compatibilityStatus = CompatibilityStatus.ANALYSIS_FAILED // This triggers "Retry Analysis" reason
        )
        repository.setMediaItemsForTesting(listOf(item1))

        repository.sortCategory = SortCategory.STANDARD
        repository.standardSort = StandardSortOption.NEWEST_FIRST
        advanceUntilIdle()

        val results = repository.latestAiSortRecommendation.value
        assertEquals("RETRY ANALYSIS", results[0].selectionReason?.uppercase())
    }

    @Test
    fun testFavoriteWhileSurpriseDoesNotPersistSurpriseLabel() = runTest(testDispatcher) {
        val item = MediaItem(
            id = "1", 
            title = "Item 1", 
            mediaType = "PHOTO", 
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            selectionReason = "Surprise!"
        )
        
        // Use reflection to call the private toEntity() method
        val toEntityMethod = MediaRepository::class.java.getDeclaredMethod("toEntity", MediaItem::class.java)
        toEntityMethod.isAccessible = true
        
        val entity = toEntityMethod.invoke(repository, item) as com.example.data.db.MediaEntity
        
        assertNull("Surprise! label should be stripped before persistence", entity.selectionReason)
    }

    @Test
    fun testSystemStatusPreservedInPersistence() = runTest(testDispatcher) {
        val item = MediaItem(
            id = "1", 
            title = "Item 1", 
            mediaType = "PHOTO", 
            compatibilityStatus = CompatibilityStatus.ANALYSIS_FAILED,
            selectionReason = "Retry Analysis"
        )
        
        val toEntityMethod = MediaRepository::class.java.getDeclaredMethod("toEntity", MediaItem::class.java)
        toEntityMethod.isAccessible = true
        
        val entity = toEntityMethod.invoke(repository, item) as com.example.data.db.MediaEntity
        
        assertEquals("Retry Analysis", entity.selectionReason)
    }
}

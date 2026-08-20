package com.example.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class CompareSelectionSessionTest {

    private lateinit var repository: MediaRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createMediaItem(id: String, title: String = "Item $id", mediaType: String = "PHOTO", sizeBytes: Long = 1000L, dateAdded: Long = System.currentTimeMillis(), rating: Float = 0f, eloRating: Double = 1500.0): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = mediaType,
            year = 2024,
            duration = "",
            genre = "Media",
            compatibilityStatus = CompatibilityStatus.PLAYABLE,
            dateAdded = dateAdded,
            sizeBytes = sizeBytes,
            rating = rating,
            eloRating = eloRating
        )
    }

    @Test
    fun testSelectionThreshold() = runTest {
        val allItems = (1..10).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(allItems)

        // 1 item
        repository.startCompareSelectionSession(setOf("1"))
        assertFalse("Session should be disabled for 1 item", repository.compareSelectionSession.value.isActive)

        // 2 items
        repository.startCompareSelectionSession(setOf("1", "2"))
        assertFalse("Session should be disabled for 2 items", repository.compareSelectionSession.value.isActive)

        // 3 items
        repository.startCompareSelectionSession(setOf("1", "2", "3"))
        assertFalse("Session should be disabled for 3 items", repository.compareSelectionSession.value.isActive)

        // 4 items
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        assertTrue("Session should be enabled for 4 items", repository.compareSelectionSession.value.isActive)
        repository.exitCompareSelectionSession()

        // 5 items
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4", "5"))
        assertTrue("Session should be enabled for 5+ items", repository.compareSelectionSession.value.isActive)
    }

    @Test
    fun testCandidateIsolation() = runTest {
        val allItems = listOf(
            createMediaItem("1"),
            createMediaItem("2"),
            createMediaItem("3"),
            createMediaItem("4"),
            createMediaItem("5")
        )
        repository.setMediaItemsForTesting(allItems)
        
        val selectedIds = setOf("1", "2", "3", "4")
        repository.startCompareSelectionSession(selectedIds)
        
        val pair = repository.pairwiseState.value
        assertTrue("Option A must be in selectedIds. Got: ${pair.optionA.id}", selectedIds.contains(pair.optionA.id))
        assertTrue("Option B must be in selectedIds. Got: ${pair.optionB.id}", selectedIds.contains(pair.optionB.id))
        assertNotEquals("Unselected item '5' must not be option A", "5", pair.optionA.id)
        assertNotEquals("Unselected item '5' must not be option B", "5", pair.optionB.id)
    }

    @Test
    fun testIsolationAfterLoop() = runTest {
        val allItems = (1..10).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(allItems)
        
        val selectedIds = setOf("1", "2", "3", "4", "5")
        repository.startCompareSelectionSession(selectedIds)
        
        // Vote
        repository.recordCompareSelectionVote(repository.pairwiseState.value.optionA.id)
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionB.id))

        // Skip
        repository.skipCompareSelectionPair()
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionB.id))

        // Filter change
        repository.setCompareMediaType(CompareMediaTypeFilter.PHOTOS)
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionB.id))

        // Sort change
        repository.setCompareSort(CompareSortOption.LARGEST_FILES)
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionB.id))

        // Strategy change
        repository.setCompareStrategy(CompareStrategy.PERSONALIZED)
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionB.id))
        
        repository.setCompareStrategy(CompareStrategy.REDISCOVER)
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
        
        repository.setCompareStrategy(CompareStrategy.LEAST_INTERACTED)
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
        
        repository.setCompareStrategy(CompareStrategy.EXPLORE)
        assertTrue(selectedIds.contains(repository.pairwiseState.value.optionA.id))
    }

    @Test
    fun testMediaTypeFilters() = runTest {
        val allItems = listOf(
            createMediaItem("1", mediaType = "PHOTO"),
            createMediaItem("2", mediaType = "PHOTO"),
            createMediaItem("3", mediaType = "VIDEO"),
            createMediaItem("4", mediaType = "VIDEO")
        )
        repository.setMediaItemsForTesting(allItems)
        
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        
        // PHOTOS
        repository.setCompareMediaType(CompareMediaTypeFilter.PHOTOS)
        val pairPhotos = repository.pairwiseState.value
        assertEquals("PHOTO", pairPhotos.optionA.mediaType.uppercase())
        assertEquals("PHOTO", pairPhotos.optionB.mediaType.uppercase())

        // VIDEOS
        repository.setCompareMediaType(CompareMediaTypeFilter.VIDEOS)
        val pairVideos = repository.pairwiseState.value
        assertEquals("VIDEO", pairVideos.optionA.mediaType.uppercase())
        assertEquals("VIDEO", pairVideos.optionB.mediaType.uppercase())
    }

    @Test
    fun testStrategies_And_Sorts_Consistency() = runTest {
        val allItems = listOf(
            createMediaItem("1", sizeBytes = 100L, rating = 5f, dateAdded = 1000L),
            createMediaItem("2", sizeBytes = 200L, rating = 4f, dateAdded = 2000L),
            createMediaItem("3", sizeBytes = 300L, rating = 3f, dateAdded = 3000L),
            createMediaItem("4", sizeBytes = 400L, rating = 2f, dateAdded = 4000L)
        )
        repository.setMediaItemsForTesting(allItems)
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))

        // Sort: LARGEST_FILES -> Pool should be [4, 3, 2, 1]
        repository.setCompareSort(CompareSortOption.LARGEST_FILES)
        var top = repository.pairwiseDiagnostics.value.topCandidateIds
        assertEquals("4", top[0])
        assertEquals("3", top[1])

        // Sort: NEWEST -> Pool should be [4, 3, 2, 1]
        repository.setCompareSort(CompareSortOption.NEWEST)
        top = repository.pairwiseDiagnostics.value.topCandidateIds
        assertEquals("4", top[0])
        assertEquals("3", top[1])

        // Sort: OLDEST -> Pool should be [1, 2, 3, 4]
        repository.setCompareSort(CompareSortOption.OLDEST)
        top = repository.pairwiseDiagnostics.value.topCandidateIds
        assertEquals("1", top[0])
        assertEquals("2", top[1])
        
        // Strategy: REDISCOVER -> Items user likes + interaction age bonus
        repository.setCompareStrategy(CompareStrategy.REDISCOVER)
        repository.setCompareSort(CompareSortOption.NEWEST)
        top = repository.pairwiseDiagnostics.value.topCandidateIds
        // Item 1 has rating 5f (liked), interaction age bonus logic applies.
        assertEquals("1", top[0])
    }

    @Test
    fun testVoting_Stats_Updates() = runTest {
        val allItems = (1..6).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(allItems)
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        
        val winnerId = repository.pairwiseState.value.optionA.id
        repository.recordCompareSelectionVote(winnerId)
        
        val session = repository.compareSelectionSession.value
        assertEquals(2, session.roundNumber)
        assertEquals(1, session.wins)
    }

    @Test
    fun testSkip_NoMutation() = runTest {
        val allItems = (1..6).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(allItems)
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        
        repository.skipCompareSelectionPair()
        
        val session = repository.compareSelectionSession.value
        assertEquals(2, session.roundNumber)
        assertEquals(1, session.skips)
        assertEquals(0, session.wins)
        assertEquals(0, session.losses)
    }

    @Test
    fun testCompletion_PairExhaustion() = runTest {
        val allItems = (1..4).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(allItems)
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        
        // 4 items = 6 pairs.
        repeat(6) {
            repository.skipCompareSelectionPair()
        }
        
        assertTrue("Session should be complete", repository.compareSelectionSession.value.isComplete)
        assertEquals("All unique pairs exhausted.", repository.compareSelectionSession.value.completionReason)
    }

    @Test
    fun testCompletion_DeletionExhaustion() = runTest {
        val allItems = mutableListOf(
            createMediaItem("1"),
            createMediaItem("2"),
            createMediaItem("3"),
            createMediaItem("4")
        )
        repository.setMediaItemsForTesting(allItems)
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        
        repository.deleteMediaItem("1")
        repository.deleteMediaItem("2")
        repository.deleteMediaItem("3")
        
        assertTrue("Session should be complete after deletions", repository.compareSelectionSession.value.isComplete)
        assertEquals("Fewer than 2 eligible items remain.", repository.compareSelectionSession.value.completionReason)
    }

    @Test
    fun testCompletion_FilterExhaustion() = runTest {
        val allItems = listOf(
            createMediaItem("1", mediaType = "PHOTO"),
            createMediaItem("2", mediaType = "PHOTO"),
            createMediaItem("3", mediaType = "PHOTO"),
            createMediaItem("4", mediaType = "VIDEO")
        )
        repository.setMediaItemsForTesting(allItems)
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        
        repository.setCompareMediaType(CompareMediaTypeFilter.VIDEOS)
        
        assertTrue("Session should be complete when filter leaves < 2 items", repository.compareSelectionSession.value.isComplete)
        assertEquals("Fewer than 2 eligible items remain.", repository.compareSelectionSession.value.completionReason)
    }

    @Test
    fun testCompareAgain_Resets() = runTest {
        val allItems = (1..6).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(allItems)
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        
        repository.recordCompareSelectionVote(repository.pairwiseState.value.optionA.id)
        assertEquals(2, repository.compareSelectionSession.value.roundNumber)
        
        repository.restartCompareSelectionSession()
        assertEquals(1, repository.compareSelectionSession.value.roundNumber)
        assertEquals(0, repository.compareSelectionSession.value.wins)
    }

    @Test
    fun testSessionExit_RestoresGlobal() = runTest {
        repository.startCompareSelectionSession(setOf("1", "2", "3", "4"))
        assertTrue(repository.compareSelectionSession.value.isActive)
        
        repository.exitCompareSelectionSession()
        assertFalse(repository.compareSelectionSession.value.isActive)
    }

    @Test
    fun testCompletion_RoundLimit() = runTest {
        // Use 20 items to avoid pair exhaustion
        val allItems = (1..20).map { createMediaItem(it.toString()) }
        repository.setMediaItemsForTesting(allItems)
        
        repository.startCompareSelectionSession((1..20).map { it.toString() }.toSet())
        
        repeat(50) {
            repository.recordCompareSelectionVote(repository.pairwiseState.value.optionA.id)
        }
        
        // 51st vote
        repository.recordCompareSelectionVote(repository.pairwiseState.value.optionA.id)
        
        assertTrue("Session should be complete after limit", repository.compareSelectionSession.value.isComplete)
        assertEquals("Session round limit reached.", repository.compareSelectionSession.value.completionReason)
    }
}

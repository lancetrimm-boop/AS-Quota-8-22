package com.example

import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.TasteDNA
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FavoriteTastePreferenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository

    @Before
    fun setup() {
        repository = MediaRepository(testDispatcher)
    }

    private fun createMediaItem(
        id: String,
        title: String = "Test Item",
        moodTags: List<String> = listOf("Vibrant", "Warm"),
        isFavorite: Boolean = false
    ): MediaItem {
        return MediaItem(
            id = id,
            title = title,
            mediaType = "PHOTO",
            isFavorite = isFavorite,
            moodTags = moodTags,
            compatibilityStatus = CompatibilityStatus.PLAYABLE
        )
    }

    @Test
    fun testFavoriteOn_AppliesPositiveTasteDnaLearning() = testScope.runTest {
        val initialDna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.5,
            warmth = 0.5,
            learnedWarmth = 0.5
        )
        repository.updateTasteDNA(initialDna, isUserGenerated = true, evidenceCategory = "Manual Setup")
        advanceUntilIdle()

        val item = createMediaItem(id = "item_1", moodTags = listOf("Vibrant"), isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        // Toggle Favorite ON
        repository.toggleFavorite("item_1")
        advanceUntilIdle()

        val updatedItem = repository.getMediaItemById("item_1")
        assertNotNull(updatedItem)
        assertTrue("Item isFavorite should be true", updatedItem!!.isFavorite)

        val updatedDna = repository.tasteDNA.value
        assertTrue(
            "Learned vibrancy should have increased after Favorite ON",
            updatedDna.learnedVibrancy > initialDna.learnedVibrancy
        )
        assertEquals("Baseline vibrancy should remain unchanged", 0.5, updatedDna.vibrancy, 0.0001)
    }

    @Test
    fun testFavoriteOff_LeavesTasteDnaCompletelyUnchanged() = testScope.runTest {
        val initialDna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.6,
            warmth = 0.5,
            learnedWarmth = 0.6
        )
        repository.updateTasteDNA(initialDna, isUserGenerated = true, evidenceCategory = "Manual Setup")
        advanceUntilIdle()

        val item = createMediaItem(id = "item_2", moodTags = listOf("Vibrant", "Warm"), isFavorite = true)
        repository.setMediaItemsForTesting(listOf(item))

        val dnaBeforeUnfavorite = repository.tasteDNA.value

        // Toggle Favorite OFF
        repository.toggleFavorite("item_2")
        advanceUntilIdle()

        val updatedItem = repository.getMediaItemById("item_2")
        assertNotNull(updatedItem)
        assertFalse("Item isFavorite should be false", updatedItem!!.isFavorite)

        val dnaAfterUnfavorite = repository.tasteDNA.value
        assertEquals("Learned vibrancy must remain exactly unchanged on Unfavorite", dnaBeforeUnfavorite.learnedVibrancy, dnaAfterUnfavorite.learnedVibrancy, 0.00001)
        assertEquals("Learned warmth must remain exactly unchanged on Unfavorite", dnaBeforeUnfavorite.learnedWarmth, dnaAfterUnfavorite.learnedWarmth, 0.00001)
        assertEquals("Effective vibrancy must remain exactly unchanged on Unfavorite", dnaBeforeUnfavorite.effectiveVibrancy, dnaAfterUnfavorite.effectiveVibrancy, 0.00001)
        assertEquals("Effective warmth must remain exactly unchanged on Unfavorite", dnaBeforeUnfavorite.effectiveWarmth, dnaAfterUnfavorite.effectiveWarmth, 0.00001)
        assertEquals("Whole TasteDNA instance should be identical", dnaBeforeUnfavorite, dnaAfterUnfavorite)
    }

    @Test
    fun testFavoriteCycle_OnThenOffThenOn() = testScope.runTest {
        val initialDna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.5
        )
        repository.updateTasteDNA(initialDna, isUserGenerated = true, evidenceCategory = "Manual Setup")
        advanceUntilIdle()

        val item = createMediaItem(id = "item_3", moodTags = listOf("Vibrant"), isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        // Step 1: Turn ON
        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        val dnaAfterFirstOn = repository.tasteDNA.value
        val learnedAfterFirstOn = dnaAfterFirstOn.learnedVibrancy
        assertTrue("First ON should increase learned vibrancy", learnedAfterFirstOn > 0.5)

        // Step 2: Turn OFF
        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        val dnaAfterOff = repository.tasteDNA.value
        assertEquals("OFF must not change learned vibrancy at all", learnedAfterFirstOn, dnaAfterOff.learnedVibrancy, 0.00001)

        // Step 3: Turn ON again
        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        val dnaAfterSecondOn = repository.tasteDNA.value
        assertTrue(
            "Second ON should further increase learned vibrancy",
            dnaAfterSecondOn.learnedVibrancy > learnedAfterFirstOn
        )
    }

    @Test
    fun testFavoritesScreenSessionDeferredRemovalLogic() {
        val itemA = createMediaItem("A", title = "Item A", isFavorite = true)
        val itemB = createMediaItem("B", title = "Item B", isFavorite = true)
        val itemC = createMediaItem("C", title = "Item C", isFavorite = false)

        val mediaItems = listOf(itemA, itemB, itemC)

        // Simulate session entry snapshot in FavoritesScreen
        val initialFavoriteIds = mediaItems.filter { it.isFavorite }.map { it.id }.toSet()
        assertEquals(setOf("A", "B"), initialFavoriteIds)

        // User unfavorites item B during the session
        val updatedMediaItems = mediaItems.map {
            if (it.id == "B") it.copy(isFavorite = false) else it
        }

        // Active session list retains item B
        val sessionMediaItems = updatedMediaItems.filter { it.id in initialFavoriteIds }
        assertEquals(2, sessionMediaItems.size)
        assertEquals(listOf("A", "B"), sessionMediaItems.map { it.id })

        // Item B's UI representation accurately reflects isFavorite = false
        val sessionItemB = sessionMediaItems.first { it.id == "B" }
        assertFalse("Item B should be unselected in active session", sessionItemB.isFavorite)

        // On screen re-entry (new session snapshot)
        val reenteredFavoriteIds = updatedMediaItems.filter { it.isFavorite }.map { it.id }.toSet()
        assertEquals(setOf("A"), reenteredFavoriteIds)
        val reenteredSessionItems = updatedMediaItems.filter { it.id in reenteredFavoriteIds }
        assertEquals(1, reenteredSessionItems.size)
        assertEquals("A", reenteredSessionItems.first().id)
    }

    private fun assertNotNull(obj: Any?) {
        org.junit.Assert.assertNotNull(obj)
    }
}

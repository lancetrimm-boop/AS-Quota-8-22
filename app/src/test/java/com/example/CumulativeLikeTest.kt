package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CompatibilityStatus
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.TasteDNA
import com.example.data.db.AuraDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CumulativeLikeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var database: AuraDatabase
    private lateinit var repository: MediaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        repository = MediaRepository(testDispatcher)
        repository.setDatabaseForTesting(database)
    }

    @After
    fun tearDown() {
        database.close()
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
    fun testFavoriteOn_CreatesOneLike() = testScope.runTest {
        val item = createMediaItem(id = "item_1", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        // Initial state: 0 Likes
        assertEquals("Initial Like count must be 0", 0, repository.getLikeCount("item_1"))

        // Favorite OFF -> ON
        repository.toggleFavorite("item_1")
        advanceUntilIdle()

        // Verify Favorite is true and Like count is 1
        val updated = repository.getMediaItemById("item_1")
        assertTrue("Item should be favorite", updated?.isFavorite == true)
        assertEquals("Like count must be 1 after Favorite ON", 1, repository.getLikeCount("item_1"))
    }

    @Test
    fun testFavoriteOff_DoesNotRemoveLike() = testScope.runTest {
        val item = createMediaItem(id = "item_2", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        // Step 1: Turn ON
        repository.toggleFavorite("item_2")
        advanceUntilIdle()
        assertEquals("Like count must be 1 after ON", 1, repository.getLikeCount("item_2"))

        // Step 2: Turn OFF (Unfavorite)
        repository.toggleFavorite("item_2")
        advanceUntilIdle()

        val updated = repository.getMediaItemById("item_2")
        assertFalse("Item should not be favorite", updated?.isFavorite == true)
        assertEquals("Like count must remain 1 after Favorite OFF (no decrement)", 1, repository.getLikeCount("item_2"))
    }

    @Test
    fun testRepeatedFavoriteCycles_AccumulatesLikes() = testScope.runTest {
        val item = createMediaItem(id = "item_3", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        assertEquals("Initial count is 0", 0, repository.getLikeCount("item_3"))

        // Cycle 1: ON -> OFF
        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        assertEquals("Cycle 1 ON -> 1 Like", 1, repository.getLikeCount("item_3"))

        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        assertEquals("Cycle 1 OFF -> Still 1 Like", 1, repository.getLikeCount("item_3"))

        // Cycle 2: ON -> OFF
        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        assertEquals("Cycle 2 ON -> 2 Likes", 2, repository.getLikeCount("item_3"))

        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        assertEquals("Cycle 2 OFF -> Still 2 Likes", 2, repository.getLikeCount("item_3"))

        // Cycle 3: ON
        repository.toggleFavorite("item_3")
        advanceUntilIdle()
        assertEquals("Cycle 3 ON -> 3 Likes", 3, repository.getLikeCount("item_3"))
    }

    @Test
    fun testHistoricalLikeEvidence_SurvivesUnfavoriteInDatabase() = testScope.runTest {
        val item = createMediaItem(id = "item_4", isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        // Turn ON
        repository.toggleFavorite("item_4")
        advanceUntilIdle()

        // Turn OFF
        repository.toggleFavorite("item_4")
        advanceUntilIdle()

        // Direct DB verification: micro_moments table still has the record
        val rawMomentCount = database.microMomentDao().getMomentCountForMedia("item_4")
        assertEquals("Database micro_moments table must retain the row", 1, rawMomentCount)
    }

    @Test
    fun testLikeRecording_IsIndependentOfFineTuning() = testScope.runTest {
        val initialDna = TasteDNA(
            isFineTuningEnabled = false,
            vibrancy = 0.5,
            learnedVibrancy = 0.5
        )
        repository.updateTasteDNA(initialDna, isUserGenerated = true, evidenceCategory = "Manual Setup")
        advanceUntilIdle()

        val item = createMediaItem(id = "item_5", moodTags = listOf("Vibrant"), isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        // Turn ON with Fine Tuning DISABLED
        repository.toggleFavorite("item_5")
        advanceUntilIdle()

        // Like count MUST still increment to 1
        assertEquals("Like count must increment even when fine tuning is disabled", 1, repository.getLikeCount("item_5"))

        // Taste DNA learned vibrancy must NOT change because fine tuning is disabled
        val currentDna = repository.tasteDNA.value
        assertEquals("Learned vibrancy must remain unchanged when fine tuning is disabled", 0.5, currentDna.learnedVibrancy, 0.0001)
    }

    @Test
    fun testPriority1Invariant_RemainsIntactDuringLikeAccumulation() = testScope.runTest {
        val initialDna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.5
        )
        repository.updateTasteDNA(initialDna, isUserGenerated = true, evidenceCategory = "Manual Setup")
        advanceUntilIdle()

        val item = createMediaItem(id = "item_6", moodTags = listOf("Vibrant"), isFavorite = false)
        repository.setMediaItemsForTesting(listOf(item))

        // Turn ON -> Learns positive signal & records Like 1
        repository.toggleFavorite("item_6")
        advanceUntilIdle()
        val learnedAfterOn = repository.tasteDNA.value.learnedVibrancy
        assertTrue("Learned vibrancy increased on Favorite ON", learnedAfterOn > 0.5)
        assertEquals("Like count is 1", 1, repository.getLikeCount("item_6"))

        // Turn OFF -> Zero change in Taste DNA & Like count remains 1
        repository.toggleFavorite("item_6")
        advanceUntilIdle()
        val dnaAfterOff = repository.tasteDNA.value
        assertEquals("Learned vibrancy is unchanged on Favorite OFF", learnedAfterOn, dnaAfterOff.learnedVibrancy, 0.00001)
        assertEquals("Like count is still 1 on Favorite OFF", 1, repository.getLikeCount("item_6"))
    }
}

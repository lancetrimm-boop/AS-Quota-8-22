package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class DiscoverRecommendationTest {

    private val tasteDNA = TasteDNA()
    private val stats = IntelligenceStats()

    private val favoriteItem = MediaItem(
        id = "fav",
        title = "Favorite",
        mediaType = "VIDEO",
        genre = "Action",
        moodTags = listOf("vibrant"), 
        isFavorite = true,
        viewCount = 10,
        rating = 5.0f
    )

    private val unseenItem = MediaItem(
        id = "new",
        title = "New",
        mediaType = "VIDEO",
        genre = "Documentary",
        moodTags = listOf("muted"),
        viewCount = 0,
        exposureCount = 0
    )

    @Test
    fun testDiscover_PersonalizedMode_FavorsFavorites() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 2.0f,
            explorationWeight = 0.1f,
            noveltyWeight = 0.1f,
            diversityWeight = 0.2f,
            familiarityPenalty = 0.5f
        )

        val categories = RecommendationEngine.computeDiscoverCategories(
            allMedia = listOf(favoriteItem, unseenItem),
            tasteDNA = tasteDNA,
            strategy = strategy,
            stats = stats
        )

        // In personalized mode, the high predicted match (favorite) should be next obsession
        assertEquals("fav", categories.nextObsession?.id)
        assertEquals("High predicted match", categories.nextObsession?.selectionReason)
    }

    @Test
    fun testDiscover_ExploratoryMode_FavorsUnseen() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 0.2f,
            explorationWeight = 1.5f,
            noveltyWeight = 0.5f,
            diversityWeight = 0.6f,
            familiarityPenalty = 0.1f
        )

        val categories = RecommendationEngine.computeDiscoverCategories(
            allMedia = listOf(favoriteItem, unseenItem),
            tasteDNA = tasteDNA,
            strategy = strategy,
            stats = stats
        )

        // In exploratory mode, the unseen item should rise to Next Obsession
        assertEquals("new", categories.nextObsession?.id)
        assertTrue(categories.nextObsession?.selectionReason?.contains("Aura is learning") == true || 
                   categories.nextObsession?.selectionReason?.contains("haven't explored") == true)
    }

    @Test
    fun testDiscover_SelectionReasons_AreAnnotated() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 1.0f,
            explorationWeight = 1.0f,
            noveltyWeight = 1.0f,
            diversityWeight = 1.0f,
            familiarityPenalty = 1.0f
        )

        val categories = RecommendationEngine.computeDiscoverCategories(
            allMedia = listOf(favoriteItem, unseenItem),
            tasteDNA = tasteDNA,
            strategy = strategy,
            stats = stats
        )

        assertNotNull(categories.nextObsession?.selectionReason)
        categories.freshForYou.forEach { 
            assertNotNull(it.selectionReason)
        }
    }
}

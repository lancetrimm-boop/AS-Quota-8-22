package com.example

import com.example.data.MediaItem
import com.example.data.RecommendationEngine
import com.example.data.TasteDNA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationHygieneTest {

    @Test
    fun `test AI Sort - Excludes rated media`() {
        val unratedItem = MediaItem(id = "unrated", title = "Unrated", mediaType = "PHOTO", rating = 0f)
        val ratedItem = MediaItem(id = "rated", title = "Rated", mediaType = "PHOTO", rating = 4.0f)
        
        val scoreUnrated = RecommendationEngine.scoreItemForDiscovery(unratedItem)
        val scoreRated = RecommendationEngine.scoreItemForDiscovery(ratedItem)
        
        assertTrue("Unrated item should have normal score", scoreUnrated > -100f)
        assertTrue("Rated item should be heavily penalized", scoreRated < -500f)
    }

    @Test
    fun `test Exposure Penalty - Deprioritizes frequently shown items`() {
        val itemFresh = MediaItem(id = "fresh", title = "Fresh", mediaType = "PHOTO", exposureCount = 0)
        val itemStale = MediaItem(id = "stale", title = "Stale", mediaType = "PHOTO", exposureCount = 10)
        
        val scoreFresh = RecommendationEngine.scoreItemForDiscovery(itemFresh)
        val scoreStale = RecommendationEngine.scoreItemForDiscovery(itemStale)
        
        assertTrue("Stale item should rank lower than fresh item", scoreFresh > scoreStale)
    }

    @Test
    fun `test Discover Deduplication - Only one representative per content identity`() {
        val sourceVideo = MediaItem(id = "video1", title = "Source Video", mediaType = "VIDEO")
        val screenshot1 = MediaItem(id = "ss1", title = "Screenshot 1", mediaType = "PHOTO", parentContentId = "video1")
        val screenshot2 = MediaItem(id = "ss2", title = "Screenshot 2", mediaType = "PHOTO", parentContentId = "video1")
        
        val allMedia = listOf(sourceVideo, screenshot1, screenshot2)
        val categories = RecommendationEngine.computeDiscoverCategories(allMedia)
        
        // Count total unique media items in all categories
        val resultIds = mutableSetOf<String>()
        categories.nextObsession?.let { resultIds.add(it.id) }
        categories.freshForYou.forEach { resultIds.add(it.id) }
        categories.underTheRadar.forEach { resultIds.add(it.id) }
        categories.aLittleDifferent.forEach { resultIds.add(it.id) }
        categories.fromYourFavorites.forEach { resultIds.add(it.id) }
        
        // Check if more than one derivative from video1 is present
        val video1Derivatives = resultIds.filter { id -> 
            val item = allMedia.find { it.id == id }
            item?.id == "video1" || item?.parentContentId == "video1"
        }
        
        assertTrue("Should only select one representative from the same source content group", video1Derivatives.size <= 1)
    }

    @Test
    fun `test Pairwise Pool Deduplication`() {
        val sourceVideo = MediaItem(id = "video1", title = "Source Video", mediaType = "VIDEO")
        val screenshot1 = MediaItem(id = "ss1", title = "Screenshot 1", mediaType = "PHOTO", parentContentId = "video1")
        val allMedia = listOf(sourceVideo, screenshot1)
        
        val pool = RecommendationEngine.getTop100PairwiseCandidates(allMedia)
        
        assertEquals("Pool should be deduplicated by content identity", 1, pool.size)
        assertEquals("Should prefer the one with highest score (usually the source video if rating same)", "video1", pool[0].first.id)
    }
}

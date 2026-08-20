package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class LibraryDiscoveryTest {

    private val tasteDNA = TasteDNA()
    private val stats = IntelligenceStats()

    private val favoriteItem = MediaItem(
        id = "fav",
        title = "Favorite",
        mediaType = "VIDEO",
        genre = "Action",
        moodTags = listOf("vibrant"), 
        isFavorite = false,
        viewCount = 10,
        rating = 2.0f,
        lastViewedTimestamp = System.currentTimeMillis() - 4000000 // Not recent
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
    fun testLibrary_AISort_RespectsPolicy() {
        val repo = MediaRepository() // In-memory instance for testing
        
        // 1. Personalized Policy
        val personalizedResults = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.PERSONALIZED,
            inputItems = listOf(favoriteItem, unseenItem),
            policy = DiscoveryPolicy(mode = DiscoveryMode.PERSONALIZED),
            stats = stats
        )
        
        assertEquals("fav", personalizedResults[0].id)
        assertTrue(personalizedResults[0].selectionReason!!.contains("For You") || personalizedResults[0].selectionReason!!.contains("Best Match"))

        // 2. Exploratory Policy
        val exploratoryResults = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.PERSONALIZED,
            inputItems = listOf(favoriteItem, unseenItem),
            policy = DiscoveryPolicy(mode = DiscoveryMode.EXPLORATORY),
            stats = stats
        )
        
        assertEquals("new", exploratoryResults[0].id)
        assertTrue(exploratoryResults[0].selectionReason!!.contains("Best Match"))
    }

    @Test
    fun testLibrary_RediscoverSort_FocusesOnLiked() {
        val repo = MediaRepository()
        val likedItem = favoriteItem.copy(id = "liked", isFavorite = true)
        val nonLikedItem = unseenItem.copy(id = "unseen", isFavorite = false, rating = 0f)
        
        val results = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.REDISCOVER,
            inputItems = listOf(likedItem, nonLikedItem)
        )
        
        assertEquals(1, results.size)
        assertEquals("liked", results[0].id)
    }

    @Test
    fun testLibrary_LeastInteractedSort_FollowsPriority() {
        val repo = MediaRepository()
        
        val itemA = unseenItem.copy(id = "A", exposureCount = 0, viewCount = 0, rating = 0f)
        val itemB = unseenItem.copy(id = "B", exposureCount = 5, viewCount = 0, rating = 0f)
        val itemC = favoriteItem.copy(id = "C", exposureCount = 5, viewCount = 0, rating = 5f)
        val itemD = favoriteItem.copy(id = "D", exposureCount = 5, viewCount = 2, rating = 5f)
        
        val results = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.LEAST_INTERACTED,
            inputItems = listOf(itemD, itemC, itemB, itemA)
        )
        
        assertEquals("A", results[0].id) // Priority 1: Lowest exposure (0)
        assertEquals("B", results[1].id) // Priority 2: Unrated (0f) among exposure=5
        assertEquals("C", results[2].id) // Priority 3: Lowest engagement (0 views) among rated items with exposure=5
        assertEquals("D", results[3].id) // Priority 3: Higher engagement (2 views)
    }
}

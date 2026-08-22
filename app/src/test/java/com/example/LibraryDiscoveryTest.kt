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
    fun testLibrary_FavoritesSort_IncludesLikedAndHighRated() {
        val repo = MediaRepository()
        val favorite = favoriteItem.copy(id = "fav_true", isFavorite = true, rating = 0f)
        val highRated = unseenItem.copy(id = "rated_5", isFavorite = false, rating = 5f)
        val ordinary = unseenItem.copy(id = "normal", isFavorite = false, rating = 0f)
        
        val results = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.FAVORITES,
            inputItems = listOf(favorite, highRated, ordinary)
        )
        
        assertEquals(2, results.size)
        val ids = results.map { it.id }
        assertTrue(ids.contains("fav_true"))
        assertTrue(ids.contains("rated_5"))
    }

    @Test
    fun testLibrary_HiddenGemsSort_FocusesOnLowExposure() {
        val repo = MediaRepository()
        val gem = unseenItem.copy(id = "gem", exposureCount = 1, viewCount = 0, rating = 0f)
        val exposed = favoriteItem.copy(id = "exposed", exposureCount = 10, viewCount = 5, rating = 5f)
        
        val results = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.HIDDEN_GEMS,
            inputItems = listOf(gem, exposed)
        )
        
        assertEquals(1, results.size)
        assertEquals("gem", results[0].id)
    }

    @Test
    fun testLibrary_SurpriseMe_UsesStableSeed() {
        val repo = MediaRepository()
        val items = (1..20).map { unseenItem.copy(id = "item_$it") }
        
        val results1 = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.SURPRISE_ME,
            inputItems = items,
            sessionSeed = 123L
        )

        val results2 = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.SURPRISE_ME,
            inputItems = items,
            sessionSeed = 123L
        )

        val results3 = repo.getFilteredAndSortedMedia(
            filterType = "ALL",
            sortCategory = SortCategory.INTELLIGENT,
            standardSort = StandardSortOption.NEWEST_FIRST,
            intelligentSort = IntelligentSortOption.SURPRISE_ME,
            inputItems = items,
            sessionSeed = 456L
        )

        assertEquals("Same seed must produce same order", results1.map { it.id }, results2.map { it.id })
        assertNotEquals("Different seed must produce different order", results1.map { it.id }, results3.map { it.id })
    }
}

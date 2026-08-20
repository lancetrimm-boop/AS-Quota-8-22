package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuraLibrarySortingTest {

    private lateinit var repository: MediaRepository
    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        repository = MediaRepository()
    }

    private fun createItem(
        id: String,
        title: String,
        dateAdded: Long = now,
        lastViewed: Long? = null,
        viewCount: Int = 0,
        rating: Float = 0f,
        durationMs: Long = 0L,
        exposureCount: Int = 0
    ) = MediaItem(
        id = id,
        title = title,
        mediaType = "VIDEO",
        dateAdded = dateAdded,
        lastViewedTimestamp = lastViewed,
        viewCount = viewCount,
        rating = rating,
        durationMs = durationMs,
        exposureCount = exposureCount,
        compatibilityStatus = CompatibilityStatus.PLAYABLE
    )

    @Test
    fun testStandardSort_Title() {
        val items = listOf(
            createItem("1", "Banana"),
            createItem("2", "Apple"),
            createItem("3", "Cherry")
        )

        val sortedAsc = repository.getFilteredAndSortedMedia(
            "ALL", SortCategory.STANDARD, StandardSortOption.TITLE_ASC, IntelligentSortOption.PERSONALIZED, inputItems = items
        )
        assertEquals("Apple", sortedAsc[0].title)
        assertEquals("Banana", sortedAsc[1].title)
        assertEquals("Cherry", sortedAsc[2].title)

        val sortedDesc = repository.getFilteredAndSortedMedia(
            "ALL", SortCategory.STANDARD, StandardSortOption.TITLE_DESC, IntelligentSortOption.PERSONALIZED, inputItems = items
        )
        assertEquals("Cherry", sortedDesc[0].title)
        assertEquals("Banana", sortedDesc[1].title)
        assertEquals("Apple", sortedDesc[2].title)
    }

    @Test
    fun testStandardSort_Duration() {
        val items = listOf(
            createItem("1", "Short", durationMs = 1000),
            createItem("2", "Long", durationMs = 5000),
            createItem("3", "Medium", durationMs = 3000)
        )

        val sortedShort = repository.getFilteredAndSortedMedia(
            "ALL", SortCategory.STANDARD, StandardSortOption.SHORTEST_DURATION, IntelligentSortOption.PERSONALIZED, inputItems = items
        )
        assertEquals("Short", sortedShort[0].title)
        assertEquals("Medium", sortedShort[1].title)
        assertEquals("Long", sortedShort[2].title)
    }

    @Test
    fun testIntelligentSort_LeastInteracted() {
        val items = listOf(
            createItem("1", "High Exposure", viewCount = 10, exposureCount = 20),
            createItem("2", "Low Exposure", viewCount = 1, exposureCount = 2),
            createItem("3", "Unseen", viewCount = 0, exposureCount = 0)
        )

        val sorted = repository.getFilteredAndSortedMedia(
            "ALL", SortCategory.INTELLIGENT, StandardSortOption.NEWEST_FIRST, IntelligentSortOption.LEAST_INTERACTED, inputItems = items
        )
        assertEquals("3", sorted[0].id)
        assertEquals("2", sorted[1].id)
    }

    @Test
    fun testIntelligentSort_Rediscover() {
        val items = listOf(
            createItem("1", "Recent Liked", lastViewed = now - 1000, rating = 5f), // Filtered (recent)
            createItem("2", "Old Liked", lastViewed = now - (1000L * 60 * 60 * 24 * 30), rating = 5f), // Kept
            createItem("3", "Old Not Liked", lastViewed = now - (1000L * 60 * 60 * 24 * 30), rating = 0f) // Filtered (not liked)
        )

        val sorted = repository.getFilteredAndSortedMedia(
            "ALL", SortCategory.INTELLIGENT, StandardSortOption.NEWEST_FIRST, IntelligentSortOption.REDISCOVER, inputItems = items
        )
        assertEquals(1, sorted.size)
        assertEquals("Old Liked", sorted[0].title)
    }
}

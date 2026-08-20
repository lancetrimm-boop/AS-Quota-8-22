package com.example.data

import org.junit.Assert.*
import org.junit.Test

class CompareRoundResultsTest {

    private fun createItem(id: String, elo: Double, isDeleted: Boolean = false) = MediaItem(
        id = id,
        title = "Media $id",
        mediaType = "PHOTO",
        eloRating = elo,
        isDeleted = isDeleted,
        compatibilityStatus = CompatibilityStatus.PLAYABLE
    )

    @Test
    fun `test ranking and windowing logic`() {
        val items = mapOf(
            "1" to createItem("1", 1500.0),
            "2" to createItem("2", 1600.0),
            "3" to createItem("3", 1400.0),
            "4" to createItem("4", 1700.0),
            "5" to createItem("5", 1550.0),
            "6" to createItem("6", 1650.0),
            "7" to createItem("7", 1450.0),
            "8" to createItem("8", 1750.0),
            "9" to createItem("9", 1350.0),
            "10" to createItem("10", 1800.0),
            "11" to createItem("11", 1200.0)
        )
        val participatingIds = items.keys

        val results = CompareResultsHelper.getRankedResults(participatingIds, items)

        // Requirement: Display a maximum of 10 results
        assertEquals(10, results.size)

        // Requirement: Sort the participating items by Elo, highest to lowest
        assertEquals("10", results[0].id) // 1800.0
        assertEquals("8", results[1].id)  // 1750.0
        assertEquals("4", results[2].id)  // 1700.0
        
        // Item 11 (1200.0) should be excluded because it's the 11th
        assertFalse(results.any { it.id == "11" })
    }

    @Test
    fun `test result count with fewer than 10 participants`() {
        val items = mapOf(
            "1" to createItem("1", 1500.0),
            "2" to createItem("2", 1600.0),
            "3" to createItem("3", 1400.0),
            "4" to createItem("4", 1700.0)
        )
        val participatingIds = items.keys

        val results = CompareResultsHelper.getRankedResults(participatingIds, items)

        // Requirement: min(participatingItems.size, 10)
        assertEquals(4, results.size)
        assertEquals("4", results[0].id)
    }

    @Test
    fun `test results exclude non-participating items`() {
        val items = mapOf(
            "1" to createItem("1", 1500.0),
            "2" to createItem("2", 1600.0),
            "3" to createItem("3", 2000.0) // High Elo but not participating
        )
        val participatingIds = setOf("1", "2")

        val results = CompareResultsHelper.getRankedResults(participatingIds, items)

        assertEquals(2, results.size)
        assertFalse(results.any { it.id == "3" })
    }

    @Test
    fun `test deterministic tie-break`() {
        val items = mapOf(
            "A" to createItem("A", 1500.0),
            "B" to createItem("B", 1500.0)
        )
        val participatingIds = items.keys

        val results = CompareResultsHelper.getRankedResults(participatingIds, items)

        assertEquals(2, results.size)
        assertEquals("A", results[0].id) // Ties broken by ID
        assertEquals("B", results[1].id)
    }

    @Test
    fun `test deleted items are excluded`() {
        val items = mapOf(
            "1" to createItem("1", 1500.0),
            "2" to createItem("2", 1600.0, isDeleted = true)
        )
        val participatingIds = setOf("1", "2")

        val results = CompareResultsHelper.getRankedResults(participatingIds, items)

        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }
}

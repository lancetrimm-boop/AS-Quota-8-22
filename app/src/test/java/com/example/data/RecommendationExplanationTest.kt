package com.example.data

import org.junit.Assert.*
import org.junit.Test

class RecommendationExplanationTest {

    private val tasteDNA = TasteDNA(
        vibrancy = 0.8, // User likes vibrant
        learnedVibrancy = 0.8,
        isFineTuningEnabled = true
    )
    
    private val stats = IntelligenceStats(
        topGenres = listOf("Action", "Nature")
    )

    @Test
    fun testGenerate_HighMatch_Vibrant() {
        val item = MediaItem(
            id = "test1",
            title = "Vibrant Scene",
            mediaType = "PHOTO",
            genre = "Nature",
            moodTags = listOf("vibrant"),
            rating = 5.0f // Boost match score
        )

        val explanation = RecommendationExplanationGenerator.generate(item, tasteDNA, stats, emptyMap())
        
        assertNotNull(explanation)
        assertEquals("Based on your high rating", explanation?.primaryReason)
        // Wait, Based on high rating will take priority now.
    }

    @Test
    fun testGenerate_HighMatch_DNA_Alignment() {
        val creatorId = "creator1"
        val item = MediaItem(
            id = "test1_dna",
            title = "Vibrant Scene",
            mediaType = "PHOTO",
            genre = "Nature",
            moodTags = listOf("vibrant"),
            eloRating = 2000.0, // Boost match score via ELO (+0.5)
            creatorId = creatorId,
            viewCount = 10, // Reduce uncertainty
            exposureCount = 10
        )
        
        val creators = mapOf(
            creatorId to CreatorProfile(id = creatorId, name = "Aura Artist", platform = "LOCAL", affinityScore = 1.0)
        )

        val explanation = RecommendationExplanationGenerator.generate(item, tasteDNA, stats, creators)
        
        assertNotNull(explanation)
        assertEquals("High predicted match", explanation?.primaryReason)
        assertTrue(explanation?.detailPoints?.any { it.contains("Vibrancy") } == true)
        assertTrue(explanation?.detailPoints?.any { it.contains("Aura Artist") } == true)
    }

    @Test
    fun testGenerate_SimilarToFavorites() {
        val item = MediaItem(
            id = "test2",
            title = "Favorite Item",
            mediaType = "PHOTO",
            genre = "Nature",
            isFavorite = true
        )

        val explanation = RecommendationExplanationGenerator.generate(item, tasteDNA, stats, emptyMap())
        
        assertNotNull(explanation)
        assertEquals("Similar to your favorites", explanation?.primaryReason)
        assertTrue(explanation?.detailPoints?.contains("You previously favorited this item") == true)
    }

    @Test
    fun testGenerate_Novelty() {
        val item = MediaItem(
            id = "test3",
            title = "New Style",
            mediaType = "PHOTO",
            genre = "Sci-Fi", // Outside top genres
            viewCount = 0,
            exposureCount = 0,
            moodTags = listOf("experimental")
        )

        val explanation = RecommendationExplanationGenerator.generate(item, tasteDNA, stats, emptyMap())
        
        assertNotNull(explanation)
        assertEquals("Expand your taste", explanation?.primaryReason)
        assertTrue(explanation?.isExploratory == true)
        assertTrue(explanation?.detailPoints?.any { it.contains("A new discovery") } == true)
    }

    @Test
    fun testGenerate_LowConfidence_ReturnsNull() {
        val item = MediaItem(
            id = "test4",
            title = "Generic Item",
            mediaType = "PHOTO",
            genre = "Action", // In top genres to avoid novelty
            viewCount = 10,
            exposureCount = 20,
            rating = 0f
        )
        
        // Neutral DNA
        val neutralDNA = TasteDNA()

        val explanation = RecommendationExplanationGenerator.generate(item, neutralDNA, stats, emptyMap())
        
        // Should be null if we can't find a strong reason
        assertNull(explanation)
    }
}

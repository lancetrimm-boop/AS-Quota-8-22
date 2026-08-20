package com.example

import com.example.data.MediaItem
import com.example.data.RecommendationEngine
import com.example.data.TasteDNA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteDNATest {

    @Test
    fun testTasteDnaEffectiveValues() {
        val dna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.8,
            learnedVibrancy = 0.2
        )
        assertEquals(0.5, dna.effectiveVibrancy, 0.01)
    }

    @Test
    fun testScoringWithTasteDna() {
        val item = MediaItem(
            id = "test_item",
            title = "Vibrant Sunset",
            mediaType = "PHOTO",
            rating = 4.0f,
            moodTags = listOf("Vibrant", "Nature")
        )

        // Baseline score with default TasteDNA (0.5)
        val baselineScore = RecommendationEngine.scoreItemForPairwise(
            item = item,
            tasteDNA = TasteDNA(vibrancy = 0.5, learnedVibrancy = 0.5)
        )

        // Score with high vibrancy (1.0)
        val highVibrancyScore = RecommendationEngine.scoreItemForPairwise(
            item = item,
            tasteDNA = TasteDNA(vibrancy = 1.0, learnedVibrancy = 1.0)
        )

        assertTrue("Score with higher vibrancy should be greater", highVibrancyScore > baselineScore)
    }

    @Test
    fun testScoringWithPreferenceProfile() {
        val item = MediaItem(
            id = "test_item",
            title = "Unseen Gem",
            mediaType = "PHOTO",
            rating = 4.0f,
            viewCount = 0
        )

        val profileLowNovelty = TasteDNA.PreferenceProfile(noveltyWeight = 0.1)
        val profileHighNovelty = TasteDNA.PreferenceProfile(noveltyWeight = 0.9)

        val lowNoveltyScore = RecommendationEngine.scoreItemForPairwise(
            item = item,
            profile = profileLowNovelty,
            tasteDNA = TasteDNA(novelty = 1.0)
        )

        val highNoveltyScore = RecommendationEngine.scoreItemForPairwise(
            item = item,
            profile = profileHighNovelty,
            tasteDNA = TasteDNA(novelty = 1.0)
        )

        assertTrue("Score with higher novelty weight should be greater for unseen item", highNoveltyScore > lowNoveltyScore)
    }
}

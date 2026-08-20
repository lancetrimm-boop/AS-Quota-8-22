package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class PairwiseDiscoveryTest {

    private val tasteDNA = TasteDNA()
    private val stats = IntelligenceStats()

    private val highConfidenceItem = MediaItem(
        id = "high_conf",
        title = "High Confidence",
        mediaType = "VIDEO",
        viewCount = 20,
        rating = 5.0f,
        eloRating = 1800.0
    )

    private val uncertainItem = MediaItem(
        id = "uncertain",
        title = "Uncertain",
        mediaType = "VIDEO",
        viewCount = 0,
        exposureCount = 0,
        eloRating = 1500.0
    )

    @Test
    fun testPairwise_ExploitationMode_FavorsHighConfidence() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 1.5f,
            explorationWeight = 0.0f,
            noveltyWeight = 0.0f,
            diversityWeight = 0.0f,
            familiarityPenalty = 0.0f
        )

        val candidates = RecommendationEngine.getTop100PairwiseCandidates(
            allMedia = listOf(highConfidenceItem, uncertainItem),
            strategy = strategy,
            tasteDNA = tasteDNA,
            stats = stats
        )

        assertEquals("High confidence item should be first in exploitation-heavy strategy", 
            "high_conf", candidates[0].first.id)
    }

    @Test
    fun testPairwise_ExplorationMode_FavorsUncertainty() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 0.0f,
            explorationWeight = 1.5f,
            noveltyWeight = 0.0f,
            diversityWeight = 0.0f,
            familiarityPenalty = 0.0f
        )

        val candidates = RecommendationEngine.getTop100PairwiseCandidates(
            allMedia = listOf(highConfidenceItem, uncertainItem),
            strategy = strategy,
            tasteDNA = tasteDNA,
            stats = stats
        )

        assertEquals("Uncertain item should be first in exploration-heavy strategy", 
            "uncertain", candidates[0].first.id)
    }

    @Test
    fun testPairwise_InformationGain_ContrastSelection() {
        // Use tags with known direct dimension opposition
        val itemA = MediaItem(id = "A", title = "Symmetric", mediaType = "PHOTO", moodTags = listOf("symmetric"))
        val itemB = MediaItem(id = "B", title = "Asymmetric", mediaType = "PHOTO", moodTags = listOf("asymmetric"))
        val itemC = MediaItem(id = "C", title = "Also Symmetric", mediaType = "PHOTO", moodTags = listOf("symmetric"))

        val strategy = RecommendationStrategy(
            exploitationWeight = 0.5f,
            explorationWeight = 2.0f, // High exploration focus
            noveltyWeight = 0.0f,
            diversityWeight = 0.0f,
            familiarityPenalty = 0.0f
        )

        val pool = listOf(
            itemA to 10f,
            itemB to 10f,
            itemC to 10f
        )

        // Comparing A and B should have higher info gain than A and C
        val pairAB_Gain = ExplorationEngine.calculatePairInformationGain(itemA, itemB, tasteDNA)
        val pairAC_Gain = ExplorationEngine.calculatePairInformationGain(itemA, itemC, tasteDNA)

        println("Gain AB: $pairAB_Gain")
        println("Gain AC: $pairAC_Gain")

        assertTrue("A vs B (Contrast) should provide more info than A vs C (Similar)", pairAB_Gain > pairAC_Gain)

        val selectedPair = RecommendationEngine.selectNextPairFromPool(
            top100Pool = pool,
            strategy = strategy,
            tasteDNA = tasteDNA,
            randomSeed = 0L
        )

        // Under high exploration weight, either (A and B) or (B and C) should be selected 
        // as both provide the same maximal contrast (Symmetric vs Asymmetric)
        assertNotNull(selectedPair)
        val ids = listOf(selectedPair!!.first.id, selectedPair.second.id)
        assertTrue("Should prefer comparing contrasting items (A/B or B/C). Got: $ids", 
            (ids.contains("A") && ids.contains("B")) || (ids.contains("B") && ids.contains("C")))
    }
}

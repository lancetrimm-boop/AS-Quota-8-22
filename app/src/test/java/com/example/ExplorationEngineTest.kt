package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class ExplorationEngineTest {

    private val tasteDNA = TasteDNA()
    private val stats = IntelligenceStats(topGenres = listOf("Action"))

    private val favoriteItem = MediaItem(
        id = "fav",
        title = "Favorite",
        mediaType = "VIDEO",
        genre = "Action",
        isFavorite = true,
        viewCount = 10,
        rating = 5.0f
    )

    private val unseenItem = MediaItem(
        id = "new",
        title = "New",
        mediaType = "VIDEO",
        genre = "Documentary",
        viewCount = 0,
        exposureCount = 0
    )

    @Test
    fun testHighExploitation_FavorsKnownFavorites() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 1.0f,
            explorationWeight = 0.0f,
            noveltyWeight = 0.0f,
            diversityWeight = 0.0f,
            familiarityPenalty = 0.0f
        )

        val favEvidence = ExplorationEngine.calculateEvidence(favoriteItem, tasteDNA, stats)
        val newEvidence = ExplorationEngine.calculateEvidence(unseenItem, tasteDNA, stats)

        val favScore = ExplorationEngine.calculatePolicyScore(favEvidence, strategy)
        val newScore = ExplorationEngine.calculatePolicyScore(newEvidence, strategy)

        assertTrue("Favorite should score higher under pure exploitation (Fav: $favScore, New: $newScore)", favScore > newScore)
    }

    @Test
    fun testHighExploration_FavorsUnseenItems() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 0.0f,
            explorationWeight = 1.0f,
            noveltyWeight = 0.0f,
            diversityWeight = 0.0f,
            familiarityPenalty = 0.0f
        )

        val favEvidence = ExplorationEngine.calculateEvidence(favoriteItem, tasteDNA, stats)
        val newEvidence = ExplorationEngine.calculateEvidence(unseenItem, tasteDNA, stats)

        val favScore = ExplorationEngine.calculatePolicyScore(favEvidence, strategy)
        val newScore = ExplorationEngine.calculatePolicyScore(newEvidence, strategy)

        assertTrue("Unseen item should score higher under pure exploration (Fav: $favScore, New: $newScore)", newScore > favScore)
    }

    @Test
    fun testNoveltyWeight_BoostsNewGenres() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 0.0f,
            explorationWeight = 0.0f,
            noveltyWeight = 1.0f,
            diversityWeight = 0.0f,
            familiarityPenalty = 0.0f
        )

        val newEvidence = ExplorationEngine.calculateEvidence(unseenItem, tasteDNA, stats)
        val noveltyScore = ExplorationEngine.calculatePolicyScore(newEvidence, strategy)

        // Novelty is 0.7 (0.4 for genre + 0.3 for unseen)
        assertTrue("Novelty score should be significant for unseen non-top-genre item (Score: $noveltyScore)", noveltyScore >= 0.7f)
    }

    @Test
    fun testRedundancyPenalty_ReducesScore() {
        val strategy = RecommendationStrategy(
            exploitationWeight = 1.0f,
            explorationWeight = 0.0f,
            noveltyWeight = 0.0f,
            diversityWeight = 0.0f,
            familiarityPenalty = 1.0f
        )

        val exposedItem = favoriteItem.copy(lastExposedTimestamp = System.currentTimeMillis())
        val evidence = ExplorationEngine.calculateEvidence(exposedItem, tasteDNA, stats)
        val score = ExplorationEngine.calculatePolicyScore(evidence, strategy)

        val nonExposedEvidence = ExplorationEngine.calculateEvidence(favoriteItem, tasteDNA, stats)
        val nonExposedScore = ExplorationEngine.calculatePolicyScore(nonExposedEvidence, strategy)

        assertTrue("Exposed item should score lower due to redundancy penalty (Exposed: $score, Non-Exposed: $nonExposedScore)", score < nonExposedScore)
    }
}

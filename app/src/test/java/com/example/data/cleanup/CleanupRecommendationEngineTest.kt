package com.example.data.cleanup

import org.junit.Assert.*
import org.junit.Test

class CleanupRecommendationEngineTest {

    private fun createResult(
        id: String, 
        category: CleanupCategory = CleanupCategory.NONE,
        reasons: List<CleanupReason> = emptyList(),
        score: Float = 0.5f
    ) = KeepScoreResult(
        mediaId = id,
        keepScore = score,
        confidenceScore = 0.5f,
        reasons = reasons,
        category = category
    )

    private fun createMetadata(
        id: String,
        size: Long = 1024 * 1024,
        exposure: Int = 5,
        views: Int = 1,
        type: String = "PHOTO",
        hash: String? = null
    ) = CleanupItemMetadata(
        mediaId = id,
        sizeBytes = size,
        exposureCount = exposure,
        viewCount = views,
        mediaType = type,
        contentHash = hash,
        isFavorite = false
    )

    @Test
    fun testForgottenItem_CategorizedCorrectly() {
        val id = "forgotten-1"
        val results = listOf(createResult(id, CleanupCategory.FORGOTTEN))
        val metadata = mapOf(id to createMetadata(id, exposure = 50, views = 0))
        
        val recommendations = CleanupRecommendationEngine.generateRecommendations(results, metadata)
        
        assertEquals(1, recommendations.size)
        assertEquals(CleanupCategory.FORGOTTEN, recommendations[0].category)
        assertTrue(recommendations[0].explanation.contains("50 times"))
        assertTrue(recommendations[0].confidenceScore > 0.9f)
    }

    @Test
    fun testNeverConnectedItem_CategorizedCorrectly() {
        val id = "never-1"
        val results = listOf(createResult(id, CleanupCategory.NEVER_CONNECTED, listOf(CleanupReason.LOW_TASTE_ALIGNMENT)))
        val metadata = mapOf(id to createMetadata(id))
        
        val recommendations = CleanupRecommendationEngine.generateRecommendations(results, metadata)
        
        assertEquals(1, recommendations.size)
        assertEquals(CleanupCategory.NEVER_CONNECTED, recommendations[0].category)
        assertTrue(recommendations[0].explanation.contains("Low alignment"))
    }

    @Test
    fun testSpaceHog_CategorizedCorrectly() {
        val id = "huge-1"
        val size = 2L * 1024 * 1024 * 1024 // 2GB
        val results = listOf(createResult(id, CleanupCategory.SPACE_HOGS, score = 0.2f))
        val metadata = mapOf(id to createMetadata(id, size = size, type = "VIDEO"))
        
        val recommendations = CleanupRecommendationEngine.generateRecommendations(results, metadata)
        
        assertEquals(1, recommendations.size)
        assertEquals(CleanupCategory.SPACE_HOGS, recommendations[0].category)
        assertTrue(recommendations[0].explanation.contains("2048MB video"))
    }

    @Test
    fun testRedundantItem_CategorizedCorrectly() {
        val id1 = "dup-1"
        val id2 = "dup-2"
        val hash = "fixed-hash"
        
        val results = listOf(createResult(id1), createResult(id2))
        val metadata = mapOf(
            id1 to createMetadata(id1, hash = hash),
            id2 to createMetadata(id2, hash = hash)
        )
        
        val recommendations = CleanupRecommendationEngine.generateRecommendations(results, metadata)
        
        assertEquals(2, recommendations.size)
        assertEquals(CleanupCategory.REDUNDANT, recommendations[0].category)
        assertEquals(CleanupCategory.REDUNDANT, recommendations[1].category)
        assertEquals(1.0f, recommendations[0].confidenceScore)
    }

    @Test
    fun testHighValueItem_NoRecommendation() {
        val id = "gold-1"
        val results = listOf(createResult(id, CleanupCategory.NONE, score = 0.95f))
        val metadata = mapOf(id to createMetadata(id))
        
        val recommendations = CleanupRecommendationEngine.generateRecommendations(results, metadata)
        
        assertTrue(recommendations.isEmpty())
    }

    @Test
    fun testStorageCalculation() {
        val recommendations = listOf(
            CleanupRecommendation("1", 0.1f, 0.9f, CleanupCategory.FORGOTTEN, emptyList(), 100L, explanation = ""),
            CleanupRecommendation("2", 0.1f, 0.9f, CleanupCategory.FORGOTTEN, emptyList(), 200L, explanation = "")
        )
        
        val total = CleanupRecommendationEngine.calculatePotentialRecovery(recommendations)
        assertEquals(300L, total)
    }
}

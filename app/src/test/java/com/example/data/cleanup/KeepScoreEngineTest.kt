package com.example.data.cleanup

import org.junit.Assert.*
import org.junit.Test

class KeepScoreEngineTest {

    private val baseInput = KeepScoreInput(
        mediaId = "test-1",
        fileSize = 1024 * 1024,
        dateAdded = System.currentTimeMillis(),
        exposureCount = 5,
        lastExposedTimestamp = System.currentTimeMillis(),
        viewCount = 1,
        playCount = 1,
        averageWatchDuration = 10f,
        completionPercentage = 0.5f,
        skipCount = 0,
        rating = 0f,
        isFavorite = false,
        tasteAlignmentScore = 0.5f,
        contentHash = "hash-1"
    )

    @Test
    fun testFavorite_IsProtected() {
        val input = baseInput.copy(isFavorite = true, exposureCount = 50, viewCount = 0)
        val result = KeepScoreEngine.calculateScore(input)
        
        assertTrue("KeepScore should be >= 0.85 for favorites", result.keepScore >= 0.85f)
        assertTrue(result.reasons.contains(CleanupReason.FAVORITE_PROTECTED))
    }

    @Test
    fun testGhostItem_HasLowScore() {
        val input = baseInput.copy(
            exposureCount = 50,
            viewCount = 0,
            playCount = 0,
            completionPercentage = 0f,
            tasteAlignmentScore = 0.3f
        )
        val result = KeepScoreEngine.calculateScore(input)
        
        assertTrue("KeepScore should be low for ghost items (scrolled past many times, never opened)", result.keepScore < 0.30f)
        assertTrue(result.reasons.contains(CleanupReason.HIGH_EXPOSURE_NO_ENGAGEMENT))
        assertEquals(CleanupCategory.FORGOTTEN, result.category)
    }

    @Test
    fun testHighTasteMatch_IncreasesScore() {
        val neutralResult = KeepScoreEngine.calculateScore(baseInput.copy(tasteAlignmentScore = 0.5f))
        val highMatchResult = KeepScoreEngine.calculateScore(baseInput.copy(tasteAlignmentScore = 0.95f))
        
        assertTrue("Score should increase significantly for high taste match", highMatchResult.keepScore > neutralResult.keepScore + 0.10f)
        assertTrue(highMatchResult.reasons.contains(CleanupReason.HIGH_TASTE_MATCH))
    }

    @Test
    fun testRepeatedSkip_PenaltyApplied() {
        val input = baseInput.copy(
            averageWatchDuration = 1.2f,
            skipCount = 5
        )
        val result = KeepScoreEngine.calculateScore(input)
        
        val neutralResult = KeepScoreEngine.calculateScore(baseInput)
        assertTrue("Score should be lower due to repeated skips", result.keepScore < neutralResult.keepScore)
        assertTrue(result.reasons.contains(CleanupReason.REPEATED_SKIP))
        assertEquals(CleanupCategory.NEVER_CONNECTED, result.category)
    }

    @Test
    fun testLowRating_PenaltyApplied() {
        val input = baseInput.copy(rating = 1.0f)
        val result = KeepScoreEngine.calculateScore(input)
        
        assertTrue(result.reasons.contains(CleanupReason.LOW_USER_RATING))
        val neutralResult = KeepScoreEngine.calculateScore(baseInput)
        assertTrue("Score should be lower for low rating", result.keepScore < neutralResult.keepScore)
    }

    @Test
    fun testSpaceHog_CategorizedCorrectly() {
        val input = baseInput.copy(
            fileSize = 500 * 1024 * 1024, // 500MB
            viewCount = 0,
            playCount = 0,
            exposureCount = 2,
            tasteAlignmentScore = 0.2f
        )
        val result = KeepScoreEngine.calculateScore(input)
        
        assertEquals(CleanupCategory.SPACE_HOGS, result.category)
        assertTrue(result.reasons.contains(CleanupReason.LARGE_FILE_SIZE))
    }

    @Test
    fun testStaleMedia_PenaltyApplied() {
        val twoYearsAgo = System.currentTimeMillis() - (2L * 365 * 24 * 60 * 60 * 1000L)
        val input = baseInput.copy(
            dateAdded = twoYearsAgo,
            lastExposedTimestamp = twoYearsAgo,
            viewCount = 0
        )
        val result = KeepScoreEngine.calculateScore(input)
        
        assertTrue(result.reasons.contains(CleanupReason.STALE_MEDIA))
    }
}

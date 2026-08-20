package com.example.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PairwiseEloEngineTest {

    @Test
    fun testExpectedScore_EqualRatings() {
        val prob = PairwiseEloEngine.calculateExpectedScore(1500.0, 1500.0)
        assertEquals(0.5, prob, 0.001)
    }

    @Test
    fun testExpectedScore_LargeDifference() {
        // A is 400 points higher
        val probA = PairwiseEloEngine.calculateExpectedScore(1800.0, 1400.0)
        // 1 / (1 + 10^((1400-1800)/400)) = 1 / (1 + 10^-1) = 1 / 1.1 = 0.909...
        assertEquals(0.909, probA, 0.001)

        // A is 400 points lower
        val probB = PairwiseEloEngine.calculateExpectedScore(1400.0, 1800.0)
        // 1 / (1 + 10^((1800-1400)/400)) = 1 / (1 + 10^1) = 1 / 11 = 0.0909...
        assertEquals(0.0909, probB, 0.001)

        assertEquals(1.0, probA + probB, 0.001)
    }

    @Test
    fun testRatingUpdate_Win() {
        val oldRating = 1500.0
        val expected = 0.5
        val actual = 1.0 // Win
        val newRating = PairwiseEloEngine.calculateNewRating(oldRating, actual, expected)
        // 1500 + 32 * (1.0 - 0.5) = 1516
        assertEquals(1516.0, newRating, 0.001)
    }

    @Test
    fun testRatingUpdate_Loss() {
        val oldRating = 1500.0
        val expected = 0.5
        val actual = 0.0 // Loss
        val newRating = PairwiseEloEngine.calculateNewRating(oldRating, actual, expected)
        // 1500 + 32 * (0.0 - 0.5) = 1484
        assertEquals(1484.0, newRating, 0.001)
    }

    @Test
    fun testInformationValue() {
        val val50 = PairwiseEloEngine.calculateInformationValue(0.5)
        val val10 = PairwiseEloEngine.calculateInformationValue(0.1)
        val val90 = PairwiseEloEngine.calculateInformationValue(0.9)

        assertTrue("Maximum uncertainty at 0.5", val50 > val10)
        assertTrue("Maximum uncertainty at 0.5", val50 > val90)
        assertEquals(val10, val90, 0.001)
        
        // Ensure no crash at extremes
        PairwiseEloEngine.calculateInformationValue(0.0)
        PairwiseEloEngine.calculateInformationValue(1.0)
    }
}

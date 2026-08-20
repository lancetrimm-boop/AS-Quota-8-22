package com.example.data

import kotlin.math.pow

/**
 * True Elo Probability Model for Aura Pairwise personalization.
 * Implements the standard expected-score model and rating update logic.
 */
object PairwiseEloEngine {

    // Configuration
    const val INITIAL_ELO = 1500.0
    const val K_FACTOR = 32.0

    /**
     * Calculates the probability that Item A defeats Item B.
     * P(A beats B) = 1 / (1 + 10^((RatingB - RatingA) / 400))
     */
    fun calculateExpectedScore(ratingA: Double, ratingB: Double): Double {
        return 1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / 400.0))
    }

    /**
     * Calculates the new Elo rating after a comparison outcome.
     * NewRating = OldRating + K * (ActualScore - ExpectedScore)
     *
     * @param oldRating The current rating before the comparison.
     * @param actualScore 1.0 for a win, 0.0 for a loss, 0.5 for a draw/skip.
     * @param expectedScore The calculated probability of winning.
     */
    fun calculateNewRating(
        oldRating: Double,
        actualScore: Double,
        expectedScore: Double,
        kFactor: Double = K_FACTOR
    ): Double {
        val newRating = oldRating + kFactor * (actualScore - expectedScore)
        return if (newRating.isNaN() || newRating.isInfinite()) oldRating else newRating
    }

    /**
     * Calculates binary entropy for a given probability.
     * Used as an information-value signal for candidate selection.
     * H(p) = -p * log2(p) - (1-p) * log2(1-p)
     */
    fun calculateInformationValue(probability: Double): Double {
        val p = probability.coerceIn(0.0001, 0.9999)
        return -(p * kotlin.math.log2(p) + (1.0 - p) * kotlin.math.log2(1.0 - p))
    }
}

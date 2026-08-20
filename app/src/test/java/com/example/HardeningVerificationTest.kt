package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class HardeningVerificationTest {

    @Test
    fun testExplorationPropensity_ModulatesStrategy() {
        val policy = DiscoveryPolicy(mode = DiscoveryMode.BALANCED)
        val dnaLow = TasteDNA(explorationPropensity = 0.1)
        val dnaHigh = TasteDNA(explorationPropensity = 0.9)
        
        val strategyLow = DiscoveryPolicyManager.resolveStrategy(policy, tasteDNA = dnaLow)
        val strategyHigh = DiscoveryPolicyManager.resolveStrategy(policy, tasteDNA = dnaHigh)
        
        // Base BALANCED exploration is 0.3
        // DNA 0.1 should reduce it, DNA 0.9 should increase it.
        assertTrue("High propensity in DNA should have higher exploration weight than low. Low: ${strategyLow.explorationWeight}, High: ${strategyHigh.explorationWeight}",
            strategyHigh.explorationWeight > strategyLow.explorationWeight)
    }

    @Test
    fun testRatingLearning_PositiveReinforcement() {
        // Setup initial DNA
        val initialDna = TasteDNA(isFineTuningEnabled = true, learnedVibrancy = 0.5)
        val item = MediaItem(id = "1", title = "Vibrant", mediaType = "PHOTO", moodTags = listOf("vibrant"))
        
        // Manual test of repo updateRating logic
        val adjustments = PersonalizationTraitMapper.getTraitAdjustments(item.moodTags)
        var updatedDna = initialDna
        
        val rating = 5f
        val sentimentDirection = 1.0
        
        adjustments.forEach { (dim, multiplier) ->
            val amount = multiplier * MediaRepository.MAX_ADJUSTMENT_PER_VOTE * sentimentDirection
            updatedDna = updatedDna.updateLearnedDimension(dim, amount, MediaRepository.TOTAL_ADJUSTMENT_LIMIT)
        }
        
        assertTrue("Rating 5 should increase learned vibrancy", updatedDna.learnedVibrancy > initialDna.learnedVibrancy)
    }

    @Test
    fun testRatingLearning_NegativeReinforcement() {
        val initialDna = TasteDNA(isFineTuningEnabled = true, learnedVibrancy = 0.5)
        val item = MediaItem(id = "1", title = "Vibrant", mediaType = "PHOTO", moodTags = listOf("vibrant"))
        
        val adjustments = PersonalizationTraitMapper.getTraitAdjustments(item.moodTags)
        var updatedDna = initialDna
        
        val rating = 1f
        val sentimentDirection = -1.0
        
        adjustments.forEach { (dim, multiplier) ->
            val amount = multiplier * MediaRepository.MAX_ADJUSTMENT_PER_VOTE * sentimentDirection
            updatedDna = updatedDna.updateLearnedDimension(dim, amount, MediaRepository.TOTAL_ADJUSTMENT_LIMIT)
        }
        
        assertTrue("Rating 1 should decrease learned vibrancy", updatedDna.learnedVibrancy < initialDna.learnedVibrancy)
    }

    @Test
    fun testContrastLearning_HighContrastChoice() {
        val initialDna = TasteDNA(isFineTuningEnabled = true, learnedVibrancy = 0.5)
        
        // Winner is Vibrant (1.0), Loser is Muted (-1.0)
        val winner = MediaItem(id = "W", title = "Winner", mediaType = "PHOTO", moodTags = listOf("vibrant"))
        val loser = MediaItem(id = "L", title = "Loser", mediaType = "PHOTO", moodTags = listOf("muted"))
        
        val winnerTraits = PersonalizationTraitMapper.getTraitAdjustments(winner.moodTags)
        val loserTraits = PersonalizationTraitMapper.getTraitAdjustments(loser.moodTags)
        
        var updatedDna = initialDna
        val allAffectedDimensions = (winnerTraits.keys + loserTraits.keys).distinct()
        
        allAffectedDimensions.forEach { dim ->
            val valWinner = winnerTraits[dim] ?: 0.0
            val valLoser = loserTraits[dim] ?: 0.0
            val contrast = valWinner - valLoser
            
            if (contrast != 0.0) {
                val amount = contrast * MediaRepository.MAX_ADJUSTMENT_PER_VOTE
                updatedDna = updatedDna.updateLearnedDimension(dim, amount, MediaRepository.TOTAL_ADJUSTMENT_LIMIT)
            }
        }
        
        // Vibrancy trait value is (presence + 1.0) / 2.0. 
        // "vibrant" tag = 1.0 weight -> trait presence 1.0
        // "muted" tag = -1.0 weight -> trait presence -1.0
        // Contrast = 1.0 - (-1.0) = 2.0
        // Adjustment = 2.0 * 0.01 = 0.02
        
        assertTrue("High contrast choice should increase learned vibrancy significantly", 
            updatedDna.learnedVibrancy > 0.51) // Base + 0.02
        assertEquals(0.52, updatedDna.learnedVibrancy, 0.001)
    }
}

package com.example

import com.example.data.PersonalizationTraitMapper
import com.example.data.TasteDNA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationAuditTest {

    @Test
    fun `test Tag to Trait Mapping - Single Tag`() {
        val tags = listOf("Vibrant")
        val adjustments = PersonalizationTraitMapper.getTraitAdjustments(tags)
        
        assertEquals(1.0, adjustments["vibrancy"]!!, 0.001)
    }

    @Test
    fun `test Tag to Trait Mapping - Conflicting Tags`() {
        // Vibrant (+1) + Muted (-1) = 0
        val tags = listOf("Vibrant", "Muted")
        val adjustments = PersonalizationTraitMapper.getTraitAdjustments(tags)
        
        assertEquals(0.0, adjustments["vibrancy"]!!, 0.001)
    }

    @Test
    fun `test Tag to Trait Mapping - Normalization`() {
        // Multiple vibrant tags should not exceed 1.0
        val tags = listOf("Vibrant", "Vivid", "Vibrant")
        val adjustments = PersonalizationTraitMapper.getTraitAdjustments(tags)
        
        assertEquals(1.0, adjustments["vibrancy"]!!, 0.001)
    }

    @Test
    fun `test TasteDNA Dynamic Update - Incremental Learning`() {
        var dna = TasteDNA(isFineTuningEnabled = true, vibrancy = 0.5, learnedVibrancy = 0.5)
        
        // AI Skip Behavior or Pairwise Vote Adjustment
        val amount = 0.1
        dna = dna.updateLearnedDimension("vibrancy", amount, 0.15)
        
        assertEquals(0.6, dna.learnedVibrancy, 0.001)
        assertEquals(0.55, dna.effectiveVibrancy, 0.001)
    }

    @Test
    fun `test TasteDNA Dynamic Update - Drift Limit`() {
        var dna = TasteDNA(isFineTuningEnabled = true, vibrancy = 0.5, learnedVibrancy = 0.5)
        
        // Large adjustment should be capped by TOTAL_ADJUSTMENT_LIMIT (e.g. 0.15)
        val largeAmount = 0.5
        dna = dna.updateLearnedDimension("vibrancy", largeAmount, 0.15)
        
        assertEquals(0.65, dna.learnedVibrancy, 0.001)
    }

    @Test
    fun `test Behavioral Mapping - Mood Vector`() {
        val tags = listOf("Bold", "Intense")
        val adjustments = PersonalizationTraitMapper.getTraitAdjustments(tags)
        
        // Bold contributes 0.8 to mood
        // Intense contributes 1.0 to mood
        // mood = 0.8 + 1.0 = 1.8 -> capped at 1.0
        
        assertEquals(1.0, adjustments["mood"]!!, 0.001)
    }
}

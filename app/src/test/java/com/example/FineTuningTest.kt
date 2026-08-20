package com.example

import com.example.data.TasteDNA
import org.junit.Assert.*
import org.junit.Test

class FineTuningTest {

    @Test
    fun `test Authority Model - Manual movement resets baseline and re-anchors learning`() {
        val initial = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.6 // AI has adjusted it up
        )
        
        assertEquals(0.55, initial.effectiveVibrancy, 0.001)
        
        // User moves slider to 0.7
        val updated = initial.updateBaseline(newVibrancy = 0.7)
        
        assertEquals(0.7, updated.vibrancy, 0.001)
        assertEquals(0.7, updated.learnedVibrancy, 0.001)
        assertEquals(0.7, updated.effectiveVibrancy, 0.001)
    }

    @Test
    fun `test Opt-In - Fine tuning disabled returns baseline`() {
        val dna = TasteDNA(
            isFineTuningEnabled = false,
            vibrancy = 0.5,
            learnedVibrancy = 0.6
        )
        
        assertEquals(0.5, dna.effectiveVibrancy, 0.001)
    }

    @Test
    fun `test Reset - Resets adjustments but preserves baseline`() {
        val dna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.5,
            learnedVibrancy = 0.6,
            naturalism = 0.8,
            learnedNaturalism = 0.4
        )
        
        val reset = dna.resetFineTuning()
        
        assertEquals(0.5, reset.vibrancy, 0.001)
        assertEquals(0.5, reset.learnedVibrancy, 0.001)
        assertEquals(0.8, reset.naturalism, 0.001)
        assertEquals(0.8, reset.learnedNaturalism, 0.001)
    }

    @Test
    fun `test Semantic Model - Effective is average of baseline and learned`() {
        val dna = TasteDNA(
            isFineTuningEnabled = true,
            vibrancy = 0.4,
            learnedVibrancy = 0.6
        )
        
        assertEquals(0.5, dna.effectiveVibrancy, 0.001)
    }
}

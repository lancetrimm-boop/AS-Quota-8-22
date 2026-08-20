package com.example.data

import org.junit.Assert.*
import org.junit.Test

class TasteRevealTest {

    private val highConfidenceStats = IntelligenceStats(
        personalizationScore = 85,
        totalComparisons = 50
    )
    
    private val lowConfidenceStats = IntelligenceStats(
        personalizationScore = 40,
        totalComparisons = 5
    )

    @Test
    fun testGenerate_HighConfidence_ReturnsReveal() {
        val tasteDNA = TasteDNA(
            vibrancy = 0.8,
            learnedVibrancy = 0.8,
            isFineTuningEnabled = true
        )
        val profile = TasteDNA.PreferenceProfile()

        val reveal = TasteRevealGenerator.generate(tasteDNA, highConfidenceStats, profile)
        
        assertNotNull(reveal)
        assertEquals("Color Enthusiast", reveal?.persona)
        assertTrue(reveal?.primaryTraits?.contains("Vibrant") == true)
    }

    @Test
    fun testGenerate_LowConfidence_ReturnsNull() {
        val tasteDNA = TasteDNA()
        val profile = TasteDNA.PreferenceProfile()

        val reveal = TasteRevealGenerator.generate(tasteDNA, lowConfidenceStats, profile)
        
        assertNull(reveal)
    }

    @Test
    fun testGenerate_CinematicDreamer_Persona() {
        val tasteDNA = TasteDNA(
            lighting = 0.9,
            learnedLighting = 0.9,
            isFineTuningEnabled = true
        )
        val profile = TasteDNA.PreferenceProfile()

        val reveal = TasteRevealGenerator.generate(tasteDNA, highConfidenceStats, profile)
        
        assertEquals("Cinematic Dreamer", reveal?.persona)
    }
}

package com.example.data

/**
 * Narrative representation of Aura's internal intelligence for the user.
 */
data class TasteReveal(
    val persona: String,
    val primaryTraits: List<String>,
    val discoveryStyle: String,
    val confidence: Float
)

object TasteRevealGenerator {

    /**
     * Generates a Taste Reveal based on the current intelligence state.
     * Returns null if system confidence is below threshold.
     */
    fun generate(
        tasteDNA: TasteDNA,
        stats: IntelligenceStats,
        profile: TasteDNA.PreferenceProfile
    ): TasteReveal? {
        // Threshold: Need at least 25 comparisons or 70% accuracy for a meaningful reveal
        if (stats.totalComparisons < 25 && stats.personalizationScore < 70) return null

        val traits = mutableListOf<String>()
        
        // 1. Map DNA to user-friendly traits (Top 3 most distinct preferences)
        val dnaMatches = listOf(
            "Cinematic" to tasteDNA.effectiveLighting,
            "Vibrant" to tasteDNA.effectiveVibrancy,
            "Minimalist" to tasteDNA.effectiveMinimalism,
            "Atmospheric" to tasteDNA.effectiveMood,
            "Sharp & Detailed" to tasteDNA.effectiveSharpness,
            "Natural" to tasteDNA.effectiveNaturalism,
            "Experimental" to tasteDNA.effectiveNovelty,
            "Classic" to (1.0 - tasteDNA.effectiveNovelty)
        ).filter { it.second > 0.7 || it.second < 0.3 }
        
        dnaMatches.sortedByDescending { Math.abs(it.second - 0.5) }
            .take(3)
            .forEach { traits.add(it.first) }

        // 2. Determine Persona
        val persona = when {
            tasteDNA.effectiveNovelty > 0.7 -> "Explorer"
            tasteDNA.effectiveLighting > 0.7 -> "Cinematic Dreamer"
            tasteDNA.effectiveVibrancy > 0.7 -> "Color Enthusiast"
            tasteDNA.effectiveNaturalism > 0.7 -> "Realist"
            else -> "Visual Storyteller"
        }

        // 3. Discovery Style based on Preference Profile
        val style = when {
            profile.noveltyWeight > 0.2 -> "You prefer familiar themes presented in unexpected ways."
            profile.diversityWeight > 0.3 -> "You enjoy a wide range of styles and perspectives."
            profile.contentSimilarityWeight > 0.5 -> "You have a very specific vision for what you love."
            else -> "You value depth and consistency in your visual journey."
        }

        return TasteReveal(
            persona = persona,
            primaryTraits = traits,
            discoveryStyle = style,
            confidence = stats.personalizationScore / 100f
        )
    }
}

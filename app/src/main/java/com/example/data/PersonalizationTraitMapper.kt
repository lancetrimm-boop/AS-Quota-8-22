package com.example.data

/**
 * Centralized registry for mapping AI tags and visual metadata to Taste DNA traits.
 * Supports weighted multi-trait contributions and semantic normalization.
 */
object PersonalizationTraitMapper {

    data class TraitContribution(val dimension: String, val weight: Double)

    private val tagMap = mapOf(
        // Low-Level Visual
        "vibrant" to listOf(TraitContribution("vibrancy", 1.0), TraitContribution("saturation", 0.5)),
        "vivid" to listOf(TraitContribution("vibrancy", 1.0), TraitContribution("saturation", 0.8)),
        "muted" to listOf(TraitContribution("vibrancy", -1.0), TraitContribution("saturation", -0.5)),
        "warm" to listOf(TraitContribution("colorTemperature", 1.0), TraitContribution("warmth", 0.8)),
        "cool" to listOf(TraitContribution("colorTemperature", -1.0), TraitContribution("warmth", -0.5)),
        "bright" to listOf(TraitContribution("dynamicRange", 1.0), TraitContribution("lighting", 0.5)),
        "dark" to listOf(TraitContribution("dynamicRange", -1.0), TraitContribution("lighting", -0.5)),
        "shadow" to listOf(TraitContribution("dynamicRange", -0.8), TraitContribution("lighting", 0.5)),
        "dramatic" to listOf(TraitContribution("contrast", 1.0), TraitContribution("lighting", 0.8)),
        "contrast" to listOf(TraitContribution("contrast", 1.0)),
        "soft" to listOf(TraitContribution("contrast", -0.8), TraitContribution("lighting", -0.8)),
        "texture" to listOf(TraitContribution("texture", 1.0)),
        "tactile" to listOf(TraitContribution("texture", 1.0)),
        "smooth" to listOf(TraitContribution("texture", -1.0)),
        "sharp" to listOf(TraitContribution("sharpness", 1.0), TraitContribution("focus", 0.5)),
        "crisp" to listOf(TraitContribution("sharpness", 1.0), TraitContribution("focus", 0.8)),
        "blurry" to listOf(TraitContribution("sharpness", -1.0), TraitContribution("focus", -1.0)),

        // Compositional
        "formal" to listOf(TraitContribution("framing", 0.8), TraitContribution("harmony", 0.5)),
        "symmetric" to listOf(TraitContribution("symmetry", 1.0), TraitContribution("harmony", 0.5)),
        "asymmetric" to listOf(TraitContribution("symmetry", -1.0)),
        "dynamic" to listOf(TraitContribution("motion", 0.8), TraitContribution("rhythm", 0.5)),
        "spacious" to listOf(TraitContribution("framing", 1.0)),
        "minimalist" to listOf(TraitContribution("minimalism", 1.0), TraitContribution("density", -1.0), TraitContribution("complexity", -1.0)),
        "dense" to listOf(TraitContribution("density", 1.0)),
        "busy" to listOf(TraitContribution("density", 0.8), TraitContribution("complexity", 0.8)),
        "complex" to listOf(TraitContribution("complexity", 1.0)),
        "intricate" to listOf(TraitContribution("complexity", 1.0)),
        "cinematic" to listOf(TraitContribution("depth", 1.0), TraitContribution("lighting", 0.5), TraitContribution("dynamicRange", 0.5)),
        "depth" to listOf(TraitContribution("depth", 1.0)),
        "flat" to listOf(TraitContribution("depth", -1.0)),
        "dominant" to listOf(TraitContribution("focus", 1.0)),
        "close-up" to listOf(TraitContribution("focus", 1.0), TraitContribution("depth", 0.5)),
        "embedded" to listOf(TraitContribution("focus", -1.0)),
        "background" to listOf(TraitContribution("focus", -0.8)),

        // Semantic / Aesthetic
        "natural" to listOf(TraitContribution("naturalism", 1.0)),
        "organic" to listOf(TraitContribution("naturalism", 0.8), TraitContribution("harmony", 0.3)),
        "stylized" to listOf(TraitContribution("naturalism", -1.0), TraitContribution("elegance", 0.5)),
        "abstract" to listOf(TraitContribution("naturalism", -0.8)),
        "artistic" to listOf(TraitContribution("naturalism", -0.5), TraitContribution("elegance", 0.8)),
        "candid" to listOf(TraitContribution("elegance", -0.5), TraitContribution("naturalism", 0.5)),
        "real" to listOf(TraitContribution("naturalism", 1.0)),
        "polished" to listOf(TraitContribution("elegance", 1.0)),
        "studio" to listOf(TraitContribution("elegance", 0.8), TraitContribution("lighting", 0.8)),
        "experimental" to listOf(TraitContribution("novelty", 1.0)),
        "unique" to listOf(TraitContribution("novelty", 0.8)),
        "weird" to listOf(TraitContribution("novelty", 0.5)),
        "familiar" to listOf(TraitContribution("novelty", -1.0)),
        "classic" to listOf(TraitContribution("novelty", -0.8), TraitContribution("warmth", 0.5)),
        "retro" to listOf(TraitContribution("warmth", 0.8), TraitContribution("grain", 0.8)),
        "vintage" to listOf(TraitContribution("warmth", 1.0), TraitContribution("grain", 1.0)),
        "nostalgic" to listOf(TraitContribution("warmth", 0.8)),
        "modern" to listOf(TraitContribution("minimalism", 0.5), TraitContribution("sharpness", 0.5)),
        "contemporary" to listOf(TraitContribution("minimalism", 0.3)),
        "maximalist" to listOf(TraitContribution("minimalism", -1.0), TraitContribution("complexity", 1.0)),
        "bold" to listOf(TraitContribution("contrast", 0.8), TraitContribution("mood", 0.8)),
        "intense" to listOf(TraitContribution("mood", 1.0), TraitContribution("motion", 0.5)),
        "high-energy" to listOf(TraitContribution("mood", 1.0), TraitContribution("motion", 1.0)),
        "relaxed" to listOf(TraitContribution("mood", -1.0), TraitContribution("harmony", 0.5)),
        "calm" to listOf(TraitContribution("mood", -1.0), TraitContribution("harmony", 0.8)),
        "serene" to listOf(TraitContribution("mood", -0.8), TraitContribution("harmony", 1.0)),
        
        // New explicit mappings
        "motion" to listOf(TraitContribution("motion", 1.0)),
        "action" to listOf(TraitContribution("motion", 1.0)),
        "saturation" to listOf(TraitContribution("saturation", 1.0)),
        "grain" to listOf(TraitContribution("grain", 1.0)),
        "noisy" to listOf(TraitContribution("grain", 0.8)),
        "pattern" to listOf(TraitContribution("rhythm", 1.0)),
        "repetitive" to listOf(TraitContribution("rhythm", 0.8)),
        "peaceful" to listOf(TraitContribution("harmony", 1.0)),
        "harmony" to listOf(TraitContribution("harmony", 1.0))
    )

    /**
     * Extracts a set of weighted dimension adjustments from a list of tags.
     */
    fun getTraitAdjustments(tags: List<String>): Map<String, Double> {
        val adjustments = mutableMapOf<String, Double>()
        tags.forEach { rawTag ->
            val tag = rawTag.lowercase().trim()
            tagMap[tag]?.forEach { contribution ->
                val current = adjustments.getOrDefault(contribution.dimension, 0.0)
                adjustments[contribution.dimension] = current + contribution.weight
            }
        }
        
        // Normalize: Cap contribution at +/- 1.0 per dimension per item to avoid runaway inflation
        return adjustments.mapValues { it.value.coerceIn(-1.0, 1.0) }
    }

    /**
     * Enhanced extraction that uses all available metadata when tags are missing.
     */
    fun getEffectiveTraitAdjustments(item: MediaItem): Map<String, Double> {
        val adjustments = getTraitAdjustments(item.moodTags).toMutableMap()
        
        // 1. Category hints
        val categoryTags = when (item.category.lowercase().trim()) {
            "nature", "outdoors" -> listOf("natural", "harmony")
            "cinematic", "drama" -> listOf("dramatic", "depth")
            "vibrant", "color" -> listOf("vivid", "saturation")
            "minimalist", "clean" -> listOf("minimalist", "framing")
            "retro", "vintage" -> listOf("retro", "warmth")
            "action", "energy" -> listOf("motion", "intense")
            "calm", "relax" -> listOf("calm", "serene")
            "urban", "city" -> listOf("dense", "complexity")
            "art", "creative" -> listOf("artistic", "experimental")
            else -> emptyList()
        }
        
        // 2. Filename hints
        val title = item.title.lowercase()
        val titleHints = mutableListOf<String>()
        if (title.contains("dark") || title.contains("night")) titleHints.add("dark")
        if (title.contains("bright") || title.contains("sun")) titleHints.add("bright")
        if (title.contains("old") || title.contains("classic") || title.contains("vintage")) titleHints.add("classic")
        if (title.contains("new") || title.contains("modern")) titleHints.add("modern")
        if (title.contains("soft") || title.contains("blur")) titleHints.add("soft")
        if (title.contains("hard") || title.contains("edge")) titleHints.add("dramatic")
        
        // 3. Media Type baseline
        if (item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.equals("Movie", ignoreCase = true)) {
            val current = adjustments.getOrDefault("mood", 0.0)
            adjustments["mood"] = (current + 0.1).coerceIn(-1.0, 1.0)
            
            // Motion is high for video
            val motionVal = adjustments.getOrDefault("motion", 0.0)
            adjustments["motion"] = (motionVal + 0.3).coerceIn(-1.0, 1.0)

            if (item.durationMs > 60000) {
                val depthVal = adjustments.getOrDefault("depth", 0.0)
                adjustments["depth"] = (depthVal + 0.2).coerceIn(-1.0, 1.0)
            }
        } else {
            // Photos are often more harmonious/static
            val harmonyVal = adjustments.getOrDefault("harmony", 0.0)
            adjustments["harmony"] = (harmonyVal + 0.05).coerceIn(-1.0, 1.0)
        }

        // Apply derived hints
        (categoryTags + titleHints).forEach { hint ->
            tagMap[hint]?.forEach { contribution ->
                val current = adjustments.getOrDefault(contribution.dimension, 0.0)
                adjustments[contribution.dimension] = (current + contribution.weight * 0.4).coerceIn(-1.0, 1.0)
            }
        }

        return adjustments
    }
}

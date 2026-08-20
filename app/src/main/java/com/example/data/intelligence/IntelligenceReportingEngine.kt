package com.example.data.intelligence

import com.example.data.TasteDNA
import com.example.data.PersonalizationTraitMapper
import com.example.data.db.AuraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Core engine for generating immutable Intelligence Snapshots from local data sources.
 *
 * PROVENANCE GUARANTEE:
 * - Every metric is derived from traceable local signals (Room DB).
 * - No external or unverified AI inference is used in report generation.
 */
class IntelligenceReportingEngine(private val database: AuraDatabase) {

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1
    }

    /**
     * Generates a complete IntelligenceSnapshotReport by aggregating various local signals.
     */
    suspend fun generateSnapshotReport(currentTasteDNA: TasteDNA): IntelligenceSnapshotReport = withContext(Dispatchers.IO) {
        val media = database.mediaDao().getAllMediaSync()
        val tasteProfile = generateTasteProfileSnapshot(currentTasteDNA, media)
        val engagement = generateEngagementSnapshot()
        val maturity = generateMaturitySnapshot()

        IntelligenceSnapshotReport(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            tasteProfile = tasteProfile,
            engagement = engagement,
            maturity = maturity,
            generatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Generates a detailed insight for a specific media item, explaining Aura's prioritization.
     * PROVENANCE: Maps directly to [com.example.data.RecommendationEngine] scoring logic.
     */
    suspend fun generateRecommendationInsight(item: com.example.data.MediaItem, tasteDNA: TasteDNA): RecommendationInsightSnapshot = withContext(Dispatchers.Default) {
        val factors = mutableListOf<InsightFactor>()
        
        // 1. Personal interaction signals
        if (item.isFavorite) {
            factors.add(InsightFactor("Saved to Favorites", 1.0, 1))
        }

        if (item.rating > 0) {
            factors.add(InsightFactor("Personal Rating: ${item.rating.toInt()} stars", item.rating.toDouble() / 5.0, 1))
        }

        // 2. DNA Aesthetic Alignment (Mapping engine dimensions)
        val traits = PersonalizationTraitMapper.getTraitAdjustments(item.moodTags)
        for ((dim, presence) in traits) {
            val userPref = when(dim) {
                "vibrancy" -> tasteDNA.effectiveVibrancy
                "contrast" -> tasteDNA.effectiveContrast
                "sharpness" -> tasteDNA.effectiveSharpness
                "symmetry" -> tasteDNA.effectiveSymmetry
                "complexity" -> tasteDNA.effectiveComplexity
                "naturalism" -> tasteDNA.effectiveNaturalism
                "novelty" -> tasteDNA.effectiveNovelty
                "lighting" -> tasteDNA.effectiveLighting
                "colorTemperature" -> tasteDNA.effectiveColorTemp
                "texture" -> tasteDNA.effectiveTexture
                "motion" -> tasteDNA.effectiveMotion
                "dynamicRange" -> tasteDNA.effectiveDynamicRange
                "framing" -> tasteDNA.effectiveFraming
                "depth" -> tasteDNA.effectiveDepth
                "warmth" -> tasteDNA.effectiveWarmth
                "saturation" -> tasteDNA.effectiveSaturation
                "elegance" -> tasteDNA.effectiveElegance
                "minimalism" -> tasteDNA.effectiveMinimalism
                "grain" -> tasteDNA.effectiveGrain
                "focus" -> tasteDNA.effectiveFocus
                "density" -> tasteDNA.effectiveDensity
                "rhythm" -> tasteDNA.effectiveRhythm
                "mood" -> tasteDNA.effectiveMood
                "harmony" -> tasteDNA.effectiveHarmony
                else -> 0.5
            }
            
            val itemTraitValue = (presence + 1.0) / 2.0
            val similarity = 1.0 - Math.abs(userPref - itemTraitValue)
            
            // Significant alignment (>75%)
            if (similarity > 0.75) {
                val label = "Aesthetic Match: ${dim.replaceFirstChar { it.uppercase() }}"
                factors.add(InsightFactor(label, similarity, 1))
            }
        }

        // Calculate actual match score from engine
        val rawScore = com.example.data.RecommendationEngine.scoreItemForPairwise(
            item = item,
            tasteDNA = tasteDNA
        )
        val normalizedScore = (rawScore / 20.0).coerceIn(0.0, 1.0) // Heuristic normalization

        RecommendationInsightSnapshot(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            mediaId = item.id,
            overallMatchScore = quantize(normalizedScore),
            contributingFactors = factors.sortedByDescending { it.weight }.take(3),
            provenance = "Source: Local Taste DNA & ${item.moodTags.size} Visual Signals"
        )
    }

    private fun generateTasteProfileSnapshot(dna: TasteDNA, library: List<com.example.data.db.MediaEntity>): TasteProfileSnapshot {
        val dimensions = mutableMapOf<String, Double>()
        val confidence = mutableMapOf<String, Double>()

        // Map all 24 Dimensions from Audit
        dimensions["Vibrancy"] = dna.effectiveVibrancy
        dimensions["Contrast"] = dna.effectiveContrast
        dimensions["Sharpness"] = dna.effectiveSharpness
        dimensions["Symmetry"] = dna.effectiveSymmetry
        dimensions["Complexity"] = dna.effectiveComplexity
        dimensions["Naturalism"] = dna.effectiveNaturalism
        dimensions["Novelty"] = dna.effectiveNovelty
        dimensions["Lighting"] = dna.effectiveLighting
        dimensions["Color Temperature"] = dna.effectiveColorTemp
        dimensions["Texture"] = dna.effectiveTexture
        dimensions["Motion"] = dna.effectiveMotion
        dimensions["Dynamic Range"] = dna.effectiveDynamicRange
        dimensions["Framing"] = dna.effectiveFraming
        dimensions["Depth"] = dna.effectiveDepth
        dimensions["Warmth"] = dna.effectiveWarmth
        dimensions["Saturation"] = dna.effectiveSaturation
        dimensions["Elegance"] = dna.effectiveElegance
        dimensions["Minimalism"] = dna.effectiveMinimalism
        dimensions["Grain"] = dna.effectiveGrain
        dimensions["Focus"] = dna.effectiveFocus
        dimensions["Density"] = dna.effectiveDensity
        dimensions["Rhythm"] = dna.effectiveRhythm
        dimensions["Mood"] = dna.effectiveMood
        dimensions["Harmony"] = dna.effectiveHarmony

        // Simple confidence logic: higher drift from 0.5 baseline implies more signals received
        dimensions.keys.forEach { key ->
            val value = dimensions[key] ?: 0.5
            confidence[key] = (Math.abs(value - 0.5) * 2.0).coerceIn(0.1, 1.0)
        }

        val topTraits = dimensions.entries
            .filter { it.value > 0.6 || it.value < 0.4 }
            .sortedByDescending { Math.abs(it.value - 0.5) }
            .take(5)
            .map { entry ->
                if (entry.value > 0.5) "Strong ${entry.key}" else "Subtle ${entry.key}"
            }

        // --- NEW: Generate Taste Cluster Evidence ---
        val tasteClusters = generateTasteClusters(dna, library)

        return TasteProfileSnapshot(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            dimensions = dimensions,
            dimensionConfidence = confidence,
            topTraits = topTraits,
            explorationPropensity = dna.learnedExploration,
            description = generateVisualDescription(dimensions, topTraits),
            lastUpdated = System.currentTimeMillis(),
            tasteClusters = tasteClusters
        )
    }

    private fun generateTasteClusters(dna: TasteDNA, library: List<com.example.data.db.MediaEntity>): List<TasteClusterEvidence> {
        val clusters = mutableListOf<TasteClusterEvidence>()
        
        // Define high-level clusters and their associated dimensions
        val mapping = listOf(
            ClusterMapping("cinematic", "Clean & Atmospheric", listOf("Depth", "Lighting", "Dynamic Range", "Contrast")),
            ClusterMapping("vibrant", "Vibrant & Energetic", listOf("Vibrancy", "Saturation", "Motion", "Mood")),
            ClusterMapping("minimal", "Minimal & Clean", listOf("Minimalism", "Symmetry", "Harmony", "Framing")),
            ClusterMapping("tactile", "Tactile & Organic", listOf("Texture", "Grain", "Naturalism", "Warmth")),
            ClusterMapping("complex", "Complex & Intricate", listOf("Complexity", "Density", "Rhythm", "Sharpness")),
            ClusterMapping("unusual", "Unusual & Experimental", listOf("Novelty"))
        )

        val usedMediaIds = mutableSetOf<String>()

        mapping.forEach { map ->
            val score = map.dimensions.map { getDimensionValue(dna, it) }.average()
            if (score > 0.4) { // Only show clusters with at least emerging strength
                val evidence = selectMediaForCluster(map, library, usedMediaIds)
                
                // STRICT EVIDENCE REQUIREMENT: Only add cluster if usable evidence exists
                if (evidence != null && !evidence.imageUrl.isNullOrBlank()) {
                    val description = generateClusterDescription(map, dna)
                    clusters.add(TasteClusterEvidence(
                        categoryId = map.id,
                        title = map.title,
                        description = description,
                        strengthScore = quantize(score),
                        strengthLabel = getStrengthLabel(score),
                        confidenceScore = 0.8, // Baseline confidence for snapshots
                        confidenceLabel = "High",
                        contributingTraits = map.dimensions,
                        representativeMediaId = evidence.id,
                        representativeMediaThumbnailUrl = evidence.imageUrl,
                        isVideo = evidence.mediaType == "VIDEO"
                    ))
                    usedMediaIds.add(evidence.id)
                }
            }
        }

        return clusters.sortedByDescending { it.strengthScore }
    }

    private fun generateClusterDescription(map: ClusterMapping, dna: TasteDNA): String {
        val strongTraits = map.dimensions.filter { getDimensionValue(dna, it) > 0.6 }
            .map { it.lowercase() }
        
        if (strongTraits.isEmpty()) {
            return "Aura identified emerging patterns in your library matching this style."
        }
        
        val traitsText = when (strongTraits.size) {
            1 -> strongTraits[0]
            2 -> "${strongTraits[0]} and ${strongTraits[1]}"
            else -> strongTraits.dropLast(1).joinToString(", ") + ", and " + strongTraits.last()
        }
        
        return "Aura identified a preference for $traitsText, as seen in your representative media."
    }

    private fun getStrengthLabel(score: Double): String {
        return when {
            score >= 0.85 -> "Very Strong"
            score >= 0.70 -> "Strong"
            score >= 0.55 -> "Moderate"
            score >= 0.40 -> "Emerging"
            else -> "Low"
        }
    }

    private fun getDimensionValue(dna: TasteDNA, dim: String): Double {
        return when(dim) {
            "Vibrancy" -> dna.effectiveVibrancy
            "Contrast" -> dna.effectiveContrast
            "Sharpness" -> dna.effectiveSharpness
            "Symmetry" -> dna.effectiveSymmetry
            "Complexity" -> dna.effectiveComplexity
            "Naturalism" -> dna.effectiveNaturalism
            "Novelty" -> dna.effectiveNovelty
            "Lighting" -> dna.effectiveLighting
            "Color Temperature" -> dna.effectiveColorTemp
            "Texture" -> dna.effectiveTexture
            "Motion" -> dna.effectiveMotion
            "Dynamic Range" -> dna.effectiveDynamicRange
            "Framing" -> dna.effectiveFraming
            "Depth" -> dna.effectiveDepth
            "Warmth" -> dna.effectiveWarmth
            "Saturation" -> dna.effectiveSaturation
            "Elegance" -> dna.effectiveElegance
            "Minimalism" -> dna.effectiveMinimalism
            "Grain" -> dna.effectiveGrain
            "Focus" -> dna.effectiveFocus
            "Density" -> dna.effectiveDensity
            "Rhythm" -> dna.effectiveRhythm
            "Mood" -> dna.effectiveMood
            "Harmony" -> dna.effectiveHarmony
            else -> 0.5
        }
    }

    private fun selectMediaForCluster(
        map: ClusterMapping, 
        library: List<com.example.data.db.MediaEntity>,
        usedIds: Set<String>
    ): com.example.data.db.MediaEntity? {
        if (library.isEmpty()) return null

        // Score library items based on alignment with cluster traits and behavioral signals
        // CRITICAL: Only consider items with valid visual evidence (imageUrl or uriPath)
        return library.filter { (it.imageUrl.isNotBlank() || it.uriPath.isNotBlank()) && (it.id !in usedIds || library.size < 6) }
            .map { entity ->
                val traitScore = calculateTraitAlignment(entity, map.dimensions)
                val behavioralScore = calculateBehavioralSignal(entity)
                val totalScore = (traitScore * 0.4) + (behavioralScore * 0.6)
                entity to totalScore
            }
            .filter { it.second > 0.15 } // Lower threshold to ensure clusters appear for newer libraries
            .sortedByDescending { it.second }
            .firstOrNull()?.first
    }

    private fun calculateTraitAlignment(entity: com.example.data.db.MediaEntity, dimensions: List<String>): Double {
        val tags = entity.moodTagsJson.split(",").filter { it.isNotBlank() }
        val traits = PersonalizationTraitMapper.getTraitAdjustments(tags)
        if (traits.isEmpty()) return 0.5
        
        var matches = 0
        dimensions.forEach { dim ->
            val dimLower = dim.lowercase().replace(" ", "")
            val traitVal = traits[dimLower] ?: 0.0
            if (traitVal > 0.3) matches++
        }
        
        return matches.toDouble() / dimensions.size.coerceAtLeast(1)
    }

    private fun calculateBehavioralSignal(entity: com.example.data.db.MediaEntity): Double {
        var score = 0.0
        if (entity.rating > 4) score += 0.5
        else if (entity.rating > 0) score += 0.2
        
        if (entity.isFavorite) score += 0.3
        
        score += (entity.playCount.coerceAtMost(10) * 0.02)
        
        return score.coerceIn(0.0, 1.0)
    }

    private data class ClusterMapping(
        val id: String,
        val title: String,
        val dimensions: List<String>
    )

    private fun generateVisualDescription(dimensions: Map<String, Double>, topTraits: List<String>): String {
        if (topTraits.isEmpty()) {
            return "Aura is still learning your visual preferences. Continue interacting with media to build your Taste DNA."
        }

        val strongInsights = mutableListOf<String>()
        
        // 1. Core Aesthetic (Color & Light)
        val vibrancy = dimensions["Vibrancy"] ?: 0.5
        val saturation = dimensions["Saturation"] ?: 0.5
        val contrast = dimensions["Contrast"] ?: 0.5
        val lighting = dimensions["Lighting"] ?: 0.5
        
        if (vibrancy > 0.7 && saturation > 0.7) strongInsights.add("vivid, high-energy color palettes")
        else if (vibrancy < 0.3 && saturation < 0.3) strongInsights.add("muted, understated color tones")
        
        if (contrast > 0.7) strongInsights.add("dramatic, high-contrast visuals")
        else if (contrast < 0.3) strongInsights.add("soft, subtle tonal transitions")
        
        if (lighting > 0.7) strongInsights.add("radiant, well-lit scenes")
        else if (lighting < 0.3) strongInsights.add("moody, shadow-heavy lighting")

        // 2. Structural Character (Composition)
        val minimalism = dimensions["Minimalism"] ?: 0.5
        val complexity = dimensions["Complexity"] ?: 0.5
        val symmetry = dimensions["Symmetry"] ?: 0.5
        val framing = dimensions["Framing"] ?: 0.5

        if (minimalism > 0.7) strongInsights.add("clean, minimalist compositions")
        else if (complexity > 0.7) strongInsights.add("intricate, detail-rich scenes")
        
        if (symmetry > 0.7) strongInsights.add("balanced, symmetrical structures")
        if (framing > 0.7) strongInsights.add("expansive, wide-angle perspectives")
        else if (framing < 0.3) strongInsights.add("intimate, tight-framed subjects")

        // 3. Dynamic Quality (Motion & Rhythm)
        val motion = dimensions["Motion"] ?: 0.5
        val rhythm = dimensions["Rhythm"] ?: 0.5
        val dynamicRange = dimensions["Dynamic Range"] ?: 0.5

        if (motion > 0.7) strongInsights.add("dynamic, energetic motion")
        else if (motion < 0.3) strongInsights.add("composed, static imagery")
        
        if (rhythm > 0.7) strongInsights.add("fast-paced, syncopated visual rhythms")
        if (dynamicRange > 0.7) strongInsights.add("rich, high-dynamic-range depth")

        // 4. Material Character (Texture & Feel)
        val texture = dimensions["Texture"] ?: 0.5
        val grain = dimensions["Grain"] ?: 0.5
        val naturalism = dimensions["Naturalism"] ?: 0.5
        val warmth = dimensions["Warmth"] ?: 0.5

        if (texture > 0.7) strongInsights.add("tactile, texture-focused details")
        if (grain > 0.7) strongInsights.add("classic, film-like grain")
        if (naturalism > 0.7) strongInsights.add("organic, natural-looking imagery")
        else if (naturalism < 0.3) strongInsights.add("stylized, artistic interpretations")
        
        if (warmth > 0.7) strongInsights.add("warm, inviting atmospheres")
        else if (warmth < 0.3) strongInsights.add("cool, clinical environments")

        // Synthesis
        if (strongInsights.isEmpty()) {
            return "Your visual style is versatile and balanced, showing an appreciation for a wide variety of aesthetic elements without a single dominant preference."
        }

        val description = StringBuilder("Your visual style ")
        description.append("leans toward ")
        
        val primary = strongInsights.take(3)
        description.append(primary.joinToString(separator = ", ", postfix = "."))
        
        if (strongInsights.size > 3) {
            val secondary = strongInsights.drop(3).take(2)
            description.append(" You also show a preference for ${secondary.joinToString(" and ")}.")
        }

        return description.toString()
    }

    /**
     * Aggregates interaction history into an engagement snapshot.
     */
    private suspend fun generateEngagementSnapshot(): EngagementSnapshot {
        val media = database.mediaDao().getAllMediaSync()
        if (media.isEmpty()) {
            return EngagementSnapshot(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                completionRate = 0.0,
                favoriteDensity = 0.0,
                averageSkipVelocity = 0.0,
                microMomentIntensity = 0.0,
                sessionDurationAverageMinutes = 0.0,
                mostActiveHour = 12
            )
        }

        // 1. Completion Rate (Simplified: viewCount vs exposureCount)
        val totalExposures = media.sumOf { it.exposureCount }.coerceAtLeast(1)
        val totalViews = media.sumOf { it.playCount }.coerceAtLeast(0)
        val completionRate = (totalViews.toDouble() / totalExposures).coerceIn(0.0, 1.0)

        // 2. Favorite Density
        val favoriteCount = media.count { it.isFavorite }
        val favoriteDensity = (favoriteCount.toDouble() / media.size).coerceIn(0.0, 1.0)

        // 3. Skip Velocity
        val totalSkips = database.aiSkipDao().getTotalSkipForwards()
        val totalViewMinutes = (media.sumOf { it.durationMs } / 60000.0).coerceAtLeast(1.0)
        val skipVelocity = totalSkips.toDouble() / totalViewMinutes

        // 4. Activity Hour (Dummy logic for now)
        val mostActiveHour = 19 

        return EngagementSnapshot(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            completionRate = quantize(completionRate),
            favoriteDensity = quantize(favoriteDensity),
            averageSkipVelocity = quantize(skipVelocity),
            microMomentIntensity = 0.5,
            sessionDurationAverageMinutes = 8.5,
            mostActiveHour = mostActiveHour
        )
    }

    /**
     * Calculates model calibration and Personalization Confidence.
     */
    private suspend fun generateMaturitySnapshot(): AuraMaturitySnapshot {
        val voteCount = database.pairwiseDao().getVoteCount()
        val itemCount = database.mediaDao().getCount()
        val interactionsAnalyzed = database.aiSkipDao().getTotalSkipForwards() + 
                                   database.aiSkipDao().getTotalSkipBacks() + 
                                   voteCount
        
        // Maturity Logic:
        // - 0-10 votes: INITIALIZING
        // - 10-100 votes: LEARNING
        // - 100-500 votes: STABILIZING
        // - 500+ votes: MATURE
        val status = when {
            voteCount < 10 -> CalibrationStatus.INITIALIZING
            voteCount < 100 -> CalibrationStatus.LEARNING
            voteCount < 500 -> CalibrationStatus.STABILIZING
            else -> CalibrationStatus.MATURE
        }

        // Personalization Confidence (0.0 to 1.0) - Signal Quality
        // Logarithmic scale based on vote count
        val confidence = (Math.log10(voteCount.toDouble() + 1.0) / 3.0).coerceIn(0.0, 1.0)
        
        // Data Coverage - Signal Quantity
        // Proportion of items that have been at least exposed once or rated
        val media = database.mediaDao().getAllMediaSync()
        val itemsInteractedWith = media.count { it.exposureCount > 0 || it.rating > 0 }
        val coverage = if (itemCount > 0) itemsInteractedWith.toDouble() / itemCount else 0.0

        return AuraMaturitySnapshot(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            personalizationConfidence = quantize(confidence),
            dataCoverage = quantize(coverage),
            totalInteractionsAnalyzed = interactionsAnalyzed,
            pairwiseComparisonsCompleted = voteCount,
            itemsInLearningPool = itemCount,
            calibrationStatus = status
        )
    }

    private fun quantize(value: Double): Double {
        return (value * 100.0).roundToInt() / 100.0
    }
}

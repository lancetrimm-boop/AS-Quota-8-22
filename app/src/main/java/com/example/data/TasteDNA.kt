package com.example.data

import com.squareup.moshi.JsonClass

/**
 * Reorganized core visual and behavioral preference model for Aura.
 * Restored to the complete 24-dimension representation identified in the audit.
 * All dimensions follow the Fine-Tuning model: Manual Baseline + AI Learned Adjustment.
 */
@JsonClass(generateAdapter = true)
data class TasteDNA(
    val isFineTuningEnabled: Boolean = false,

    // --- 24 VISUAL & AESTHETIC DIMENSIONS ---
    val vibrancy: Double = 0.5,
    val contrast: Double = 0.5,
    val sharpness: Double = 0.5,
    val symmetry: Double = 0.5,
    val complexity: Double = 0.5,
    val naturalism: Double = 0.5,
    val novelty: Double = 0.5,
    val lighting: Double = 0.5,
    val colorTemperature: Double = 0.5,
    val texture: Double = 0.5,
    val motion: Double = 0.5,
    val dynamicRange: Double = 0.5,
    val framing: Double = 0.5,
    val depth: Double = 0.5,
    val warmth: Double = 0.5,
    val saturation: Double = 0.5,
    val elegance: Double = 0.5,
    val minimalism: Double = 0.5,
    val grain: Double = 0.5,
    val focus: Double = 0.5,
    val density: Double = 0.5,
    val rhythm: Double = 0.5,
    val mood: Double = 0.5,
    val harmony: Double = 0.5,

    // --- BEHAVIORAL PREFERENCE ---
    val skipSensitivity: Double = 0.5,
    val explorationPropensity: Double = 0.5,
    val retentionFocus: Double = 0.5,
    val favoriteSignificance: Double = 0.5,

    // --- AI LEARNED STATE (Hidden Calibration Layer) ---
    val learnedVibrancy: Double = 0.5,
    val learnedContrast: Double = 0.5,
    val learnedSharpness: Double = 0.5,
    val learnedSymmetry: Double = 0.5,
    val learnedComplexity: Double = 0.5,
    val learnedNaturalism: Double = 0.5,
    val learnedNovelty: Double = 0.5,
    val learnedLighting: Double = 0.5,
    val learnedColorTemp: Double = 0.5,
    val learnedTexture: Double = 0.5,
    val learnedMotion: Double = 0.5,
    val learnedDynamicRange: Double = 0.5,
    val learnedFraming: Double = 0.5,
    val learnedDepth: Double = 0.5,
    val learnedWarmth: Double = 0.5,
    val learnedSaturation: Double = 0.5,
    val learnedElegance: Double = 0.5,
    val learnedMinimalism: Double = 0.5,
    val learnedGrain: Double = 0.5,
    val learnedFocus: Double = 0.5,
    val learnedDensity: Double = 0.5,
    val learnedRhythm: Double = 0.5,
    val learnedMood: Double = 0.5,
    val learnedHarmony: Double = 0.5,
    
    val learnedSkipSensitivity: Double = 0.5,
    val learnedExploration: Double = 0.5,
    val learnedRetention: Double = 0.5,
    val learnedFavSignificance: Double = 0.5
) {
    enum class AestheticDimension(val key: String) {
        VIBRANCY("vibrancy"),
        CONTRAST("contrast"),
        SHARPNESS("sharpness"),
        SYMMETRY("symmetry"),
        COMPLEXITY("complexity"),
        NATURALISM("naturalism"),
        NOVELTY("novelty"),
        LIGHTING("lighting"),
        COLOR_TEMPERATURE("colorTemperature"),
        TEXTURE("texture"),
        MOTION("motion"),
        DYNAMIC_RANGE("dynamicRange"),
        FRAMING("framing"),
        DEPTH("depth"),
        WARMTH("warmth"),
        SATURATION("saturation"),
        ELEGANCE("elegance"),
        MINIMALISM("minimalism"),
        GRAIN("grain"),
        FOCUS("focus"),
        DENSITY("density"),
        RHYTHM("rhythm"),
        MOOD("mood"),
        HARMONY("harmony")
    }

    // Effective values (Averaged Manual + AI Adjustment)
    fun getEffective(manual: Double, learned: Double): Double =
        if (isFineTuningEnabled) (manual + learned) / 2.0 else manual

    val effectiveVibrancy: Double get() = getEffective(vibrancy, learnedVibrancy)
    val effectiveContrast: Double get() = getEffective(contrast, learnedContrast)
    val effectiveSharpness: Double get() = getEffective(sharpness, learnedSharpness)
    val effectiveSymmetry: Double get() = getEffective(symmetry, learnedSymmetry)
    val effectiveComplexity: Double get() = getEffective(complexity, learnedComplexity)
    val effectiveNaturalism: Double get() = getEffective(naturalism, learnedNaturalism)
    val effectiveNovelty: Double get() = getEffective(novelty, learnedNovelty)
    val effectiveLighting: Double get() = getEffective(lighting, learnedLighting)
    val effectiveColorTemp: Double get() = getEffective(colorTemperature, learnedColorTemp)
    val effectiveTexture: Double get() = getEffective(texture, learnedTexture)
    val effectiveMotion: Double get() = getEffective(motion, learnedMotion)
    val effectiveDynamicRange: Double get() = getEffective(dynamicRange, learnedDynamicRange)
    val effectiveFraming: Double get() = getEffective(framing, learnedFraming)
    val effectiveDepth: Double get() = getEffective(depth, learnedDepth)
    val effectiveWarmth: Double get() = getEffective(warmth, learnedWarmth)
    val effectiveSaturation: Double get() = getEffective(saturation, learnedSaturation)
    val effectiveElegance: Double get() = getEffective(elegance, learnedElegance)
    val effectiveMinimalism: Double get() = getEffective(minimalism, learnedMinimalism)
    val effectiveGrain: Double get() = getEffective(grain, learnedGrain)
    val effectiveFocus: Double get() = getEffective(focus, learnedFocus)
    val effectiveDensity: Double get() = getEffective(density, learnedDensity)
    val effectiveRhythm: Double get() = getEffective(rhythm, learnedRhythm)
    val effectiveMood: Double get() = getEffective(mood, learnedMood)
    val effectiveHarmony: Double get() = getEffective(harmony, learnedHarmony)

    val effectiveSkipSensitivity: Double get() = getEffective(skipSensitivity, learnedSkipSensitivity)
    val effectiveExploration: Double get() = getEffective(explorationPropensity, learnedExploration)
    val effectiveRetention: Double get() = getEffective(retentionFocus, learnedRetention)
    val effectiveFavSignificance: Double get() = getEffective(favoriteSignificance, learnedFavSignificance)

    /**
     * Resets learned layers back to manual baselines.
     */
    fun resetFineTuning(): TasteDNA = this.copy(
        learnedVibrancy = vibrancy,
        learnedContrast = contrast,
        learnedSharpness = sharpness,
        learnedSymmetry = symmetry,
        learnedComplexity = complexity,
        learnedNaturalism = naturalism,
        learnedNovelty = novelty,
        learnedLighting = lighting,
        learnedColorTemp = colorTemperature,
        learnedTexture = texture,
        learnedMotion = motion,
        learnedDynamicRange = dynamicRange,
        learnedFraming = framing,
        learnedDepth = depth,
        learnedWarmth = warmth,
        learnedSaturation = saturation,
        learnedElegance = elegance,
        learnedMinimalism = minimalism,
        learnedGrain = grain,
        learnedFocus = focus,
        learnedDensity = density,
        learnedRhythm = rhythm,
        learnedMood = mood,
        learnedHarmony = harmony,
        learnedSkipSensitivity = skipSensitivity,
        learnedExploration = explorationPropensity,
        learnedRetention = retentionFocus,
        learnedFavSignificance = favoriteSignificance
    )

    /**
     * Ensures all dimensions are valid numbers and within [0, 1] range.
     * Falls back to 0.50 for any invalid value.
     */
    fun sanitize(): TasteDNA {
        fun Double.valid() = if (isNaN() || isInfinite()) 0.5 else coerceIn(0.0, 1.0)
        return copy(
            vibrancy = vibrancy.valid(),
            contrast = contrast.valid(),
            sharpness = sharpness.valid(),
            symmetry = symmetry.valid(),
            complexity = complexity.valid(),
            naturalism = naturalism.valid(),
            novelty = novelty.valid(),
            lighting = lighting.valid(),
            colorTemperature = colorTemperature.valid(),
            texture = texture.valid(),
            motion = motion.valid(),
            dynamicRange = dynamicRange.valid(),
            framing = framing.valid(),
            depth = depth.valid(),
            warmth = warmth.valid(),
            saturation = saturation.valid(),
            elegance = elegance.valid(),
            minimalism = minimalism.valid(),
            grain = grain.valid(),
            focus = focus.valid(),
            density = density.valid(),
            rhythm = rhythm.valid(),
            mood = mood.valid(),
            harmony = harmony.valid(),
            skipSensitivity = skipSensitivity.valid(),
            explorationPropensity = explorationPropensity.valid(),
            retentionFocus = retentionFocus.valid(),
            favoriteSignificance = favoriteSignificance.valid(),
            learnedVibrancy = learnedVibrancy.valid(),
            learnedContrast = learnedContrast.valid(),
            learnedSharpness = learnedSharpness.valid(),
            learnedSymmetry = learnedSymmetry.valid(),
            learnedComplexity = learnedComplexity.valid(),
            learnedNaturalism = learnedNaturalism.valid(),
            learnedNovelty = learnedNovelty.valid(),
            learnedLighting = learnedLighting.valid(),
            learnedColorTemp = learnedColorTemp.valid(),
            learnedTexture = learnedTexture.valid(),
            learnedMotion = learnedMotion.valid(),
            learnedDynamicRange = learnedDynamicRange.valid(),
            learnedFraming = learnedFraming.valid(),
            learnedDepth = learnedDepth.valid(),
            learnedWarmth = learnedWarmth.valid(),
            learnedSaturation = learnedSaturation.valid(),
            learnedElegance = learnedElegance.valid(),
            learnedMinimalism = learnedMinimalism.valid(),
            learnedGrain = learnedGrain.valid(),
            learnedFocus = learnedFocus.valid(),
            learnedDensity = learnedDensity.valid(),
            learnedRhythm = learnedRhythm.valid(),
            learnedMood = learnedMood.valid(),
            learnedHarmony = learnedHarmony.valid(),
            learnedSkipSensitivity = learnedSkipSensitivity.valid(),
            learnedExploration = learnedExploration.valid(),
            learnedRetention = learnedRetention.valid(),
            learnedFavSignificance = learnedFavSignificance.valid()
        )
    }

    /**
     * Preference Profile rollup for high-level scoring weights.
     */
    @JsonClass(generateAdapter = true)
    data class PreferenceProfile(
        val contentSimilarityWeight: Double = 0.5,
        val collaborativeWeight: Double = 0.5,
        val diversityWeight: Double = 0.5,
        val noveltyWeight: Double = 0.5,
        val interactionsCount: Int = 0,
        val similaritySignal: Int = 0,
        val collaborativeSignal: Int = 0,
        val diversitySignal: Int = 0,
        val noveltySignal: Int = 0
    ) {
        fun normalize(targetSum: Double = 2.0): PreferenceProfile {
            val currentSum = contentSimilarityWeight + collaborativeWeight + diversityWeight + noveltyWeight
            if (currentSum == 0.0) return PreferenceProfile(0.5, 0.5, 0.5, 0.5)
            
            return copy(
                contentSimilarityWeight = (contentSimilarityWeight / currentSum) * targetSum,
                collaborativeWeight = (collaborativeWeight / currentSum) * targetSum,
                diversityWeight = (diversityWeight / currentSum) * targetSum,
                noveltyWeight = (noveltyWeight / currentSum) * targetSum
            )
        }

        fun sanitize(): PreferenceProfile {
            fun Double.valid() = if (isNaN() || isInfinite()) 0.5 else coerceIn(0.0, 1.0)
            return copy(
                contentSimilarityWeight = contentSimilarityWeight.valid(),
                collaborativeWeight = collaborativeWeight.valid(),
                diversityWeight = diversityWeight.valid(),
                noveltyWeight = noveltyWeight.valid()
            )
        }
    }

    /**
     * Returns a copy of TasteDNA with a specific learned dimension adjusted.
     */
    fun updateLearnedDimension(dimension: String, adjustment: Double, baselineLimit: Double): TasteDNA {
        return when (dimension) {
            "vibrancy" -> copy(learnedVibrancy = (learnedVibrancy + adjustment).coerceIn(vibrancy - baselineLimit, vibrancy + baselineLimit).coerceIn(0.0, 1.0))
            "contrast" -> copy(learnedContrast = (learnedContrast + adjustment).coerceIn(contrast - baselineLimit, contrast + baselineLimit).coerceIn(0.0, 1.0))
            "sharpness" -> copy(learnedSharpness = (learnedSharpness + adjustment).coerceIn(sharpness - baselineLimit, sharpness + baselineLimit).coerceIn(0.0, 1.0))
            "symmetry" -> copy(learnedSymmetry = (learnedSymmetry + adjustment).coerceIn(symmetry - baselineLimit, symmetry + baselineLimit).coerceIn(0.0, 1.0))
            "complexity" -> copy(learnedComplexity = (learnedComplexity + adjustment).coerceIn(complexity - baselineLimit, complexity + baselineLimit).coerceIn(0.0, 1.0))
            "naturalism" -> copy(learnedNaturalism = (learnedNaturalism + adjustment).coerceIn(naturalism - baselineLimit, naturalism + baselineLimit).coerceIn(0.0, 1.0))
            "novelty" -> copy(learnedNovelty = (learnedNovelty + adjustment).coerceIn(novelty - baselineLimit, novelty + baselineLimit).coerceIn(0.0, 1.0))
            "lighting" -> copy(learnedLighting = (learnedLighting + adjustment).coerceIn(lighting - baselineLimit, lighting + baselineLimit).coerceIn(0.0, 1.0))
            "colorTemperature" -> copy(learnedColorTemp = (learnedColorTemp + adjustment).coerceIn(colorTemperature - baselineLimit, colorTemperature + baselineLimit).coerceIn(0.0, 1.0))
            "texture" -> copy(learnedTexture = (learnedTexture + adjustment).coerceIn(texture - baselineLimit, texture + baselineLimit).coerceIn(0.0, 1.0))
            "motion" -> copy(learnedMotion = (learnedMotion + adjustment).coerceIn(motion - baselineLimit, motion + baselineLimit).coerceIn(0.0, 1.0))
            "dynamicRange" -> copy(learnedDynamicRange = (learnedDynamicRange + adjustment).coerceIn(dynamicRange - baselineLimit, dynamicRange + baselineLimit).coerceIn(0.0, 1.0))
            "framing" -> copy(learnedFraming = (learnedFraming + adjustment).coerceIn(framing - baselineLimit, framing + baselineLimit).coerceIn(0.0, 1.0))
            "depth" -> copy(learnedDepth = (learnedDepth + adjustment).coerceIn(depth - baselineLimit, depth + baselineLimit).coerceIn(0.0, 1.0))
            "warmth" -> copy(learnedWarmth = (learnedWarmth + adjustment).coerceIn(warmth - baselineLimit, warmth + baselineLimit).coerceIn(0.0, 1.0))
            "saturation" -> copy(learnedSaturation = (learnedSaturation + adjustment).coerceIn(saturation - baselineLimit, saturation + baselineLimit).coerceIn(0.0, 1.0))
            "elegance" -> copy(learnedElegance = (learnedElegance + adjustment).coerceIn(elegance - baselineLimit, elegance + baselineLimit).coerceIn(0.0, 1.0))
            "minimalism" -> copy(learnedMinimalism = (learnedMinimalism + adjustment).coerceIn(minimalism - baselineLimit, minimalism + baselineLimit).coerceIn(0.0, 1.0))
            "grain" -> copy(learnedGrain = (learnedGrain + adjustment).coerceIn(grain - baselineLimit, grain + baselineLimit).coerceIn(0.0, 1.0))
            "focus" -> copy(learnedFocus = (learnedFocus + adjustment).coerceIn(focus - baselineLimit, focus + baselineLimit).coerceIn(0.0, 1.0))
            "density" -> copy(learnedDensity = (learnedDensity + adjustment).coerceIn(density - baselineLimit, density + baselineLimit).coerceIn(0.0, 1.0))
            "rhythm" -> copy(learnedRhythm = (learnedRhythm + adjustment).coerceIn(rhythm - baselineLimit, rhythm + baselineLimit).coerceIn(0.0, 1.0))
            "mood" -> copy(learnedMood = (learnedMood + adjustment).coerceIn(mood - baselineLimit, mood + baselineLimit).coerceIn(0.0, 1.0))
            "harmony" -> copy(learnedHarmony = (learnedHarmony + adjustment).coerceIn(harmony - baselineLimit, harmony + baselineLimit).coerceIn(0.0, 1.0))
            "skipSensitivity" -> copy(learnedSkipSensitivity = (learnedSkipSensitivity + adjustment).coerceIn(skipSensitivity - baselineLimit, skipSensitivity + baselineLimit).coerceIn(0.0, 1.0))
            "explorationPropensity" -> copy(learnedExploration = (learnedExploration + adjustment).coerceIn(explorationPropensity - baselineLimit, explorationPropensity + baselineLimit).coerceIn(0.0, 1.0))
            "retentionFocus" -> copy(learnedRetention = (learnedRetention + adjustment).coerceIn(retentionFocus - baselineLimit, retentionFocus + baselineLimit).coerceIn(0.0, 1.0))
            "favoriteSignificance" -> copy(learnedFavSignificance = (learnedFavSignificance + adjustment).coerceIn(favoriteSignificance - baselineLimit, favoriteSignificance + baselineLimit).coerceIn(0.0, 1.0))
            else -> this
        }
    }

    /**
     * Updates manual baseline and re-anchors AI learning to that baseline.
     */
    fun updateBaseline(
        newVibrancy: Double? = null,
        newContrast: Double? = null,
        newSharpness: Double? = null,
        newSymmetry: Double? = null,
        newComplexity: Double? = null,
        newNaturalism: Double? = null,
        newNovelty: Double? = null,
        newLighting: Double? = null,
        newColorTemp: Double? = null,
        newTexture: Double? = null,
        newMotion: Double? = null,
        newDynamicRange: Double? = null,
        newFraming: Double? = null,
        newDepth: Double? = null,
        newWarmth: Double? = null,
        newSaturation: Double? = null,
        newElegance: Double? = null,
        newMinimalism: Double? = null,
        newGrain: Double? = null,
        newFocus: Double? = null,
        newDensity: Double? = null,
        newRhythm: Double? = null,
        newMood: Double? = null,
        newHarmony: Double? = null,
        newSkip: Double? = null,
        newExploration: Double? = null,
        newRetention: Double? = null,
        newFavSignificance: Double? = null
    ): TasteDNA {
        return this.copy(
            vibrancy = newVibrancy ?: vibrancy,
            learnedVibrancy = newVibrancy ?: learnedVibrancy,
            contrast = newContrast ?: contrast,
            learnedContrast = newContrast ?: learnedContrast,
            sharpness = newSharpness ?: sharpness,
            learnedSharpness = newSharpness ?: learnedSharpness,
            symmetry = newSymmetry ?: symmetry,
            learnedSymmetry = newSymmetry ?: learnedSymmetry,
            complexity = newComplexity ?: complexity,
            learnedComplexity = newComplexity ?: learnedComplexity,
            naturalism = newNaturalism ?: naturalism,
            learnedNaturalism = newNaturalism ?: learnedNaturalism,
            novelty = newNovelty ?: novelty,
            learnedNovelty = newNovelty ?: learnedNovelty,
            lighting = newLighting ?: lighting,
            learnedLighting = newLighting ?: learnedLighting,
            colorTemperature = newColorTemp ?: colorTemperature,
            learnedColorTemp = newColorTemp ?: learnedColorTemp,
            texture = newTexture ?: texture,
            learnedTexture = newTexture ?: learnedTexture,
            motion = newMotion ?: motion,
            learnedMotion = newMotion ?: learnedMotion,
            dynamicRange = newDynamicRange ?: dynamicRange,
            learnedDynamicRange = newDynamicRange ?: learnedDynamicRange,
            framing = newFraming ?: framing,
            learnedFraming = newFraming ?: learnedFraming,
            depth = newDepth ?: depth,
            learnedDepth = newDepth ?: learnedDepth,
            warmth = newWarmth ?: warmth,
            learnedWarmth = newWarmth ?: learnedWarmth,
            saturation = newSaturation ?: saturation,
            learnedSaturation = newSaturation ?: learnedSaturation,
            elegance = newElegance ?: elegance,
            learnedElegance = newElegance ?: learnedElegance,
            minimalism = newMinimalism ?: minimalism,
            learnedMinimalism = newMinimalism ?: learnedMinimalism,
            grain = newGrain ?: grain,
            learnedGrain = newGrain ?: learnedGrain,
            focus = newFocus ?: focus,
            learnedFocus = newFocus ?: learnedFocus,
            density = newDensity ?: density,
            learnedDensity = newDensity ?: learnedDensity,
            rhythm = newRhythm ?: rhythm,
            learnedRhythm = newRhythm ?: learnedRhythm,
            mood = newMood ?: mood,
            learnedMood = newMood ?: learnedMood,
            harmony = newHarmony ?: harmony,
            learnedHarmony = newHarmony ?: learnedHarmony,
            skipSensitivity = newSkip ?: skipSensitivity,
            learnedSkipSensitivity = newSkip ?: learnedSkipSensitivity,
            explorationPropensity = newExploration ?: explorationPropensity,
            learnedExploration = newExploration ?: learnedExploration,
            retentionFocus = newRetention ?: retentionFocus,
            learnedRetention = newRetention ?: learnedRetention,
            favoriteSignificance = newFavSignificance ?: favoriteSignificance,
            learnedFavSignificance = newFavSignificance ?: learnedFavSignificance
        )
    }
}

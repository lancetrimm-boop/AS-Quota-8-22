package com.example.data.contribution

import com.example.data.TasteDNA
import com.example.data.db.AISkipEventEntity
import com.example.data.db.PairwiseOutcomeEntity
import kotlin.math.roundToInt

/**
 * Pure, stateless transformation layer that converts local domain data and Room entities
 * into sanitized, anonymized contribution payload models.
 */
object ContributionDataSanitizer {

    private const val MILLIS_PER_HOUR = 3600000L

    /**
     * Converts the complete TasteDNA model into an anonymized vector snapshot payload.
     */
    fun sanitizeTasteDNA(
        tasteDNA: TasteDNA,
        timestampMs: Long = System.currentTimeMillis()
    ): ElemTasteVectorSnapshotV1? {
        if (timestampMs <= 0L) return null

        val hourWindow = timestampMs / MILLIS_PER_HOUR

        return ElemTasteVectorSnapshotV1(
            schemaVersion = "1.0",
            eventType = "ELEM_TASTE_VECTOR_SNAPSHOT_V1",
            timeWindowHour = hourWindow,

            // 24 Visual & Aesthetic Dimensions
            vibrancy = quantize(tasteDNA.learnedVibrancy),
            contrast = quantize(tasteDNA.learnedContrast),
            sharpness = quantize(tasteDNA.learnedSharpness),
            symmetry = quantize(tasteDNA.learnedSymmetry),
            complexity = quantize(tasteDNA.learnedComplexity),
            naturalism = quantize(tasteDNA.learnedNaturalism),
            novelty = quantize(tasteDNA.learnedNovelty),
            lighting = quantize(tasteDNA.learnedLighting),
            colorTemperature = quantize(tasteDNA.learnedColorTemp),
            texture = quantize(tasteDNA.learnedTexture),
            motion = quantize(tasteDNA.learnedMotion),
            dynamicRange = quantize(tasteDNA.learnedDynamicRange),
            framing = quantize(tasteDNA.learnedFraming),
            depth = quantize(tasteDNA.learnedDepth),
            warmth = quantize(tasteDNA.learnedWarmth),
            saturation = quantize(tasteDNA.learnedSaturation),
            elegance = quantize(tasteDNA.learnedElegance),
            minimalism = quantize(tasteDNA.learnedMinimalism),
            grain = quantize(tasteDNA.learnedGrain),
            focus = quantize(tasteDNA.learnedFocus),
            density = quantize(tasteDNA.learnedDensity),
            rhythm = quantize(tasteDNA.learnedRhythm),
            mood = quantize(tasteDNA.learnedMood),
            harmony = quantize(tasteDNA.learnedHarmony),

            // Behavioral Preference (4)
            skipSensitivity = quantize(tasteDNA.learnedSkipSensitivity),
            explorationPropensity = quantize(tasteDNA.learnedExploration),
            retentionFocus = quantize(tasteDNA.learnedRetention),
            favoriteSignificance = quantize(tasteDNA.learnedFavSignificance)
        )
    }

    /**
     * Converts a PairwiseOutcomeEntity into a decoupled Elo calibration delta payload.
     */
    fun sanitizePairwiseOutcome(entity: PairwiseOutcomeEntity): ElemPairwiseDeltaV1? {
        if (entity.timestamp <= 0L) return null

        val outcomeCategory = when (entity.chosenId) {
            entity.optionAId -> "A_WINS"
            entity.optionBId -> "B_WINS"
            else -> "TIE"
        }

        val actualOutcome = if (entity.chosenId == entity.optionAId) 1.0 else if (entity.chosenId == entity.optionBId) 0.0 else 0.5
        val eloDelta = entity.postRatingA - entity.preRatingA

        return ElemPairwiseDeltaV1(
            schemaVersion = "1.0",
            eventType = "ELEM_PAIRWISE_DELTA_V1",
            timeWindowHour = entity.timestamp / MILLIS_PER_HOUR,
            expectedScoreQuantized = quantize(entity.expectedScoreA, min = 0.0, max = 1.0),
            actualOutcome = quantize(actualOutcome, min = 0.0, max = 1.0),
            eloDeltaQuantized = quantize(eloDelta, min = -100.0, max = 100.0),
            kFactorQuantized = quantize(entity.kFactor, min = 0.0, max = 100.0),
            outcomeCategory = outcomeCategory
        )
    }

    /**
     * Converts an AISkipEventEntity into an anonymized skip calibration payload.
     */
    fun sanitizeAISkipEvent(entity: AISkipEventEntity): ElemSkipCalibrationV1? {
        if (entity.timestamp <= 0L || entity.eventType.isBlank()) return null

        val direction = if (entity.toPosMs >= entity.fromPosMs) "FORWARD" else "BACKWARD"
        val relPos = entity.fromPosMs / 1000.0
        val relJump = (entity.toPosMs - entity.fromPosMs) / 1000.0

        return ElemSkipCalibrationV1(
            schemaVersion = "1.0",
            eventType = "ELEM_SKIP_CALIBRATION_V1",
            timeWindowHour = entity.timestamp / MILLIS_PER_HOUR,
            skipType = entity.eventType.take(32),
            direction = direction,
            relativePositionQuantized = quantize(relPos, min = 0.0, max = 86400.0),
            relativeJumpDistanceQuantized = quantize(relJump, min = -86400.0, max = 86400.0),
            watchedDestination = entity.eventType == "WATCHED_DESTINATION",
            repeatedSkip = entity.eventType == "REPEATED_SKIP"
        )
    }

    /**
     * Sanitizes high-level UI feedback or telemetry signals into recommendation feedback payloads.
     */
    fun sanitizeTelemetryEvent(
        interactionType: String,
        feedbackCategory: String,
        score: Double? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): ElemRecommendationFeedbackV1? {
        if (interactionType.isBlank() || feedbackCategory.isBlank() || timestampMs <= 0L) return null

        val sanitizedScore = score?.let { quantize(it, min = 0.0, max = 1.0) }

        return ElemRecommendationFeedbackV1(
            schemaVersion = "1.0",
            eventType = "ELEM_RECOMMENDATION_FEEDBACK_V1",
            timeWindowHour = timestampMs / MILLIS_PER_HOUR,
            interactionType = interactionType.take(32),
            feedbackCategory = feedbackCategory.take(32),
            scoreQuantized = sanitizedScore
        )
    }

    /**
     * Clamps a continuous numeric value to [min, max] and rounds to 2 decimal places.
     */
    private fun quantize(value: Double, min: Double = 0.0, max: Double = 1.0): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        val clamped = value.coerceIn(min, max)
        return (clamped * 100.0).roundToInt() / 100.0
    }
}

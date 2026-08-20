package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

/**
 * Interprets raw intelligence signals and dispatches meaningful "Aura Moments" to the UI.
 * Handles priority, cooldowns, and deduplication to maintain a premium, calm experience.
 */
class AuraMomentDispatcher(
    private val repository: MediaRepository
) {
    private val TAG = "AuraMomentDispatcher"

    // COOLDOWN CONFIGURATION (ms)
    private val INSIGHT_COOLDOWN = 10 * 60 * 1000L // 10 minutes between textual insights
    private val GLOBAL_MOMENT_LIMIT = 5            // Max significant moments per app session
    private val CALIBRATION_HAPTIC_COOLDOWN = 2000L // 2 seconds between subtle pulses

    // Milestone thresholds based on forensic audit of IntelligenceStats
    private val MEANINGFUL_VOTES_THRESHOLD = 10
    private val HIGH_CONFIDENCE_VOTES_THRESHOLD = 50
    private val ACCURACY_STEP_THRESHOLD = 5 // Notify every 5% accuracy increase

    private var lastAccuracyNotified = 0

    private val _moments = MutableSharedFlow<AuraMoment>(extraBufferCapacity = 1)
    val moments = _moments.asSharedFlow()

    private val categoryLastEmitted = mutableMapOf<MomentCategory, Long>()
    private var sessionMomentCount = 0

    /**
     * Raw intelligence events that can trigger an emotional experience.
     */
    sealed class IntelligenceEvent {
        data class TasteCalibrated(val dimension: String, val delta: Double, val isMajor: Boolean = false) : IntelligenceEvent()
        data class PredictionMade(val id: String, val context: String) : IntelligenceEvent()
        data class PredictionOutcome(val itemId: String, val isPositive: Boolean) : IntelligenceEvent()
        data class AffinityStrengthened(val identifier: String, val type: String) : IntelligenceEvent()
        data class SystemMilestone(val accuracy: Int, val totalVotes: Int) : IntelligenceEvent()
        object NoveltyConfirmed : IntelligenceEvent()
    }

    /**
     * Dispatches an event for interpretation.
     */
    suspend fun onEvent(event: IntelligenceEvent) {
        val moment = interpret(event) ?: return

        // 1. Enforce Global Session Limit (Except for Pulses)
        if (moment.category != MomentCategory.PULSE && sessionMomentCount >= GLOBAL_MOMENT_LIMIT) {
            return
        }

        // 2. Enforce Category Cooldown
        val lastTime = categoryLastEmitted[moment.category] ?: 0L
        val cooldown = when (moment.category) {
            MomentCategory.INSIGHT -> INSIGHT_COOLDOWN
            MomentCategory.PULSE -> CALIBRATION_HAPTIC_COOLDOWN
            MomentCategory.CELEBRATION -> 30 * 60 * 1000L // 30 mins for celebrations
        }

        if (System.currentTimeMillis() - lastTime < cooldown && moment.priority != MomentPriority.CRITICAL) {
            return
        }

        // 3. Emit Moment
        Log.i(TAG, "Dispatching Aura Moment: ${moment.title} - ${moment.message}")
        categoryLastEmitted[moment.category] = System.currentTimeMillis()
        if (moment.category != MomentCategory.PULSE) sessionMomentCount++
        
        _moments.emit(moment)
    }

    private fun interpret(event: IntelligenceEvent): AuraMoment? {
        return when (event) {
            is IntelligenceEvent.PredictionMade -> {
                AuraMoment(
                    category = MomentCategory.INSIGHT,
                    priority = MomentPriority.MEDIUM,
                    title = "Prediction",
                    message = event.context
                )
            }
            is IntelligenceEvent.TasteCalibrated -> {
                if (event.isMajor) {
                    AuraMoment(
                        category = MomentCategory.INSIGHT,
                        priority = MomentPriority.MEDIUM,
                        title = "Taste Evolving",
                        message = "Your preference for ${event.dimension} is becoming clearer."
                    )
                } else {
                    // Subtle feedback for routine learning
                    AuraMoment(
                        category = MomentCategory.PULSE,
                        priority = MomentPriority.LOW,
                        title = "Learning",
                        message = "Refining ${event.dimension}"
                    )
                }
            }
            is IntelligenceEvent.SystemMilestone -> {
                val accuracy = event.accuracy
                val votes = event.totalVotes

                when {
                    accuracy >= 90 && lastAccuracyNotified < 90 -> {
                        lastAccuracyNotified = 90
                        // AURA PHASE 4: Vibe Sync attunement popups removed as per requirement.
                        // We still update the notified level to maintain state consistency, 
                        // but no longer emit a CELEBRATION moment here.
                        null
                    }
                    accuracy >= 75 && lastAccuracyNotified < 75 -> {
                        lastAccuracyNotified = 75
                        AuraMoment(
                            category = MomentCategory.INSIGHT,
                            priority = MomentPriority.HIGH,
                            title = "Clearer Picture",
                            message = "I think I'm getting a clearer picture of what you love."
                        )
                    }
                    votes == MEANINGFUL_VOTES_THRESHOLD -> {
                        AuraMoment(
                            category = MomentCategory.INSIGHT,
                            priority = MomentPriority.MEDIUM,
                            title = "Learning",
                            message = "I'm starting to understand your taste."
                        )
                    }
                    votes == 5 -> { // Early learning
                        AuraMoment(
                            category = MomentCategory.INSIGHT,
                            priority = MomentPriority.LOW,
                            title = "Aura Moment",
                            message = "Got it. Learning your preferences."
                        )
                    }
                    accuracy % ACCURACY_STEP_THRESHOLD == 0 && accuracy > lastAccuracyNotified -> {
                        lastAccuracyNotified = accuracy
                        AuraMoment(
                            category = MomentCategory.PULSE, // Silent accuracy bump
                            priority = MomentPriority.LOW,
                            title = "Accuracy Up",
                            message = "Sync increased to $accuracy%"
                        )
                    }
                    else -> null
                }
            }
            is IntelligenceEvent.PredictionOutcome -> {
                if (event.isPositive) {
                    AuraMoment(
                        category = MomentCategory.INSIGHT,
                        priority = MomentPriority.HIGH,
                        title = "Called it.",
                        message = "I'm getting better at predicting what you'll enjoy."
                    )
                } else {
                    AuraMoment(
                        category = MomentCategory.INSIGHT,
                        priority = MomentPriority.MEDIUM,
                        title = "Adjusting.",
                        message = "Noted. I'll recalibrate for that style."
                    )
                }
            }
            is IntelligenceEvent.AffinityStrengthened -> {
                AuraMoment(
                    category = MomentCategory.INSIGHT,
                    priority = MomentPriority.MEDIUM,
                    title = "Noticed something.",
                    message = "You have a clear affinity for ${event.identifier}."
                )
            }
            is IntelligenceEvent.NoveltyConfirmed -> {
                AuraMoment(
                    category = MomentCategory.INSIGHT,
                    priority = MomentPriority.MEDIUM,
                    title = "Expanding your horizon.",
                    message = "I found something meaningful outside your usual style."
                )
            }
        }
    }
}

enum class MomentCategory { PULSE, INSIGHT, CELEBRATION }
enum class MomentPriority { LOW, MEDIUM, HIGH, CRITICAL }

data class AuraMoment(
    val id: String = UUID.randomUUID().toString(),
    val category: MomentCategory,
    val priority: MomentPriority,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

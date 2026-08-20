package com.example.data

import android.util.Log

object AISkipEngine {

    private const val TAG = "AISkipEngine"

    // Configuration weights (can be updated by Strategy Blueprints)
    var skipThreshold = 0.30f

    enum class SkipType {
        FORWARD,
        BACK,
        REVERSAL
    }

    data class SkipDecision(
        val type: SkipType,
        val targetPositionMs: Long,
        val destinationCandidate: ClipCandidate?,
        val reason: String,
        val isReversal: Boolean = false,
        val isRepeatedSkip: Boolean = false
    )

    /**
     * Calculates the intelligent AI Skip Forward destination.
     * Evaluates clip/scene candidates, rating, duration, and recent skip timing.
     */
    fun calculateSkipForward(
        item: MediaItem,
        currentPosMs: Long,
        durationMs: Long,
        clipCandidates: List<ClipCandidate>,
        lastSkipForwardTimeMs: Long = 0L,
        skipSensitivity: Double = 0.5
    ): SkipDecision {
        val now = System.currentTimeMillis()
        val timeSinceLastSkip = now - lastSkipForwardTimeMs
        val isRepeated = lastSkipForwardTimeMs > 0 && timeSinceLastSkip in 1..4000L

        // Find candidate scene starting after current position + threshold (2s buffer)
        val candidateAhead = clipCandidates.firstOrNull { candidate ->
            candidate.startTimeMs > currentPosMs + 2000L
        }

        val targetMs: Long
        val candidate: ClipCandidate?
        val reason: String

        if (candidateAhead != null) {
            candidate = candidateAhead
            targetMs = candidateAhead.startTimeMs
            reason = if (isRepeated) {
                "Aggressive AI Skip to scene: '${candidateAhead.title}' (${candidateAhead.relevanceScorePercent}% match)"
            } else {
                "AI Skip to predicted scene: '${candidateAhead.title}' (${candidateAhead.relevanceScorePercent}% match)"
            }
        } else {
            candidate = null
            // Unsegmented or past last candidate: compute dynamic skip offset based on media metrics & duration
            val baseJumpPercent = when {
                item.rating >= 4.5f -> skipThreshold * 0.32f * (2.0f - skipSensitivity.toFloat())
                item.rating >= 3.5f -> skipThreshold * 0.48f * (2.0f - skipSensitivity.toFloat())
                else -> skipThreshold * 0.72f * (skipSensitivity.toFloat() * 1.5f)
            }

            var dynamicJumpMs = (durationMs * baseJumpPercent).toLong().coerceIn(12000L, 45000L)
            if (isRepeated) {
                dynamicJumpMs = (dynamicJumpMs * 1.5f * skipSensitivity.toFloat() * 2.0f).toLong().coerceAtLeast(dynamicJumpMs)
            }

            targetMs = (currentPosMs + dynamicJumpMs).coerceAtMost(durationMs)
            reason = "AI Skip past low-interest section (+${dynamicJumpMs / 1000}s)"
        }

        logD(TAG, "AISkip Forward: item=${item.title}, from=${currentPosMs}ms, to=${targetMs}ms, reason=$reason")

        return SkipDecision(
            type = SkipType.FORWARD,
            targetPositionMs = targetMs,
            destinationCandidate = candidate,
            reason = reason,
            isRepeatedSkip = isRepeated
        )
    }

    /**
     * Calculates the intelligent AI Skip Back destination.
     * Detects reversal (forward skip followed immediately by back skip) and returns to prior content boundary or previous scene.
     */
    fun calculateSkipBack(
        item: MediaItem,
        currentPosMs: Long,
        durationMs: Long,
        clipCandidates: List<ClipCandidate>,
        lastSkipForwardTimeMs: Long = 0L,
        lastSkipForwardPosMs: Long = 0L
    ): SkipDecision {
        val now = System.currentTimeMillis()
        val timeSinceForwardSkip = now - lastSkipForwardTimeMs

        // Reversal Detection: if user skipped forward less than 5 seconds ago and presses AI Skip Back
        if (lastSkipForwardTimeMs > 0 && timeSinceForwardSkip in 1..5000L) {
            val targetMs = lastSkipForwardPosMs.coerceIn(0L, durationMs)
            val reason = "AI Skip Reversal! Returned to skipped content (${lastSkipForwardPosMs / 1000}s)"
            logD(TAG, "AISkip Reversal: item=${item.title}, from=${currentPosMs}ms, to=${targetMs}ms, reason=$reason")
            return SkipDecision(
                type = SkipType.REVERSAL,
                targetPositionMs = targetMs,
                destinationCandidate = null,
                reason = reason,
                isReversal = true
            )
        }

        // Normal AI Skip Back: find preceding candidate scene
        val precedingCandidate = clipCandidates.lastOrNull { candidate ->
            candidate.startTimeMs < currentPosMs - 3000L
        }

        val targetMs: Long
        val candidate: ClipCandidate?
        val reason: String

        if (precedingCandidate != null) {
            candidate = precedingCandidate
            targetMs = precedingCandidate.startTimeMs
            reason = "AI Skip Back to prior scene: '${precedingCandidate.title}'"
        } else {
            candidate = null
            val dynamicJumpMs = (durationMs * 0.10f).toLong().coerceIn(10000L, 30000L)
            targetMs = (currentPosMs - dynamicJumpMs).coerceAtLeast(0L)
            reason = "AI Skip Back to preceding section (-${dynamicJumpMs / 1000}s)"
        }

        logD(TAG, "AISkip Back: item=${item.title}, from=${currentPosMs}ms, to=${targetMs}ms, reason=$reason")

        return SkipDecision(
            type = SkipType.BACK,
            targetPositionMs = targetMs,
            destinationCandidate = candidate,
            reason = reason
        )
    }

    private fun logD(tag: String, msg: String) {
        try {
            Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }
}

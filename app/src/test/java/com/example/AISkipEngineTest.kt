package com.example

import com.example.data.AISkipEngine
import com.example.data.ClipCandidate
import com.example.data.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AISkipEngineTest {

    private val testItem = MediaItem(
        id = "video1",
        title = "Test Action Video",
        uriPath = "file:///test.mp4",
        mediaType = "VIDEO",
        genre = "Action",
        rating = 4.2f,
        durationMs = 300000L // 5 minutes
    )

    private val clipCandidates = listOf(
        ClipCandidate("Opening Action Peak", 0L, 30000L, 30, 93, "High initial energy"),
        ClipCandidate("Midpoint Turning Point", 100000L, 135000L, 35, 91, "Key moment"),
        ClipCandidate("Climax Peak Segment", 220000L, 255000L, 35, 94, "Peak energy")
    )

    @Test
    fun testSkipForwardToCandidateScene() {
        val currentPosMs = 10000L // Currently at 10s
        val decision = AISkipEngine.calculateSkipForward(
            item = testItem,
            currentPosMs = currentPosMs,
            durationMs = testItem.durationMs,
            clipCandidates = clipCandidates,
            lastSkipForwardTimeMs = 0L
        )

        assertEquals(AISkipEngine.SkipType.FORWARD, decision.type)
        assertEquals(100000L, decision.targetPositionMs) // Jumps to Midpoint Turning Point at 100s
        assertTrue(decision.reason.contains("Midpoint Turning Point"))
    }

    @Test
    fun testSkipForwardDynamicWhenPastCandidates() {
        val currentPosMs = 260000L // Past last clip candidate
        val decision = AISkipEngine.calculateSkipForward(
            item = testItem,
            currentPosMs = currentPosMs,
            durationMs = testItem.durationMs,
            clipCandidates = clipCandidates,
            lastSkipForwardTimeMs = 0L
        )

        assertEquals(AISkipEngine.SkipType.FORWARD, decision.type)
        assertTrue(decision.targetPositionMs > currentPosMs)
        assertTrue(decision.reason.contains("past low-interest section"))
    }

    @Test
    fun testSkipBackToPriorCandidate() {
        val currentPosMs = 150000L
        val decision = AISkipEngine.calculateSkipBack(
            item = testItem,
            currentPosMs = currentPosMs,
            durationMs = testItem.durationMs,
            clipCandidates = clipCandidates,
            lastSkipForwardTimeMs = 0L,
            lastSkipForwardPosMs = 0L
        )

        assertEquals(AISkipEngine.SkipType.BACK, decision.type)
        assertEquals(100000L, decision.targetPositionMs) // Returns to Midpoint at 100s
    }

    @Test
    fun testReversalDetectionOnImmediateBackSkip() {
        val forwardPos = 15000L
        val now = System.currentTimeMillis()
        
        // Skip Back performed 1 second after Skip Forward
        val decision = AISkipEngine.calculateSkipBack(
            item = testItem,
            currentPosMs = 100000L,
            durationMs = testItem.durationMs,
            clipCandidates = clipCandidates,
            lastSkipForwardTimeMs = now - 1000L,
            lastSkipForwardPosMs = forwardPos
        )

        assertEquals(AISkipEngine.SkipType.REVERSAL, decision.type)
        assertTrue(decision.isReversal)
        assertEquals(forwardPos, decision.targetPositionMs) // Returns to where forward skip was triggered
        assertTrue(decision.reason.contains("Reversal"))
    }
}

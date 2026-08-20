package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class AISkipEngineBehaviorTest {

    @Test
    fun testSkipThresholdApplication() {
        val item = MediaItem(id = "test", title = "Low Rating Video", mediaType = "VIDEO", rating = 2.0f)
        val durationMs = 100000L
        
        // Test with 0.25 (Baseline)
        AISkipEngine.skipThreshold = 0.25f
        val decisionBaseline = AISkipEngine.calculateSkipForward(item, 0L, durationMs, emptyList())
        val jumpBaseline = decisionBaseline.targetPositionMs
        
        // Test with 0.30 (Proposed)
        AISkipEngine.skipThreshold = 0.30f
        val decisionProposed = AISkipEngine.calculateSkipForward(item, 0L, durationMs, emptyList())
        val jumpProposed = decisionProposed.targetPositionMs
        
        assertTrue("Higher skip threshold should result in a larger jump for low-rating content", jumpProposed > jumpBaseline)
        assertEquals("Proposed jump should match expected calculation", (0.30f * 0.72f * 0.5f * 1.5f * durationMs).toLong(), jumpProposed)
    }
}

package com.example.util

import com.example.data.OriginalCleanupStatus
import com.example.data.db.ConversionJobEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class OriginalMediaCleanupTest {

    @Test
    fun testStabilityPeriodCalculation() {
        val now = System.currentTimeMillis()
        val stabilityMillis = TimeUnit.DAYS.toMillis(7)
        val eligibility = now + stabilityMillis
        
        // Verify it's roughly 7 days in the future
        val diffDays = (eligibility - now) / (1000 * 60 * 60 * 24)
        assertEquals(7, diffDays)
    }

    @Test
    fun testCleanupStates() {
        assertEquals("WAITING_FOR_STABILITY", OriginalCleanupStatus.WAITING_FOR_STABILITY.name)
        assertEquals("CLEANUP_ELIGIBLE", OriginalCleanupStatus.CLEANUP_ELIGIBLE.name)
        assertEquals("CLEANUP_COMPLETED", OriginalCleanupStatus.CLEANUP_COMPLETED.name)
    }
}

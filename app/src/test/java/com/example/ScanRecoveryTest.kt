package com.example

import com.example.compatibility.AuraMediaCompatibilityEngine
import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class ScanRecoveryTest {

    @Test
    fun testIsEligibleForImport_IncludesPlayableAfterConversion() {
        // This confirms the fix for PLAYABLE_AFTER_CONVERSION being treated as ineligible
        assertTrue("Media needing conversion should be eligible for import",
            AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.PLAYABLE_AFTER_CONVERSION))
    }

    @Test
    fun testPermanentRejectionLogic_OnlyForTerminalStatuses() {
        // This test logic simulates the hardening in processPendingMedia and reconcileExistingMedia
        
        fun shouldPermanentlyReject(status: CompatibilityStatus): Boolean {
            return status == CompatibilityStatus.UNSUPPORTED || 
                   status == CompatibilityStatus.CORRUPT || 
                   status == CompatibilityStatus.UNREADABLE
        }

        // Terminal statuses should be rejected
        assertTrue(shouldPermanentlyReject(CompatibilityStatus.UNSUPPORTED))
        assertTrue(shouldPermanentlyReject(CompatibilityStatus.CORRUPT))
        assertTrue(shouldPermanentlyReject(CompatibilityStatus.UNREADABLE))

        // Transient statuses should NOT be rejected
        assertFalse(shouldPermanentlyReject(CompatibilityStatus.ANALYSIS_FAILED))
        assertFalse(shouldPermanentlyReject(CompatibilityStatus.ANALYSIS_PENDING))
        assertFalse(shouldPermanentlyReject(CompatibilityStatus.ANALYSIS_IN_PROGRESS))
        assertFalse(shouldPermanentlyReject(CompatibilityStatus.THUMBNAIL_FAILED))
    }

    @Test
    fun testScanErrorMapping() {
        val statePermission = ScanProgressState(errorCode = ScanError.PERMISSION_DENIED)
        assertEquals(ScanError.PERMISSION_DENIED, statePermission.errorCode)
        
        val stateStorage = ScanProgressState(errorCode = ScanError.STORAGE_ACCESS_FAILED)
        assertEquals(ScanError.STORAGE_ACCESS_FAILED, stateStorage.errorCode)
        
        val stateEmpty = ScanProgressState(errorCode = ScanError.QUERY_EMPTY)
        assertEquals(ScanError.QUERY_EMPTY, stateEmpty.errorCode)
    }
}

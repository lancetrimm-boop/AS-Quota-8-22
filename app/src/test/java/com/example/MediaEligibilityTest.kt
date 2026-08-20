package com.example

import com.example.compatibility.AuraMediaCompatibilityEngine
import com.example.data.CompatibilityStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEligibilityTest {

    @Test
    fun testEligibilityLogic() {
        // Supported
        assertTrue(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.PLAYABLE))
        assertTrue(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.PLAYABLE_SOFTWARE_DECODE))
        assertTrue(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.THUMBNAIL_FAILED))
        assertTrue(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.PLAYABLE_AFTER_CONVERSION))
        assertTrue(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.UNTESTED))
        assertTrue(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.NEEDS_TRANSCODE))

        // Unsupported/Ineligible
        assertFalse(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.UNSUPPORTED))
        assertFalse(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.CORRUPT))
        assertFalse(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.DELETED))
        assertFalse(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.UNREADABLE))
        
        // Note: ANALYSIS_PENDING and ANALYSIS_FAILED are NOT considered "eligible for import" 
        // in the strict sense of the engine's gatekeeper, but they are preserved in the DB 
        // for analysis retries. The Engine gatekeeper is used for permanent rejection.
        assertFalse(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.ANALYSIS_PENDING))
        assertFalse(AuraMediaCompatibilityEngine.isEligibleForImport(CompatibilityStatus.ANALYSIS_FAILED))
    }
}

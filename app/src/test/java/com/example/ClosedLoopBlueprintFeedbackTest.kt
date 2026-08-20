package com.example

import com.example.data.*
import com.example.data.blueprint.*
import org.junit.Assert.*
import org.junit.Test

class ClosedLoopBlueprintFeedbackTest {

    @Test
    fun testClosedLoopFeedback_SuccessLineage() {
        // 1. Initial State (Blueprint v1.0.0)
        val report1 = ClosedLoopEngine.evaluate(50.0, 50.0, 60.0, emptyList())
        val blueprintV1 = StrategyBlueprintGenerator.generateBlueprint("Optimization V1", "Initial attempt", report1)
        val manifestV1 = BlueprintImplementationPlanner.planImplementation(blueprintV1)
        
        // 2. Post-Implementation Evidence
        val postEvidence = listOf(
            EvidenceRecord(
                tier = EvidenceTier.PRODUCTION, 
                sampleCount = 100, 
                score = 65.0, 
                quality = 0.9,
                associatedManifestId = manifestV1.manifestId
            )
        )
        
        // 3. Validation Cycle (Feedback Loop)
        val (updatedManifest, blueprintV2) = BlueprintImplementationValidator.validateImplementation(
            blueprintV1, manifestV1, postEvidence
        )
        
        // 4. Verify Outcomes and Next Actions
        assertEquals(ClosedLoopOutcome.SUCCESS, updatedManifest.closedLoopOutcome)
        assertTrue(blueprintV2.nextExperimentRecommendation.contains("Retain strategy"))
        
        // 5. Verify Lineage and Versioning
        assertEquals("1.0.1", blueprintV2.identity.version)
        assertEquals(blueprintV1.identity.blueprintId, blueprintV2.identity.parentBlueprintId)
        assertTrue(blueprintV2.versionHistory.any { it.notes.contains("Outcome: SUCCESS") })
        
        // 6. Verify Evidence Source Preservation
        assertEquals(100, blueprintV2.evidence.productionCount)
    }

    @Test
    fun testClosedLoopFeedback_RegressionAction() {
        val blueprintV1 = StrategyBlueprintGenerator.generateBlueprint(
            "Regression Test", "Desc", ClosedLoopEngine.evaluate(50.0, 50.0, 60.0, emptyList())
        )
        val manifestV1 = BlueprintImplementationPlanner.planImplementation(blueprintV1)
        
        val regressionEvidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 20, score = 30.0, quality = 0.9)
        )
        
        val (updatedManifest, blueprintV2) = BlueprintImplementationValidator.validateImplementation(
            blueprintV1, manifestV1, regressionEvidence
        )
        
        assertEquals(ClosedLoopOutcome.REGRESSION, updatedManifest.closedLoopOutcome)
        assertTrue(blueprintV2.nextExperimentRecommendation.contains("ROLLBACK REQUIRED"))
    }
}

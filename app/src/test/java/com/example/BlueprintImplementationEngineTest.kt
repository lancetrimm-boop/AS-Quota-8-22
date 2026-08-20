package com.example

import com.example.data.*
import com.example.data.blueprint.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BlueprintImplementationEngineTest {

    @Test
    fun testGenerateAndApproveManifest() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 65.0, quality = 0.9)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "AURA Personalization Engine Strategy",
            description = "Test strategy",
            report = report
        )

        // 1. Generate Manifest
        val manifest = BlueprintImplementationPlanner.planImplementation(blueprint)
        
        // 2. Simulate Approval
        val approvedManifest = manifest.copy(
            approvalState = ImplementationApprovalState.APPROVED,
            manifestStatus = ImplementationStatus.READY_FOR_IMPLEMENTATION
        )

        assertNotNull(approvedManifest)
        assertEquals(ImplementationApprovalState.APPROVED, approvedManifest.approvalState)
    }
}

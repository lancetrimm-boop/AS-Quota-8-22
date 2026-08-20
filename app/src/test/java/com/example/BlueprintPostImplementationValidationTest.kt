package com.example

import com.example.data.*
import com.example.data.blueprint.*
import org.junit.Assert.*
import org.junit.Test

class BlueprintPostImplementationValidationTest {

    @Test
    fun testValidation_ImplementationValidated() {
        // 1. Setup baseline
        val baselineScore = 50.0
        val targetScore = 65.0
        
        val report = ClosedLoopEngine.evaluate(baselineScore, 50.0, targetScore, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Test Strategy", "Desc", report)
        val manifest = BlueprintImplementationPlanner.planImplementation(blueprint)
        
        // 2. Simulate fresh production evidence after implementation (High sample count for causality)
        val postEvidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 90, score = 68.0, quality = 0.9)
        )
        
        // 3. Validate
        val (updatedManifest, updatedBlueprint) = BlueprintImplementationValidator.validateImplementation(
            blueprint, manifest, postEvidence
        )
        
        // 4. Assert
        assertEquals(ValidationStatus.IMPLEMENTATION_VALIDATED, updatedManifest.validationStatus)
        assertEquals(ImplementationStatus.VALIDATED, updatedManifest.manifestStatus)
        assertEquals(68.0, updatedManifest.postImplementationScore!!, 0.1)
        assertEquals(90, updatedManifest.postImplementationSamples)
        
        // Verify Blueprint updates
        assertTrue(updatedBlueprint.identity.version > blueprint.identity.version)
        assertEquals(OutcomeClassification.SIGNIFICANT_IMPROVEMENT.name, updatedBlueprint.actualOutcome.outcomeClassification)
        assertEquals(18.0, updatedBlueprint.actualOutcome.measuredScore!! - updatedBlueprint.baselineState.engagementScore, 0.1)
        assertTrue(updatedBlueprint.versionHistory.size > blueprint.versionHistory.size)
        
        println("Causality Summary: ${updatedManifest.causalValidationSummary}")
        assertTrue(updatedManifest.causalValidationSummary.contains("Causal validation: Strongly supported"))
    }

    @Test
    fun testValidation_RegressionDetected() {
        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            "Regression Test", "Desc", 
            ClosedLoopEngine.evaluate(50.0, 50.0, 60.0, emptyList())
        )
        val manifest = BlueprintImplementationPlanner.planImplementation(blueprint)
        
        val postEvidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 20, score = 35.0, quality = 0.9)
        )
        
        val (updatedManifest, _) = BlueprintImplementationValidator.validateImplementation(
            blueprint, manifest, postEvidence
        )
        
        assertEquals(ValidationStatus.REGRESSION_DETECTED, updatedManifest.validationStatus)
        assertTrue(updatedManifest.auditNotes.contains("Score: 35.0"))
    }

    @Test
    fun testLongTermMonitoring_Preservation() {
        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            "Monitoring Test", "Desc", 
            ClosedLoopEngine.evaluate(50.0, 50.0, 60.0, emptyList())
        )
        val manifest = BlueprintImplementationPlanner.planImplementation(blueprint)
        
        assertTrue(manifest.monitoringRequirements.isNotEmpty())
        assertTrue(manifest.monitoringRequirements.any { it.contains("30-day window") })
    }
}

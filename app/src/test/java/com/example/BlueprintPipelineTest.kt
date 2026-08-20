package com.example

import com.example.data.*
import com.example.data.blueprint.BuilderInstructionGenerator
import org.junit.Assert.*
import org.junit.Test

class BlueprintPipelineTest {

    @Test
    fun testBlueprintValidationGates_SimulationOnly() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 80.0,
            targetScore = 85.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 1000, score = 80.0, quality = 0.95)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Sim Strategy", "Testing sim", report)

        assertEquals(StrategyValidationState.SIMULATION_SUPPORTED, blueprint.validationState)
        assertTrue(blueprint.nextExperimentRecommendation.contains("Conduct controlled experimental trials"))
        assertTrue(blueprint.diagnosis.hypotheses.isNotEmpty())
        
        val instructions = BuilderInstructionGenerator.generate(blueprint)
        val forensicText = instructions.prompts[0].promptText
        assertTrue(forensicText.contains("Evidence Tier: SIMULATION_SUPPORTED"))
        assertTrue(forensicText.contains("HYPOTHESIS:"))
    }

    @Test
    fun testBlueprintValidationGates_ProductionValidated() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 65.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 65.0, quality = 0.95)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Prod Strategy", "Testing prod", report)

        assertEquals(StrategyValidationState.PRODUCTION_VALIDATED, blueprint.validationState)
        assertTrue(blueprint.productionValidationRequirements.isEmpty() || blueprint.productionValidationRequirements.none { it.contains("Minimum of 5") })
        
        val instructions = BuilderInstructionGenerator.generate(blueprint)
        val forensicText = instructions.prompts[0].promptText
        assertTrue(forensicText.contains("Evidence Tier: PRODUCTION_VALIDATED"))
        assertFalse(forensicText.contains("HYPOTHESIS:"))
    }

    @Test
    fun testBlueprintValidationGates_ProductionRegression() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 40.0,
            targetScore = 60.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 40.0, quality = 0.9)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Regression Fix", "Testing regression", report)

        assertEquals(StrategyValidationState.PRODUCTION_VALIDATED, blueprint.validationState)
        assertTrue(blueprint.productionValidationRequirements.any { it.contains("Immediate rollback") })
        assertTrue(blueprint.riskAssessment.contains("CRITICAL RISK / REGRESSION"))
    }
}

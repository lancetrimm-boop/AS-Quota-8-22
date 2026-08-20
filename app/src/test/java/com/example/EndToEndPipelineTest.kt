package com.example

import com.example.data.*
import com.example.data.blueprint.BuilderInstructionGenerator
import com.example.util.IntelligenceExporter
import org.junit.Assert.*
import org.junit.Test

class EndToEndPipelineTest {

    @Test
    fun testScenario1_NoProductionEvidence() {
        // 1. User behavior: Zero engagement
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 50.0,
            targetScore = 60.0,
            evidenceList = emptyList()
        )

        // Verify Outcome
        assertEquals(OutcomeClassification.INSUFFICIENT_DATA, report.outcomeClassification)
        assertEquals(0.0, report.productionConfidence, 0.01)

        // 2. Blueprint Generation
        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            "Test Strategy", "Testing no evidence", report
        )

        // Verify Provenance
        assertTrue(blueprint.recommendationNotice.contains("NO PRODUCTION MODIFICATION"))
        assertTrue(blueprint.proposedModifications.all { it.modificationType == ModificationType.NO_CHANGE })
        
        // 3. Builder Instructions
        val instructions = BuilderInstructionGenerator.generate(blueprint)
        val text = instructions.toFormattedString()
        assertTrue(text.contains("Modification Type: NO_CHANGE"))

        // 4. Export
        val json = IntelligenceExporter.exportToJson(report, blueprint)
        assertTrue(json.contains("\"productionCount\": 0"))
    }

    @Test
    fun testScenario2_ExperimentalOnly() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 50, score = 70.0, quality = 0.8)
        )
        val report = ClosedLoopEngine.evaluate(50.0, 70.0, 75.0, evidence)

        assertEquals(OutcomeClassification.EXPERIMENT_INCONCLUSIVE, report.outcomeClassification)
        
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Experimental Strategy", "Testing exp", report)

        // Verify Provenance: Should be EXPERIMENTAL_CHANGE
        assertTrue(blueprint.recommendationNotice.contains("NO PRODUCTION MODIFICATION"))
        assertTrue(blueprint.proposedModifications.any { it.modificationType == ModificationType.EXPERIMENTAL_CHANGE })
        
        val instructions = BuilderInstructionGenerator.generate(blueprint)
        assertTrue(instructions.toFormattedString().contains("Modification Type: EXPERIMENTAL_CHANGE"))
    }

    @Test
    fun testScenario3_SimulationOnly() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 500, score = 80.0, quality = 0.9)
        )
        val report = ClosedLoopEngine.evaluate(50.0, 80.0, 85.0, evidence)

        assertEquals(OutcomeClassification.EXPERIMENT_INCONCLUSIVE, report.outcomeClassification)
        
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Simulation Strategy", "Testing sim", report)

        // Verify Provenance: Should be SIMULATED_CHANGE
        assertTrue(blueprint.proposedModifications.any { it.modificationType == ModificationType.SIMULATED_CHANGE })
        
        val instructions = BuilderInstructionGenerator.generate(blueprint)
        assertTrue(instructions.toFormattedString().contains("Modification Tier: SIMULATED_CHANGE"))
    }

    @Test
    fun testScenario5_ProductionImprovement() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 65.0, quality = 0.9)
        )
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 65.0, evidence)

        assertEquals(OutcomeClassification.SIGNIFICANT_IMPROVEMENT, report.outcomeClassification)
        assertTrue(report.productionImprovementEstablished)

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Production Strategy", "Testing improvement", report)

        // Verify Provenance: Should be RECOMMENDED_CHANGE
        assertTrue(blueprint.recommendationNotice.contains("RECOMMENDED CHANGE"))
        assertTrue(blueprint.proposedModifications.any { it.modificationType == ModificationType.RECOMMENDED_CHANGE })

        val instructions = BuilderInstructionGenerator.generate(blueprint)
        assertTrue(instructions.toFormattedString().contains("Modification Type: RECOMMENDED_CHANGE"))
    }

    @Test
    fun testScenario6_ProductionRegression() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 40.0, quality = 0.9)
        )
        val report = ClosedLoopEngine.evaluate(50.0, 40.0, 60.0, evidence)

        assertEquals(OutcomeClassification.REGRESSION_DETECTED, report.outcomeClassification)
        assertTrue(report.productionRegressionEstablished)

        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Regression Fix", "Testing regression", report)

        // Verify Provenance: Should be NO_CHANGE (or a fix strategy)
        assertTrue(blueprint.recommendationNotice.contains("NO PRODUCTION MODIFICATION"))
        assertTrue(blueprint.proposedModifications.all { it.modificationType == ModificationType.NO_CHANGE })
        assertTrue(blueprint.riskAssessment.contains("CRITICAL RISK / REGRESSION"))
    }

    @Test
    fun testScenario7_MixedEvidence() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 65.0, quality = 0.9),
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 50, score = 70.0, quality = 0.8),
            EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 1000, score = 80.0, quality = 0.95)
        )
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 75.0, evidence)

        assertEquals(OutcomeClassification.SIGNIFICANT_IMPROVEMENT, report.outcomeClassification)
        
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Mixed Strategy", "Testing mixed", report)

        // Production improvement established, so Recommended change
        assertTrue(blueprint.recommendationNotice.contains("RECOMMENDED CHANGE"))
        
        val json = IntelligenceExporter.exportToJson(report, blueprint)
        assertTrue(json.contains("\"productionCount\": 100"))
        assertTrue(json.contains("\"experimentalCount\": 50"))
        assertTrue(json.contains("\"simulationCount\": 1000"))
    }
}

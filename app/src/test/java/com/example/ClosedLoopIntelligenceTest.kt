package com.example

import com.example.data.*
import com.example.util.IntelligenceExporter
import org.junit.Assert.*
import org.junit.Test

class ClosedLoopIntelligenceTest {

    @Test
    fun test1_ZeroProductionEvidence_CannotClaimProductionImprovement() {
        val baseline = 50.0
        val measured = 50.0
        val target = 50.0

        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 100, score = 65.0, quality = 0.9),
            EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 1000, score = 80.0, quality = 0.95)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = evidence
        )

        assertEquals(0, report.productionSampleCount)
        assertFalse(report.productionImprovementEstablished)
        assertEquals(0.0, report.productionConfidence, 0.001)
        assertNotEquals(OutcomeClassification.IMPROVEMENT_DETECTED, report.outcomeClassification)
        assertNotEquals(OutcomeClassification.SIGNIFICANT_IMPROVEMENT, report.outcomeClassification)
        assertEquals(OutcomeClassification.EXPERIMENT_INCONCLUSIVE, report.outcomeClassification)
        assertEquals(TargetValidity.UNCHANGED_TARGET, report.targetValidity)
        assertTrue(report.unknowns.any { it.contains("UNKNOWN") })
    }

    @Test
    fun test2_EqualBaselineAndMeasuredScore_ResultsInNoMeasurableChange() {
        val baseline = 50.0
        val measured = 50.0
        val target = 60.0

        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 20, score = 50.0, quality = 0.9)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = evidence
        )

        assertEquals(20, report.productionSampleCount)
        assertEquals(OutcomeClassification.NO_MEASURABLE_CHANGE, report.outcomeClassification)
        assertFalse(report.productionImprovementEstablished)
        assertFalse(report.productionRegressionEstablished)
    }

    @Test
    fun test3_TargetEqualsBaseline_FlagsUnchangedTarget() {
        val baseline = 50.0
        val measured = 50.0
        val target = 50.0

        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = emptyList()
        )

        assertEquals(TargetValidity.UNCHANGED_TARGET, report.targetValidity)
        assertTrue(report.knownFacts.any { it.contains("Target score equals baseline score") })
    }

    @Test
    fun test4_ExperimentalImprovementWithoutProductionEvidence_ProductionImprovementIsFalse() {
        val baseline = 50.0
        val measured = 70.0
        val target = 75.0

        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 150, score = 70.0, quality = 0.85)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = evidence
        )

        assertEquals(0, report.productionSampleCount)
        assertFalse(report.productionImprovementEstablished)
        assertEquals(0.0, report.productionConfidence, 0.001)
        assertTrue(report.experimentalConfidence > 0.5)
        assertEquals(OutcomeClassification.EXPERIMENT_INCONCLUSIVE, report.outcomeClassification)
    }

    @Test
    fun test5_SimulationImprovementWithoutProductionEvidence_ProductionImprovementIsFalse() {
        val baseline = 50.0
        val measured = 85.0
        val target = 90.0

        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 2000, score = 85.0, quality = 0.9)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = evidence
        )

        assertEquals(0, report.productionSampleCount)
        assertFalse(report.productionImprovementEstablished)
        assertEquals(0.0, report.productionConfidence, 0.001)
        assertTrue(report.simulatedFindings.isNotEmpty())
    }

    @Test
    fun test6_ConfirmedProductionImprovement_ClassifiesImprovement() {
        val baseline = 50.0
        val measured = 65.0
        val target = 65.0

        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 65.0, quality = 0.95)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = evidence
        )

        assertEquals(100, report.productionSampleCount)
        assertTrue(report.productionImprovementEstablished)
        assertTrue(report.productionConfidence >= 0.7)
        assertEquals(OutcomeClassification.SIGNIFICANT_IMPROVEMENT, report.outcomeClassification)
    }

    @Test
    fun test7_ProductionRegression_ClassifiesRegression() {
        val baseline = 50.0
        val measured = 35.0
        val target = 60.0

        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 50, score = 35.0, quality = 0.9)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = evidence
        )

        assertEquals(50, report.productionSampleCount)
        assertFalse(report.productionImprovementEstablished)
        assertTrue(report.productionRegressionEstablished)
        assertEquals(OutcomeClassification.REGRESSION_DETECTED, report.outcomeClassification)
    }

    @Test
    fun test8_InsufficientData_WhenZeroEvidence() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 50.0,
            targetScore = 60.0,
            evidenceList = emptyList()
        )

        assertEquals(0, report.productionSampleCount)
        assertEquals(0, report.experimentalSampleCount)
        assertEquals(0, report.simulationSampleCount)
        assertEquals(OutcomeClassification.INSUFFICIENT_DATA, report.outcomeClassification)
    }

    @Test
    fun test9_ExperimentInconclusive_WhenOnlyWeakOrIncompleteExperimentEvidence() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 3, score = 52.0, quality = 0.4)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 52.0,
            targetScore = 60.0,
            evidenceList = evidence
        )

        assertEquals(0, report.productionSampleCount)
        assertEquals(OutcomeClassification.EXPERIMENT_INCONCLUSIVE, report.outcomeClassification)
        assertFalse(report.productionImprovementEstablished)
    }

    @Test
    fun test10_ConfidenceIntegrity_ZeroProductionSamplesZeroProductionConfidence() {
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 500, score = 90.0, quality = 1.0),
            EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 10000, score = 95.0, quality = 1.0)
        )

        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 90.0,
            targetScore = 90.0,
            evidenceList = evidence
        )

        assertEquals(0.0, report.productionConfidence, 0.0001)
        assertEquals(0.0, report.overallConfidence, 0.0001)
        assertFalse(report.productionImprovementEstablished)
    }

    @Test
    fun test11_ExportIntegrity_ExposesFullEvidenceBreakdown() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 50.0,
            targetScore = 50.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 50, score = 55.0, quality = 0.8)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "A/B Optimization Strategy",
            description = "Test optimization",
            report = report,
            actions = listOf(StrategyAction("Tune AISkip", "Adjust skip threshold", "HIGH", "AISkipEngine"))
        )

        val jsonExport = IntelligenceExporter.exportToJson(report, blueprint)
        val textExport = IntelligenceExporter.exportToText(report, blueprint)
        val markdownExport = IntelligenceExporter.exportToMarkdown(report, blueprint)
        val htmlExport = IntelligenceExporter.exportToHtml(report, blueprint)

        assertTrue(jsonExport.contains("\"productionSampleCount\": 0"))
        assertTrue(textExport.contains("Production Evidence Samples : 0"))
        assertTrue(textExport.contains("Production Improvement Established : NO"))
        assertTrue(markdownExport.contains("Production Improvement Established:** **`NO`**"))
        assertTrue(htmlExport.contains("Production Evidence Samples: 0"))
        assertTrue(htmlExport.contains("Production Improvement Established: NO"))
        assertEquals(StrategyValidationState.EXPERIMENTALLY_SUPPORTED, blueprint.validationState)
        assertTrue(blueprint.requiresProductionValidation)
    }

    @Test
    fun test12_StrategyBlueprint15Sections_ZeroProductionEvidence_ExplicitNoModificationNotice() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 100, score = 65.0, quality = 0.85),
                EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 500, score = 70.0, quality = 0.90)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "A/B Optimization Blueprint",
            description = "Experimental test blueprint",
            report = report,
            actions = listOf(StrategyAction("Adjust Skip Weight", "Increase skip penalty", "HIGH", "AISkipEngine"))
        )

        // Rule 1: recommendationNotice must explicitly state NO PRODUCTION MODIFICATION RECOMMENDED
        assertTrue(blueprint.recommendationNotice.startsWith("NO PRODUCTION MODIFICATION RECOMMENDED"))

        // Rule 2: Inspect all 15 Sections
        // 1. Identity
        assertNotNull(blueprint.identity.blueprintId)
        assertEquals("1.0.0", blueprint.identity.version)
        assertNotNull(blueprint.identity.trigger)
        assertEquals(StrategyStatus.DRAFT, blueprint.identity.status)

        // 2. Problem Diagnosis
        assertNotNull(blueprint.diagnosis.problemStatement)
        assertNotNull(blueprint.diagnosis.beliefDescription)
        assertNotNull(blueprint.diagnosis.affectedComponent)
        assertTrue(blueprint.diagnosis.knownFacts.isNotEmpty() || blueprint.diagnosis.inferences.isNotEmpty())

        // 3. Evidence
        assertEquals(0, blueprint.evidence.productionCount)
        assertEquals(100, blueprint.evidence.experimentalCount)
        assertEquals(500, blueprint.evidence.simulationCount)
        assertNotNull(blueprint.evidence.provenance)

        // 4. Baseline State
        assertNotNull(blueprint.baselineState.pairwiseWeights)
        assertNotNull(blueprint.baselineState.aiSkipWeights)
        assertNotNull(blueprint.baselineState.tasteDnaWeights)

        // 5. Target State
        assertNotNull(blueprint.targetState.targetMetric)
        assertEquals(70.0, blueprint.targetState.targetValue ?: 0.0, 0.01)

        // 6. Strategy Selection
        assertNotNull(blueprint.strategySelection.selectedStrategy)
        assertNotNull(blueprint.strategySelection.rationale)

        // 7. Proposed Modifications - NONE should be RECOMMENDED_CHANGE when 0 production samples
        assertTrue(blueprint.proposedModifications.none { it.modificationType == ModificationType.RECOMMENDED_CHANGE })
        assertTrue(blueprint.proposedModifications.any { it.modificationType == ModificationType.EXPERIMENTAL_CHANGE })

        // 8. Taste DNA Modifications
        assertTrue(blueprint.tasteDnaModifications.isNotEmpty())

        // 9. Recommendation Engine Modifications
        assertTrue(blueprint.recommendationEngineModifications.isNotEmpty())

        // 10. Execution Plan
        assertTrue(blueprint.executionPlan.intendedActions.isNotEmpty())
        assertTrue(blueprint.executionPlan.affectedComponents.isNotEmpty())

        // 11. Experiment Design
        assertNotNull(blueprint.experimentDesign.controlGroupConfig)
        assertNotNull(blueprint.experimentDesign.experimentalGroupConfig)

        // 12. Expected Outcome
        assertNotNull(blueprint.expectedOutcome.expectedImprovement)
        assertNotNull(blueprint.expectedOutcome.riskLevel)

        // 13. Actual Outcome
        assertEquals(65.0, blueprint.actualOutcome.measuredScore ?: 0.0, 0.01)

        // 14. Learning
        assertTrue(blueprint.learning.keyInsights.isNotEmpty())

        // 15. Version History
        assertTrue(blueprint.versionHistory.isNotEmpty())
    }

    @Test
    fun test13_StrategyBlueprint15Sections_WithProductionEvidence_RecommendsChange() {
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 65.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 65.0, quality = 0.95)
            )
        )

        val blueprint = StrategyBlueprintGenerator.generateBlueprint(
            title = "Production Optimization Blueprint",
            description = "Production confirmed blueprint",
            report = report
        )

        // Rule: recommendationNotice recommends modification
        assertTrue(blueprint.recommendationNotice.contains("RECOMMENDED"))
        assertEquals(StrategyStatus.PROPOSED, blueprint.identity.status)

        // Section 7 contains RECOMMENDED_CHANGE
        assertTrue(blueprint.proposedModifications.any { it.modificationType == ModificationType.RECOMMENDED_CHANGE })
    }
}

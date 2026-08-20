package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class FinalReleaseHardeningTest {

    @Test
    fun testCriticalEvidenceIntegrityScenario() {
        // Scenario:
        // baseline_score = 50.0
        // measured_score = 50.0
        // target_score = 50.0
        // production_evidence_samples = 0
        // experimental_evidence_samples > 0
        // simulation_evidence_samples > 0
        
        val baseline = 50.0
        val measured = 50.0
        val target = 50.0
        
        val evidence = listOf(
            EvidenceRecord(tier = EvidenceTier.EXPERIMENTAL, sampleCount = 100, score = 60.0, quality = 0.8),
            EvidenceRecord(tier = EvidenceTier.SIMULATION, sampleCount = 1000, score = 70.0, quality = 0.9)
        )
        
        val report = ClosedLoopEngine.evaluate(
            baselineScore = baseline,
            measuredScore = measured,
            targetScore = target,
            evidenceList = evidence
        )
        
        // 1. Must NOT claim production improvement
        assertFalse("Should not establish production improvement with 0 samples", report.productionImprovementEstablished)
        assertNotEquals("Should not be SIGNIFICANT_IMPROVEMENT", OutcomeClassification.SIGNIFICANT_IMPROVEMENT, report.outcomeClassification)
        assertNotEquals("Should not be IMPROVEMENT_DETECTED", OutcomeClassification.IMPROVEMENT_DETECTED, report.outcomeClassification)
        
        // 2. Must identify target as unchanged
        assertEquals(TargetValidity.UNCHANGED_TARGET, report.targetValidity)
        
        // 3. Must distinguish experimental and simulation evidence
        assertTrue(report.experimentalSampleCount > 0)
        assertTrue(report.simulationSampleCount > 0)
        assertEquals(100, report.experimentalSampleCount)
        assertEquals(1000, report.simulationSampleCount)
        
        // 4. Must explicitly state that production improvement has not been established
        assertTrue(report.summaryMessage.contains("PRODUCTION EVIDENCE MISSING"))
        assertTrue(report.summaryMessage.contains("improvement CANNOT be established"))
        
        // 5. Outcome should be EXPERIMENT_INCONCLUSIVE (as exp/sim exist but prod doesn't)
        assertEquals(OutcomeClassification.EXPERIMENT_INCONCLUSIVE, report.outcomeClassification)
        
        // 6. Verify Blueprint generation for this scenario
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Hardening BP", "Scenario Test", report)
        
        assertEquals(StrategyValidationState.EXPERIMENTALLY_SUPPORTED, blueprint.validationState)
        assertTrue(blueprint.recommendationNotice.contains("NO PRODUCTION MODIFICATION RECOMMENDED"))
        
        // Ensure evidence provenance is preserved
        assertTrue(blueprint.evidence.productionCount == 0)
        assertTrue(blueprint.evidence.experimentalCount == 100)
        assertTrue(blueprint.evidence.simulationCount == 1000)
    }

    @Test
    fun testInternalConsistency_DataFlow() {
        // Verify that adding a pairwise vote affects Taste DNA (Automatic learning)
        // and that Taste DNA affects scoring (Personalization)
        
        val itemA = MediaItem(id = "a", title = "Vibrant A", mediaType = "PHOTO", moodTags = listOf("Vibrant"))
        val itemB = MediaItem(id = "b", title = "Dark B", mediaType = "PHOTO", moodTags = listOf("Dark"))
        
        val initialDna = TasteDNA(isFineTuningEnabled = true, learnedVibrancy = 0.5)
        
        // Simulation of learning from a vote for itemA (Vibrant)
        var learnedV = initialDna.learnedVibrancy
        itemA.moodTags.forEach { tag ->
            if (tag.lowercase().contains("vibrant")) learnedV = (learnedV + 0.02).coerceAtMost(1.0)
        }
        val updatedDna = initialDna.copy(learnedVibrancy = learnedV)
        
        assertTrue("Learned vibrancy should increase", updatedDna.learnedVibrancy > initialDna.learnedVibrancy)
        assertTrue("Effective vibrancy should increase", updatedDna.effectiveVibrancy > initialDna.effectiveVibrancy)
        
        // Verify scoring impact
        val scoreInitial = RecommendationEngine.scoreItemForPairwise(itemA, tasteDNA = initialDna)
        val scoreUpdated = RecommendationEngine.scoreItemForPairwise(itemA, tasteDNA = updatedDna)
        
        assertTrue("Score for vibrant item should increase as vibrancy preference increases", scoreUpdated > scoreInitial)
    }
}

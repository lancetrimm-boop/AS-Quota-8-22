package com.example.data.blueprint

import com.example.data.ClosedLoopEngine
import com.example.data.ClosedLoopReport
import com.example.data.EvidenceRecord
import com.example.data.EvidenceTier
import com.example.data.OutcomeClassification
import com.example.data.StrategyBlueprint

/**
 * Validates the results of a Blueprint implementation using fresh production evidence.
 */
object BlueprintImplementationValidator {

    /**
     * Performs post-implementation validation.
     * @param blueprint The original blueprint that was implemented.
     * @param manifest The implementation manifest associated with the blueprint.
     * @param postEvidence Fresh evidence records collected after implementation.
     */
    fun validateImplementation(
        blueprint: StrategyBlueprint,
        manifest: BlueprintImplementationManifest,
        postEvidence: List<EvidenceRecord>
    ): Pair<BlueprintImplementationManifest, StrategyBlueprint> {
        
        // 1. Evaluate outcome using post-implementation evidence
        val postReport = ClosedLoopEngine.evaluate(
            baselineScore = blueprint.baselineState.engagementScore,
            measuredScore = postEvidence.filter { it.tier == EvidenceTier.PRODUCTION }.map { it.score }.average().takeIf { !it.isNaN() } ?: blueprint.baselineState.engagementScore,
            targetScore = blueprint.targetState.targetValue,
            evidenceList = postEvidence
        )

        // 2. Determine Validation Status and Closed Loop Outcome
        val (status, outcome) = when {
            postReport.productionRegressionEstablished -> 
                ValidationStatus.REGRESSION_DETECTED to ClosedLoopOutcome.REGRESSION
            postReport.productionSampleCount < 5 -> 
                ValidationStatus.IMPLEMENTATION_INCONCLUSIVE to ClosedLoopOutcome.INCONCLUSIVE
            postReport.productionImprovementEstablished && postReport.measuredScore >= blueprint.targetState.targetValue -> 
                ValidationStatus.IMPLEMENTATION_VALIDATED to ClosedLoopOutcome.SUCCESS
            postReport.measuredScore > blueprint.baselineState.engagementScore -> 
                ValidationStatus.IMPLEMENTATION_VALIDATED to ClosedLoopOutcome.PARTIAL_SUCCESS
            else -> 
                ValidationStatus.IMPLEMENTATION_FAILED to ClosedLoopOutcome.FAILED
        }

        // 3. Generate Automatic Next Actions
        val nextAction = when (outcome) {
            ClosedLoopOutcome.SUCCESS -> "Retain strategy. Continue production monitoring for stability."
            ClosedLoopOutcome.PARTIAL_SUCCESS -> "Refine strategy. Observed improvement below target. Generate revised Blueprint."
            ClosedLoopOutcome.INCONCLUSIVE -> "Collect additional evidence. Do not overclaim validation. Maintain current parameters."
            ClosedLoopOutcome.REGRESSION -> "ROLLBACK REQUIRED. Measured regression established. Investigate root cause."
            ClosedLoopOutcome.FAILED -> "Reject strategy. No improvement measured. Generate alternative strategy."
        }

        // 4. Causality Assessment
        val causality = if (status == ValidationStatus.IMPLEMENTATION_VALIDATED && postReport.statisticalStrength > 0.8) {
            "Causal validation: Strongly supported. Implemented changes (${blueprint.proposedModifications.size}) correlate with significant score increase (+${"%.2f".format(postReport.measuredScore - postReport.baselineScore)})."
        } else if (status == ValidationStatus.IMPLEMENTATION_VALIDATED) {
            "Causal validation: Observed improvement persists; causality not yet confirmed due to statistical noise."
        } else {
            "Causal validation: Not established."
        }

        // 5. Create New Blueprint Version (v1 -> v2)
        val newVersion = blueprint.identity.version.split(".").let {
            if (it.size == 3) "${it[0]}.${it[1]}.${it[2].toInt() + 1}" else "1.0.1"
        }
        
        val updatedBlueprint = blueprint.copy(
            identity = blueprint.identity.copy(
                version = newVersion,
                status = if (outcome == ClosedLoopOutcome.SUCCESS) 
                    com.example.data.StrategyStatus.COMPLETED else com.example.data.StrategyStatus.EXECUTING,
                parentBlueprintId = blueprint.identity.blueprintId // Link lineage
            ),
            evidence = blueprint.evidence.copy(
                productionCount = postReport.productionSampleCount,
                evidenceQuality = postReport.overallConfidence
            ),
            actualOutcome = blueprint.actualOutcome.copy(
                measuredScore = postReport.measuredScore,
                outcomeClassification = postReport.outcomeClassification.name,
                deltaVsTarget = postReport.measuredScore - blueprint.targetState.targetValue,
                verificationTimestamp = System.currentTimeMillis()
            ),
            nextExperimentRecommendation = nextAction,
            learning = blueprint.learning.copy(
                keyInsights = blueprint.learning.keyInsights + "Post-implementation score: ${postReport.measuredScore}. Outcome: ${outcome.name}."
            ),
            versionHistory = blueprint.versionHistory + com.example.data.BlueprintVersionEntry(
                version = newVersion,
                timestamp = System.currentTimeMillis(),
                notes = "Closed Loop Feedback Cycle complete. Outcome: ${outcome.name}. Next Action: $nextAction"
            )
        )

        // 6. Update Manifest
        val updatedManifest = manifest.copy(
            validationStatus = status,
            closedLoopOutcome = outcome,
            causalValidationSummary = causality,
            postImplementationScore = postReport.measuredScore,
            postImplementationSamples = postReport.productionSampleCount,
            manifestStatus = if (status == ValidationStatus.IMPLEMENTATION_VALIDATED) 
                ImplementationStatus.VALIDATED else ImplementationStatus.READY_FOR_IMPLEMENTATION,
            auditNotes = manifest.auditNotes + "\nClosed Loop Result: ${outcome.name}. Next Action: $nextAction. Score: ${postReport.measuredScore}."
        )

        return updatedManifest to updatedBlueprint
    }
}

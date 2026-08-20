package com.example.data.blueprint

import com.example.data.EvidenceTier
import com.example.data.StrategyBlueprint
import com.example.data.StrategyValidationState
import java.util.UUID

/**
 * Generator that creates a Blueprint Implementation Manifest from a Strategy Blueprint.
 * Maps high-level blueprint recommendations to granular code-level modification plans.
 */
object BlueprintImplementationManifestGenerator {

    private var currentRepositoryUrl: String? = null
    private var currentBranch: String? = null
    private var currentCommitHash: String? = null

    /**
     * Sets the global repository context for manifest generation.
     */
    fun setRepositoryContext(url: String?, branch: String?, hash: String?) {
        currentRepositoryUrl = url
        currentBranch = branch
        currentCommitHash = hash
    }

    fun generate(blueprint: StrategyBlueprint): BlueprintImplementationManifest {
        val report = blueprint.closedLoopReport
        val highestTier = when {
            blueprint.evidence.productionCount > 0 -> EvidenceTier.PRODUCTION
            blueprint.evidence.experimentalCount > 0 -> EvidenceTier.EXPERIMENTAL
            blueprint.evidence.simulationCount > 0 -> EvidenceTier.SIMULATION
            else -> EvidenceTier.SIMULATION // Default for unvalidated
        }

        val observedImprovement = report?.productionImprovementEstablished ?: false

        val modifications = mutableListOf<CodeModificationPlan>()

        // Map Proposed Modifications from Section 7
        blueprint.proposedModifications.forEach { mod ->
            modifications.add(mapProposedToCodePlan(mod, blueprint, highestTier))
        }

        // Map Taste DNA Modifications from Section 8
        blueprint.tasteDnaModifications.forEach { mod ->
            modifications.add(mapTasteDnaToCodePlan(mod, blueprint, highestTier))
        }

        return BlueprintImplementationManifest(
            manifestId = UUID.randomUUID().toString(),
            blueprintId = blueprint.identity.blueprintId,
            blueprintVersion = blueprint.identity.version,
            strategyName = blueprint.strategySelection.selectedStrategy,
            blueprintValidationState = blueprint.validationState,
            evidenceTier = highestTier,
            productionSampleCount = blueprint.evidence.productionCount,
            confidence = blueprint.evidence.evidenceQuality,
            baselineScore = blueprint.baselineState.engagementScore,
            measuredScore = blueprint.actualOutcome.measuredScore ?: blueprint.baselineState.engagementScore,
            targetScore = blueprint.targetState.targetValue,
            outcomeClassification = report?.outcomeClassification ?: com.example.data.OutcomeClassification.INSUFFICIENT_DATA,
            observedProductionImprovement = observedImprovement,
            manifestStatus = ImplementationStatus.PENDING,
            proposedModifications = modifications,
            auditNotes = "Generated from Blueprint ${blueprint.identity.blueprintId} v${blueprint.identity.version}. " +
                         "Observed production improvement: ${if (observedImprovement) "YES" else "NO"}. " +
                         "Evidence tier: ${highestTier.name}.",
            repositoryUrl = currentRepositoryUrl,
            branch = currentBranch,
            commitHash = currentCommitHash
        )
    }

    private fun mapProposedToCodePlan(
        mod: com.example.data.ProposedModification,
        blueprint: StrategyBlueprint,
        highestTier: EvidenceTier
    ): CodeModificationPlan {
        val (pkg, cls, file) = resolveComponent(mod.component)
        
        return CodeModificationPlan(
            modificationId = mod.modificationId,
            componentName = mod.component,
            sourceFile = file,
            packageName = pkg,
            className = cls,
            methodOrProperty = mod.parameter,
            blueprintExpectedValue = mod.currentValue,
            proposedValue = mod.proposedValue,
            changeType = resolveChangeType(mod.parameter),
            expectedBehavioralEffect = mod.expectedEffect,
            evidenceSupportingChange = mod.supportingEvidence,
            evidenceTier = highestTier,
            confidence = mod.confidence,
            risk = blueprint.riskAssessment,
            requiredTests = listOf("Unit tests in ${cls}Test.kt", "Regression test suite"),
            validationCriteria = "Personalization score >= ${blueprint.targetState.targetValue}",
            rollbackCriteria = "Personalization score drops below baseline ${blueprint.baselineState.engagementScore}",
            implementationStatus = ImplementationStatus.PENDING,
            isCausallyValidated = false, // Set to false by default as per requirement
            filesToModify = listOf(file),
            repositoryUrl = currentRepositoryUrl,
            branch = currentBranch,
            commitHash = currentCommitHash
        )
    }

    private fun mapTasteDnaToCodePlan(
        mod: com.example.data.TasteDnaModification,
        blueprint: StrategyBlueprint,
        highestTier: EvidenceTier
    ): CodeModificationPlan {
        val fileName = "TasteDNA.kt"
        return CodeModificationPlan(
            modificationId = UUID.randomUUID().toString(),
            componentName = "TasteDNA",
            sourceFile = fileName,
            packageName = "com.example.data",
            className = "TasteDNA",
            methodOrProperty = mod.dimension,
            blueprintExpectedValue = mod.previousValue.toString(),
            proposedValue = mod.proposedValue.toString(),
            changeType = ImplementationChangeType.WEIGHT_CHANGE,
            expectedBehavioralEffect = "Adjust ${mod.dimension} preference weighting",
            evidenceSupportingChange = mod.evidence,
            evidenceTier = highestTier,
            confidence = mod.confidence,
            risk = blueprint.riskAssessment,
            requiredTests = listOf("TasteDNATest.kt"),
            validationCriteria = "Alignment with user engagement trends",
            rollbackCriteria = "User engagement regression",
            implementationStatus = ImplementationStatus.PENDING,
            isCausallyValidated = false,
            filesToModify = listOf(fileName),
            repositoryUrl = currentRepositoryUrl,
            branch = currentBranch,
            commitHash = currentCommitHash
        )
    }

    private fun resolveComponent(componentName: String): Triple<String, String, String> {
        val pkg = "com.example.data"
        val path = ""
        return when (componentName) {
            "AISkipEngine" -> Triple(pkg, "AISkipEngine", path + "AISkipEngine.kt")
            "RecommendationEngine" -> Triple(pkg, "RecommendationEngine", path + "RecommendationEngine.kt")
            "PairwiseSystem" -> Triple(pkg, "MediaRepository", path + "MediaRepository.kt")
            else -> Triple(pkg, componentName, path + "$componentName.kt")
        }
    }

    private fun resolveChangeType(parameterName: String): ImplementationChangeType {
        val lower = parameterName.lowercase()
        return when {
            lower.contains("weight") -> ImplementationChangeType.WEIGHT_CHANGE
            lower.contains("threshold") -> ImplementationChangeType.THRESHOLD_CHANGE
            lower.contains("calibration") -> ImplementationChangeType.CALIBRATION_CHANGE
            lower.contains("algorithm") -> ImplementationChangeType.ALGORITHM_CHANGE
            else -> ImplementationChangeType.PARAMETER_CHANGE
        }
    }
}

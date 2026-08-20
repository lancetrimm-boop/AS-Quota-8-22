package com.example.data.blueprint

import com.example.data.ModificationType

/**
 * Strict Blueprint Validator for Aura Blueprint Artifacts.
 * Ensures data completeness, version compliance, score validity, evidence consistency,
 * and supported target modifications before any blueprint can be validated or proposed.
 */
object BlueprintValidator {

    private val SEMVER_REGEX = Regex("""^\d+\.\d+\.\d+$""")
    private val SUPPORTED_COMPONENTS = setOf(
        "AISkipEngine",
        "TasteDNA",
        "RecommendationEngine",
        "PairwiseEngine",
        "PairwiseSystem",
        "AuraTelemetryService",
        "StrategyBlueprintEngine",
        "Recommender & Taste DNA",
        "EngagementEngine"
    )

    fun validate(artifact: BlueprintArtifact): BlueprintValidationResult {
        val errors = mutableListOf<BlueprintValidationError>()
        val warnings = mutableListOf<BlueprintValidationWarning>()
        val bp = artifact.strategyBlueprint

        // 1. Version validation
        if (artifact.schemaVersion.isBlank()) {
            errors.add(BlueprintValidationError("schema_version", "Schema version cannot be blank."))
        } else if (!SEMVER_REGEX.matches(artifact.schemaVersion)) {
            errors.add(BlueprintValidationError("schema_version", "Malformed schema version '${artifact.schemaVersion}'. Must be semver format (X.Y.Z)."))
        } else if (!BlueprintArtifact.COMPATIBLE_SCHEMA_PREFIXES.any { artifact.schemaVersion.startsWith(it) }) {
            errors.add(BlueprintValidationError("schema_version", "Incompatible schema version '${artifact.schemaVersion}'. Expected version 1.x."))
        }

        if (artifact.blueprintVersion.isBlank()) {
            errors.add(BlueprintValidationError("blueprint_version", "Blueprint version cannot be blank."))
        } else if (!SEMVER_REGEX.matches(artifact.blueprintVersion)) {
            errors.add(BlueprintValidationError("blueprint_version", "Malformed blueprint version '${artifact.blueprintVersion}'. Must be semver format (X.Y.Z)."))
        }

        if (artifact.blueprintId.isBlank()) {
            errors.add(BlueprintValidationError("blueprint_id", "Blueprint ID cannot be blank."))
        }

        // 2. Missing Required Fields in Strategy Blueprint
        if (bp.title.isBlank()) {
            errors.add(BlueprintValidationError("title", "Blueprint title cannot be blank."))
        }

        if (bp.diagnosis.problemStatement.isBlank()) {
            errors.add(BlueprintValidationError("diagnosis.problemStatement", "Problem statement cannot be blank."))
        }

        if (bp.diagnosis.affectedComponent.isBlank()) {
            errors.add(BlueprintValidationError("diagnosis.affectedComponent", "Affected component cannot be blank."))
        }

        if (bp.strategySelection.selectedStrategy.isBlank()) {
            errors.add(BlueprintValidationError("strategySelection.selectedStrategy", "Selected strategy name cannot be blank."))
        }

        // 3. Invalid Values Range Checks
        if (bp.diagnosis.diagnosticConfidence !in 0.0..1.0) {
            errors.add(BlueprintValidationError("diagnosis.diagnosticConfidence", "Diagnostic confidence must be between 0.0 and 1.0 (was ${bp.diagnosis.diagnosticConfidence})."))
        }

        if (bp.evidence.evidenceQuality !in 0.0..1.0) {
            errors.add(BlueprintValidationError("evidence.evidenceQuality", "Evidence quality must be between 0.0 and 1.0 (was ${bp.evidence.evidenceQuality})."))
        }

        if (bp.evidence.productionCount < 0) {
            errors.add(BlueprintValidationError("evidence.productionCount", "Production sample count cannot be negative."))
        }

        if (bp.evidence.experimentalCount < 0) {
            errors.add(BlueprintValidationError("evidence.experimentalCount", "Experimental sample count cannot be negative."))
        }

        if (bp.evidence.simulationCount < 0) {
            errors.add(BlueprintValidationError("evidence.simulationCount", "Simulation sample count cannot be negative."))
        }

        if (bp.baselineState.engagementScore !in 0.0..100.0) {
            errors.add(BlueprintValidationError("baselineState.engagementScore", "Baseline engagement score must be between 0.0 and 100.0 (was ${bp.baselineState.engagementScore})."))
        }

        // 4. Invalid Target Scores
        val targetVal = bp.targetState.targetValue
        if (targetVal != null) {
            if (targetVal !in 0.0..100.0) {
                errors.add(BlueprintValidationError("targetState.targetValue", "Target value must be between 0.0 and 100.0 (was $targetVal)."))
            }

            if (targetVal < bp.baselineState.engagementScore && bp.targetState.targetMetric.contains("Improvement", ignoreCase = true)) {
                warnings.add(BlueprintValidationWarning("targetState.targetValue", "Target value ($targetVal) is lower than baseline score (${bp.baselineState.engagementScore}) despite claiming improvement."))
            }
        }

        // 5. Inconsistent Evidence Checks
        val totalSamples = bp.evidence.productionCount + bp.evidence.experimentalCount + bp.evidence.simulationCount
        if (bp.evidence.evidenceQuality > 0.0 && totalSamples == 0) {
            errors.add(BlueprintValidationError("evidence", "Evidence quality is rated > 0.0 (${bp.evidence.evidenceQuality}) but total sample count across all tiers is 0."))
        }

        if (bp.evidence.productionCount == 0) {
            // Cannot have RECOMMENDED_CHANGE without production evidence
            val containsRecommendedChanges = bp.proposedModifications.any { it.modificationType == ModificationType.RECOMMENDED_CHANGE } ||
                    bp.recommendationEngineModifications.any { it.modificationType == ModificationType.RECOMMENDED_CHANGE } ||
                    bp.tasteDnaModifications.any { it.modificationType == ModificationType.RECOMMENDED_CHANGE }

            if (containsRecommendedChanges) {
                errors.add(BlueprintValidationError("proposedModifications", "Inconsistent evidence: Cannot specify RECOMMENDED_CHANGE modifications when production sample count is 0."))
            }

            if (bp.recommendationNotice.startsWith("PRODUCTION MODIFICATION RECOMMENDED")) {
                errors.add(BlueprintValidationError("recommendationNotice", "Inconsistent evidence: Notice claims production modification recommended, but production sample count is 0."))
            }
        }

        // 6. Unsupported Modifications
        bp.proposedModifications.forEachIndexed { idx, mod ->
            if (mod.component.isNotBlank() && !SUPPORTED_COMPONENTS.contains(mod.component)) {
                warnings.add(BlueprintValidationWarning("proposedModifications[$idx]", "Component '${mod.component}' is not in standard supported target list."))
            }
            if (mod.parameter.isBlank()) {
                errors.add(BlueprintValidationError("proposedModifications[$idx].parameter", "Modification parameter name cannot be blank."))
            }
            if (mod.confidence !in 0.0..1.0) {
                errors.add(BlueprintValidationError("proposedModifications[$idx].confidence", "Modification confidence must be between 0.0 and 1.0."))
            }
        }

        val isValid = errors.isEmpty()
        val validatedArtifact = if (isValid) {
            artifact.copy(
                lifecycleState = if (artifact.lifecycleState == BlueprintLifecycleState.LOADED) BlueprintLifecycleState.VALIDATED else artifact.lifecycleState
            )
        } else {
            null
        }

        return BlueprintValidationResult(
            isValid = isValid,
            errors = errors,
            warnings = warnings,
            validatedArtifact = validatedArtifact
        )
    }
}

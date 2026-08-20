package com.example.data.blueprint

import com.example.data.EvidenceTier
import com.example.data.OutcomeClassification
import com.example.data.StrategyValidationState
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * Classification of code-level changes proposed in an implementation manifest.
 */
enum class ImplementationChangeType {
    PARAMETER_CHANGE,
    WEIGHT_CHANGE,
    THRESHOLD_CHANGE,
    CALIBRATION_CHANGE,
    ALGORITHM_CHANGE,
    DATA_MODEL_CHANGE,
    UI_CHANGE,
    PERSISTENCE_CHANGE,
    INTEGRATION_CHANGE,
    TELEMETRY_CHANGE,
    TEST_ONLY
}

/**
 * Current status of a specific code modification or the manifest itself.
 */
enum class ImplementationStatus {
    PENDING,
    INSPECTING,
    READY_FOR_IMPLEMENTATION,
    REQUIRES_REVIEW,
    UNRESOLVED,
    IMPLEMENTED,
    TESTED,
    VALIDATED,
    ROLLED_BACK,
    REJECTED
}

/**
 * Formal approval state for the implementation manifest.
 */
enum class ImplementationApprovalState {
    NOT_REVIEWED,
    READY_FOR_REVIEW,
    APPROVED,
    REJECTED,
    IMPLEMENTING,
    COMPLETED
}

/**
 * Granular mapping of a Blueprint modification to actual Android source code.
 */
@JsonClass(generateAdapter = true)
data class CodeModificationPlan(
    @Json(name = "modification_id") val modificationId: String = UUID.randomUUID().toString(),
    @Json(name = "component_name") val componentName: String,
    @Json(name = "source_file") val sourceFile: String,
    @Json(name = "package_name") val packageName: String,
    @Json(name = "class_name") val className: String,
    @Json(name = "method_or_property") val methodOrProperty: String,
    @Json(name = "current_implementation_state") val currentImplementationState: String = "Hardcoded Heuristic",
    @Json(name = "current_value_blueprint") val blueprintExpectedValue: String,
    @Json(name = "current_value_actual") val actualRepositoryValue: String? = null,
    @Json(name = "proposed_value") val proposedValue: String,
    @Json(name = "change_type") val changeType: ImplementationChangeType,
    @Json(name = "semantics") val semantics: String = "",
    @Json(name = "expected_behavioral_effect") val expectedBehavioralEffect: String,
    @Json(name = "evidence_supporting_change") val evidenceSupportingChange: String,
    @Json(name = "evidence_tier") val evidenceTier: EvidenceTier,
    @Json(name = "confidence") val confidence: Double,
    @Json(name = "risk_level") val risk: String,
    @Json(name = "required_tests") val requiredTests: List<String> = emptyList(),
    @Json(name = "validation_criteria") val validationCriteria: String,
    @Json(name = "rollback_criteria") val rollbackCriteria: String,
    @Json(name = "rollback_procedure") val rollbackProcedure: String = "Revert to baseline hardcoded value.",
    @Json(name = "status") val implementationStatus: ImplementationStatus = ImplementationStatus.PENDING,
    @Json(name = "causally_validated") val isCausallyValidated: Boolean = false,
    @Json(name = "dependencies") val dependencies: List<String> = emptyList(),
    @Json(name = "files_to_modify") val filesToModify: List<String> = emptyList(),
    @Json(name = "files_not_to_modify") val filesNotToModify: List<String> = emptyList(),
    @Json(name = "repository_url") val repositoryUrl: String? = null,
    @Json(name = "branch") val branch: String? = null,
    @Json(name = "commit_hash") val commitHash: String? = null
)

/**
 * Outcomes for the Closed Loop feedback phase.
 */
enum class ClosedLoopOutcome {
    SUCCESS,
    PARTIAL_SUCCESS,
    INCONCLUSIVE,
    REGRESSION,
    FAILED
}

/**
 * Status of the implementation validation phase.
 */
enum class ValidationStatus {
    NOT_VALIDATED,
    IMPLEMENTATION_VALIDATED,
    IMPLEMENTATION_INCONCLUSIVE,
    IMPLEMENTATION_FAILED,
    REGRESSION_DETECTED
}

/**
 * Formal bridge between Strategy Blueprint intelligence and actual Android source code.
 * Provides a safe, auditable implementation plan for an AI coding agent or developer.
 */
@JsonClass(generateAdapter = true)
data class BlueprintImplementationManifest(
    @Json(name = "manifest_id") val manifestId: String = UUID.randomUUID().toString(),
    @Json(name = "blueprint_id") val blueprintId: String,
    @Json(name = "blueprint_version") val blueprintVersion: String,
    @Json(name = "generated_date") val generatedDate: Long = System.currentTimeMillis(),
    @Json(name = "strategy_name") val strategyName: String,
    @Json(name = "blueprint_validation_state") val blueprintValidationState: StrategyValidationState,
    @Json(name = "highest_evidence_tier") val evidenceTier: EvidenceTier,
    @Json(name = "production_sample_count") val productionSampleCount: Int,
    @Json(name = "confidence") val confidence: Double,
    @Json(name = "baseline_score") val baselineScore: Double,
    @Json(name = "measured_score") val measuredScore: Double,
    @Json(name = "target_score") val targetScore: Double,
    @Json(name = "outcome_classification") val outcomeClassification: OutcomeClassification,
    @Json(name = "observed_production_improvement") val observedProductionImprovement: Boolean,
    @Json(name = "manifest_status") val manifestStatus: ImplementationStatus = ImplementationStatus.PENDING,
    @Json(name = "approval_state") val approvalState: ImplementationApprovalState = ImplementationApprovalState.NOT_REVIEWED,
    
    // Validation Results
    @Json(name = "validation_status") val validationStatus: ValidationStatus = ValidationStatus.NOT_VALIDATED,
    @Json(name = "closed_loop_outcome") val closedLoopOutcome: ClosedLoopOutcome? = null,
    @Json(name = "causal_validation_summary") val causalValidationSummary: String = "Causal validation: Not yet established.",
    @Json(name = "post_implementation_score") val postImplementationScore: Double? = null,
    @Json(name = "post_implementation_samples") val postImplementationSamples: Int = 0,
    
    @Json(name = "code_modifications") val proposedModifications: List<CodeModificationPlan> = emptyList(),
    @Json(name = "implementation_order") val implementationOrder: List<String> = emptyList(),
    @Json(name = "causality_warning") val causalityWarning: String = "",
    @Json(name = "long_term_monitoring_requirements") val monitoringRequirements: List<String> = listOf(
        "Monitor for metric drift over 30-day window.",
        "Verify observed metric stability across application restarts.",
        "Check for regression in secondary engagement signals (e.g., skip velocity)."
    ),
    @Json(name = "audit_notes") val auditNotes: String = "",
    @Json(name = "repository_url") val repositoryUrl: String? = null,
    @Json(name = "branch") val branch: String? = null,
    @Json(name = "commit_hash") val commitHash: String? = null
)

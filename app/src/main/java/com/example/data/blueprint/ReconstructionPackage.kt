package com.example.data.blueprint

import com.example.data.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * High-fidelity Reconstruction Package for migrating or sharing 
 * complete Aura Intelligence lifecycle context across environments.
 */
@JsonClass(generateAdapter = true)
data class ReconstructionPackage(
    @Json(name = "schema_version") val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    @Json(name = "package_id") val packageId: String = UUID.randomUUID().toString(),
    @Json(name = "export_timestamp") val exportTimestamp: Long = System.currentTimeMillis(),
    @Json(name = "package_type") val packageType: String = "RECONSTRUCTION_PACKAGE",
    
    // Core Identity
    @Json(name = "improvement_id") val improvementId: String,
    @Json(name = "finding_id") val findingId: String,
    
    // Authoritative technical artifacts
    @Json(name = "blueprint_artifact") val blueprintArtifact: BlueprintArtifact,
    @Json(name = "finding") val finding: Finding? = null,
    
    // Historical snapshots & references
    @Json(name = "evidence_references") val evidenceReferences: List<String> = emptyList(),
    @Json(name = "evidence_snapshot") val evidenceSnapshot: EvidenceSnapshot? = null,
    
    // Lifecycle metadata
    @Json(name = "approval_state") val approvalState: String? = null,
    @Json(name = "implementation_records") val implementationRuns: List<ImplementationRun> = emptyList(),
    @Json(name = "verification_results") val verificationResults: List<VerificationResult> = emptyList(),
    @Json(name = "monitoring_configuration") val monitoringSessions: List<MonitoringSession> = emptyList(),
    @Json(name = "validation_results") val validationResults: List<ValidationResult> = emptyList(),
    
    // Diagnostic context
    @Json(name = "system_analysis_summary") val systemAnalysis: SystemAnalysisSummary? = null,
    
    // Recovery extensions
    @Json(name = "regression_records") val regressionRecords: List<RegressionAlert> = emptyList(),
    @Json(name = "rollback_records") val rollbackRecords: List<RollbackRun> = emptyList(),
    @Json(name = "attention_items") val attentionItems: List<AttentionItem> = emptyList(),
    @Json(name = "report_snapshots") val reportSnapshots: List<MasterIntelligenceReport> = emptyList(),

    @Json(name = "checksum") val checksum: String? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = "1.0.0"
    }
}

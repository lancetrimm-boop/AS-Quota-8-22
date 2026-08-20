package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.*

@Entity(tableName = "intelligence_findings")
data class FindingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val classification: FindingClassification,
    val confidence: ConfidenceLevel,
    val dateDiscovered: Long,
    val technicalDetailsJson: String, // Moshi serialized StrategyBlueprint
    val lifecycleState: IntelligenceLifecycleState
)

@Entity(tableName = "suggested_improvements")
data class SuggestedImprovementEntity(
    @PrimaryKey val id: String,
    val findingId: String,
    val title: String,
    val summary: String,
    val priority: String,
    val expectedImpact: String,
    val risk: String,
    val confidence: ConfidenceLevel,
    val evidenceCount: Int,
    val source: String,
    val classification: FindingClassification,
    val rationale: String,
    val whatWillChange: String,
    val whatWillNotChange: String,
    val proposedChangesJson: String, // Moshi serialized modifications/deltas
    val implementationPlanJson: String, // Moshi serialized ImplementationPlan
    val verificationPlanJson: String, // Moshi serialized VerificationPlan
    val rollbackPlanJson: String, // Moshi serialized RollbackPlan
    val technicalDetailsJson: String? = null, // Moshi serialized StrategyBlueprint
    val blueprintArtifactId: String?,
    val status: IntelligenceLifecycleState,
    val version: Int = 1,
    val createdAt: Long
)

@Entity(tableName = "intelligence_lifecycle_events")
data class LifecycleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetId: String, // FindingId or ImprovementId
    val fromState: IntelligenceLifecycleState?,
    val toState: IntelligenceLifecycleState,
    val timestamp: Long = System.currentTimeMillis(),
    val actor: String = "Aura Engine",
    val reason: String? = null,
    val metadataJson: String? = null
)

@Entity(tableName = "regression_alerts")
data class RegressionAlertEntity(
    @PrimaryKey val id: String,
    val improvementId: String,
    val runId: String,
    val artifactId: String,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: RegressionSeverity,
    val affectedMetric: String,
    val baselineValue: Double,
    val preRegressionValue: Double,
    val currentResult: Double,
    val change: Double,
    val evidenceIdsJson: String,
    val confidence: Double,
    val status: RegressionAlertStatus,
    val recommendation: String
)

@Entity(tableName = "rollback_runs")
data class RollbackRunEntity(
    @PrimaryKey val id: String,
    val improvementId: String,
    val regressionId: String,
    val originalRunId: String,
    val artifactId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: IntelligenceActionStatus,
    val notes: String? = null,
    val changedFilesJson: String? = null,
    val deviationDetected: Boolean = false,
    val resultSummary: String? = null
)

@Entity(tableName = "blueprint_artifacts")
data class BlueprintArtifactEntity(
    @PrimaryKey val id: String, // Internal artifact entry ID
    val blueprintId: String, // Stable strategy ID
    val improvementId: String, // Link to originating improvement
    val proposalVersion: Int, // Link to approved proposal version
    val version: String,
    val dataJson: String, // Serialized BlueprintArtifact
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "intelligence_actions")
data class IntelligenceActionEntity(
    @PrimaryKey val id: String,
    val improvementId: String,
    val type: IntelligenceActionType,
    val status: IntelligenceActionStatus,
    val planJson: String,
    val resultJson: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "implementation_runs")
data class ImplementationRunEntity(
    @PrimaryKey val id: String,
    val improvementId: String,
    val artifactId: String,
    val proposalVersion: Int,
    val startTime: Long,
    val endTime: Long? = null,
    val status: IntelligenceActionStatus,
    val notes: String? = null,
    val changedFilesJson: String? = null,
    val deviationDetected: Boolean = false,
    val deviationDetails: String? = null,
    val resultSummary: String? = null
)

@Entity(tableName = "verification_results")
data class VerificationResultEntity(
    @PrimaryKey val id: String,
    val improvementId: String,
    val runId: String,
    val artifactId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val buildPassed: Boolean,
    val testsPassed: Boolean,
    val regressionPassed: Boolean,
    val dbIntegrityPassed: Boolean,
    val scopeCompliant: Boolean,
    val acceptanceCriteriaResultsJson: String,
    val technicalDetailsJson: String? = null,
    val overallPassed: Boolean
)

@Entity(tableName = "monitoring_sessions")
data class MonitoringSessionEntity(
    @PrimaryKey val id: String,
    val improvementId: String,
    val runId: String,
    val artifactId: String,
    val startTime: Long,
    val status: MonitoringStatus,
    val baselineMetricsJson: String,
    val currentMetricsJson: String,
    val requiredSampleCount: Int,
    val currentSampleCount: Int,
    val durationDays: Int,
    val regressionDetected: Boolean,
    val confidence: Double,
    val validationOutcome: IntelligenceLifecycleState? = null,
    val evidenceIdsJson: String
)

@Entity(tableName = "validation_results_history")
data class ValidationResultEntity(
    @PrimaryKey val id: String,
    val improvementId: String,
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: IntelligenceLifecycleState,
    val evidenceSummary: String,
    val baselineValue: Double,
    val finalValue: Double,
    val change: Double,
    val sampleCount: Int,
    val confidence: Double,
    val regressionSeverity: String? = null,
    val metadataJson: String? = null
)

@Entity(tableName = "review_metadata")
data class ReviewMetadataEntity(
    @PrimaryKey val targetId: String, // FindingId or ImprovementId
    val firstSeenTimestamp: Long? = null,
    val lastSeenTimestamp: Long? = null,
    val reviewedTimestamp: Long? = null,
    val status: ReviewStatus = ReviewStatus.UNREAD
)

@Entity(tableName = "user_checkpoints")
data class UserCheckpointEntity(
    @PrimaryKey val checkpointId: String, // e.g., "LAST_REVIEW"
    val timestamp: Long
)

@Entity(tableName = "intelligence_events")
data class IntelligenceEventEntity(
    @PrimaryKey val id: String,
    val type: String, // EVIDENCE_AVAILABLE, FINDING_CREATED, etc.
    val sourceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // PENDING, COMPLETED, FAILED
    val failureReason: String? = null,
    val retryCount: Int = 0
)

@Entity(tableName = "attention_items")
data class AttentionItemEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val sourceId: String,
    val attentionType: AttentionType,
    val priority: DecisionPriority,
    val title: String,
    val summary: String,
    val whyItMatters: String,
    val status: AttentionStatus,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val requiresAction: Boolean,
    val deepLink: String,
    val deduplicationKey: String
)

@Entity(tableName = "integrity_audit_history")
data class IntegrityAuditEntity(
    @PrimaryKey val id: String,
    val targetId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: IntegrityStatus,
    val scope: String,
    val issuesJson: String, // Moshi serialized List<IntegrityIssue>
    val recommendedAction: String?
)

@Entity(tableName = "evidence_snapshots")
data class EvidenceSnapshotEntity(
    @PrimaryKey val improvementId: String,
    val blueprintId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reportJson: String // Moshi serialized ClosedLoopReport
)

@Entity(tableName = "saved_intelligence_reports")
data class SavedIntelligenceReportEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val dataThrough: Long,
    val reportingPeriodDays: Int,
    val reportJson: String, // Moshi serialized MasterIntelligenceReport
    val isSnapshot: Boolean = true
)

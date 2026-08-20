package com.example.data

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class Finding(
    val id: String,
    val title: String,
    val summary: String,
    val classification: FindingClassification,
    val confidence: ConfidenceLevel,
    val dateDiscovered: Long,
    val technicalDetails: StrategyBlueprint,
    val lifecycleState: IntelligenceLifecycleState
)

@JsonClass(generateAdapter = true)
data class SuggestedImprovement(
    val id: String,
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
    val proposedChanges: ProposedChanges,
    val implementationPlan: ImplementationPlan,
    val verificationPlan: VerificationPlan,
    val rollbackPlan: RollbackPlan,
    val technicalDetails: StrategyBlueprint? = null,
    val blueprintArtifactId: String?,
    val status: IntelligenceLifecycleState,
    val version: Int = 1,
    val createdAt: Long
)

@JsonClass(generateAdapter = true)
data class ProposedChanges(
    val modifications: List<ProposedModification>
)

@JsonClass(generateAdapter = true)
data class ImplementationPlan(
    val steps: List<ActionStep>,
    val targetComponents: List<String>
)

@JsonClass(generateAdapter = true)
data class VerificationPlan(
    val criteria: List<String>,
    val testCases: List<String>
)

@JsonClass(generateAdapter = true)
data class RollbackPlan(
    val procedure: String,
    val criteria: String
)

@JsonClass(generateAdapter = true)
data class LifecycleEvent(
    val id: Long,
    val targetId: String,
    val fromState: IntelligenceLifecycleState?,
    val toState: IntelligenceLifecycleState,
    val timestamp: Long,
    val actor: String,
    val reason: String?,
    val metadata: Map<String, String>?
)

@JsonClass(generateAdapter = true)
data class IntelligenceAction(
    val id: String,
    val improvementId: String,
    val type: IntelligenceActionType,
    val status: IntelligenceActionStatus,
    val plan: ImplementationPlan,
    val result: String?,
    val updatedAt: Long
)

@JsonClass(generateAdapter = true)
data class IntelligenceMetrics(
    val newFindings: Int = 0,
    val needsReview: Int = 0,
    val approved: Int = 0,
    val implementing: Int = 0,
    val monitoring: Int = 0,
    val validated: Int = 0,
    val activeRegressions: Int = 0
)

@JsonClass(generateAdapter = true)
data class IntelligenceChange(
    val title: String,
    val description: String,
    val timestamp: Long,
    val type: String, // e.g., "Finding", "Improvement"
    val state: IntelligenceLifecycleState,
    val targetId: String
)

@JsonClass(generateAdapter = true)
data class SystemAnalysisSummary(
    val summary: String,
    val whatsWorking: List<String>,
    val whatsNotWorking: List<String>,
    val actionRequired: Boolean,
    val recommendation: String,
    val latestFinding: Finding? = null,
    val evidenceSummary: EvidenceSummary? = null,
    val confidenceExplanation: String = "",
    val actionStatus: ActionStatus = ActionStatus.NO_ACTION_REQUIRED
)

@JsonClass(generateAdapter = true)
data class EvidenceSummary(
    val sampleCount: Int,
    val baseline: Double,
    val currentResult: Double,
    val change: Double,
    val evidenceAge: String,
    val confidence: ConfidenceLevel,
    val hasRegression: Boolean
)

enum class ActionStatus {
    ACTION_REQUIRED,
    REVIEW_RECOMMENDED,
    CONTINUE_MONITORING,
    MORE_EVIDENCE_NEEDED,
    NO_ACTION_REQUIRED
}

@JsonClass(generateAdapter = true)
data class ImplementationRun(
    val id: String,
    val improvementId: String,
    val artifactId: String,
    val proposalVersion: Int,
    val startTime: Long,
    val endTime: Long?,
    val status: IntelligenceActionStatus,
    val notes: String?,
    val changedFiles: List<String>,
    val deviationDetected: Boolean,
    val deviationDetails: String?,
    val resultSummary: String?
)

@JsonClass(generateAdapter = true)
data class VerificationResult(
    val id: String,
    val improvementId: String,
    val runId: String,
    val artifactId: String,
    val timestamp: Long,
    val buildPassed: Boolean,
    val testsPassed: Boolean,
    val regressionPassed: Boolean,
    val dbIntegrityPassed: Boolean,
    val scopeCompliant: Boolean,
    val acceptanceCriteriaResults: Map<String, String>, // Criterion -> Status (Passed, Failed, etc.)
    val technicalDetails: String?,
    val overallPassed: Boolean
)

@JsonClass(generateAdapter = true)
data class MonitoringSession(
    val id: String,
    val improvementId: String,
    val runId: String,
    val artifactId: String,
    val startTime: Long,
    val status: MonitoringStatus,
    val baselineMetrics: Map<String, Double>,
    val currentMetrics: Map<String, Double>,
    val requiredSampleCount: Int,
    val currentSampleCount: Int,
    val durationDays: Int,
    val regressionDetected: Boolean,
    val confidence: Double,
    val validationOutcome: IntelligenceLifecycleState? = null,
    val evidenceIds: List<String> = emptyList()
)

enum class MonitoringStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    TERMINATED
}

@JsonClass(generateAdapter = true)
data class ValidationResult(
    val id: String,
    val improvementId: String,
    val sessionId: String,
    val timestamp: Long,
    val outcome: IntelligenceLifecycleState, // VALIDATED, INCONCLUSIVE, REGRESSION_DETECTED
    val evidenceSummary: String,
    val baselineValue: Double,
    val finalValue: Double,
    val change: Double,
    val sampleCount: Int,
    val confidence: Double,
    val regressionSeverity: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class RegressionAlert(
    val id: String,
    val improvementId: String,
    val runId: String,
    val artifactId: String,
    val sessionId: String,
    val timestamp: Long,
    val severity: RegressionSeverity,
    val affectedMetric: String,
    val baselineValue: Double,
    val preRegressionValue: Double,
    val currentResult: Double,
    val change: Double,
    val evidenceIds: List<String>,
    val confidence: Double,
    val status: RegressionAlertStatus,
    val recommendation: String
)

enum class RegressionSeverity {
    LOW, MEDIUM, HIGH, CRITICAL, UNDETERMINED
}

enum class RegressionAlertStatus {
    ACTIVE, INVESTIGATING, MONITORING, FALSE_POSITIVE, RESOLVED, PERSISTING
}

@JsonClass(generateAdapter = true)
data class RollbackRun(
    val id: String,
    val improvementId: String,
    val regressionId: String,
    val originalRunId: String,
    val artifactId: String,
    val startTime: Long,
    val endTime: Long?,
    val status: IntelligenceActionStatus,
    val notes: String?,
    val changedFiles: List<String>,
    val deviationDetected: Boolean,
    val resultSummary: String?
)

/**
 * Master Intelligence Report - Top-level executive summary.
 */
@JsonClass(generateAdapter = true)
data class MasterIntelligenceReport(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val dataThrough: Long,
    val executiveSummary: ExecutiveSummary,
    val sinceLastReview: ReviewUpdate,
    val systemAnalysis: SystemAnalysisSummary,
    val productIntelligence: DomainIntelligence,
    val engagement: DomainIntelligence,
    val personalization: PersonalizationIntelligence,
    val retention: DomainIntelligence,
    val monetization: DomainIntelligence,
    val technicalHealth: TechnicalHealthIntelligence,
    val improvementPipeline: List<SuggestedImprovement>,
    val implementationOverview: List<ImplementationRun>,
    val risksAndRegressions: List<RegressionAlert>,
    val recentlyValidated: List<ValidationResult>,
    val discovery: DomainIntelligence? = null,
    val openQuestions: List<String> = emptyList(),
    val recommendedAreasToWatch: List<String> = emptyList(),
    val isSnapshot: Boolean = false,
    val packageType: String = "MASTER_INTELLIGENCE_REPORT",
    val reportingPeriodDays: Int = 30
)

@JsonClass(generateAdapter = true)
data class ExecutiveSummary(
    val systemHealth: String,
    val plainEnglishSummary: String,
    val metrics: IntelligenceMetrics
)

@JsonClass(generateAdapter = true)
data class ReviewUpdate(
    val newFindings: Int,
    val newImprovements: Int,
    val newApprovals: Int,
    val newValidations: Int,
    val newRegressions: Int,
    val items: List<IntelligenceChange>
)

@JsonClass(generateAdapter = true)
data class DomainIntelligence(
    val domainName: String,
    val status: String, // Healthy, Needs Attention, etc.
    val whatsWorking: List<String>,
    val whatsNotWorking: List<String>,
    val importantChanges: List<String>,
    val recommendedActions: List<String>
)

@JsonClass(generateAdapter = true)
data class PersonalizationIntelligence(
    val performanceSummary: String,
    val baselineScore: Double,
    val currentScore: Double,
    val change: Double,
    val sampleCount: Int,
    val confidence: Double,
    val regressionStatus: String
)

@JsonClass(generateAdapter = true)
data class TechnicalHealthIntelligence(
    val overallStatus: String,
    val crashRate: Double,
    val errorCount: Int,
    val startupTimeMs: Long,
    val dbHealth: String,
    val performanceRegressions: Int
)

@JsonClass(generateAdapter = true)
data class ReviewMetadata(
    val targetId: String,
    val firstSeenTimestamp: Long?,
    val lastSeenTimestamp: Long?,
    val reviewedTimestamp: Long?,
    val status: ReviewStatus
)

@JsonClass(generateAdapter = true)
data class DecisionCenterState(
    val attentionItems: List<DecisionActionItem>,
    val criticalIssues: List<DecisionActionItem>,
    val awaitingDecision: List<SuggestedImprovement>,
    val inProgress: List<InWorkImprovement>,
    val recentlyCompleted: List<IntelligenceChange>,
    val whatChanged: List<IntelligenceChange>,
    val lastReviewedAt: Long
)

@JsonClass(generateAdapter = true)
data class DecisionActionItem(
    val id: String,
    val title: String,
    val description: String,
    val reason: String,
    val priority: DecisionPriority,
    val timestamp: Long,
    val targetId: String, // ImprovementId or FindingId
    val primaryAction: String,
    val reviewStatus: ReviewStatus
)

enum class DecisionPriority {
    CRITICAL, HIGH, MEDIUM, LOW
}

@JsonClass(generateAdapter = true)
data class InWorkImprovement(
    val id: String,
    val title: String,
    val status: IntelligenceLifecycleState,
    val progressDescription: String,
    val currentEvidence: String? = null
)

@JsonClass(generateAdapter = true)
data class ImplementationPackage(
    val improvementId: String,
    val findingId: String,
    val blueprintArtifactId: String,
    val blueprintVersion: String,
    val contractId: String,
    val title: String,
    val summary: String,
    val technicalObjective: String,
    val approvedScope: List<String>,
    val outOfScope: List<String> = listOf("Non-targeted components", "Database schema unless specified", "UI theme"),
    val whyApproved: String,
    val evidenceSummary: String,
    val expectedImpact: String,
    val risk: String,
    val filesAffected: List<String>,
    val classesAffected: List<String>,
    val functionsAffected: List<String>,
    val dataModelChanges: String? = null,
    val implementationSteps: List<String>,
    val acceptanceCriteria: List<String>,
    val verificationTests: List<String>,
    val rollbackPlan: String,
    val androidStudioPrompt: String? = null,
    val approvalTimestamp: Long
)

@JsonClass(generateAdapter = true)
data class IntegrityAuditResult(
    val id: String = UUID.randomUUID().toString(),
    val targetId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: IntegrityStatus,
    val scope: String, // Finding, Improvement, Full Chain
    val issues: List<IntegrityIssue>,
    val recommendedAction: String?
)

enum class IntegrityStatus {
    PASS, WARNING, FAIL, REVIEW_REQUIRED
}

@JsonClass(generateAdapter = true)
data class IntegrityIssue(
    val severity: IntegritySeverity,
    val code: String,
    val message: String
)

enum class IntegritySeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

@JsonClass(generateAdapter = true)
data class EvidenceSnapshot(
    val improvementId: String,
    val blueprintId: String,
    val timestamp: Long,
    val report: ClosedLoopReport
)

sealed class ImportResult {
    data class Success(val improvementId: String, val isIdempotent: Boolean = false) : ImportResult()
    data class Conflict(val targetId: String, val message: String) : ImportResult()
    data class Failure(val errorMessage: String) : ImportResult()
}

/**
 * Unified model for Aura Intelligence attention items.
 */
@JsonClass(generateAdapter = true)
data class AttentionItem(
    val id: String,
    val sourceType: String, // Finding, Improvement, Regression, Implementation
    val sourceId: String,
    val attentionType: AttentionType,
    val priority: DecisionPriority,
    val title: String,
    val summary: String,
    val whyItMatters: String,
    val status: AttentionStatus,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val requiresAction: Boolean,
    val deepLink: String,
    val deduplicationKey: String
)

enum class AttentionType {
    DECISION_REQUIRED,
    REVIEW_RECOMMENDED,
    INTEGRITY_WARNING,
    EXECUTION_FAILURE,
    REGRESSION_DETECTED,
    VALIDATION_COMPLETE,
    INFORMATIONAL
}

enum class AttentionStatus {
    NEW, SEEN, REVIEWED, DISMISSED, RESOLVED, ARCHIVED
}

/**
 * Unified model for Aura Intelligence explanations.
 */
@JsonClass(generateAdapter = true)
data class IntelligenceExplanation(
    val explanationId: String = UUID.randomUUID().toString(),
    val sourceType: String, // Finding, Improvement, Evidence, etc.
    val sourceId: String,
    val summary: String,
    val reasoning: List<String>,
    val evidenceStrength: String? = null,
    val confidence: ConfidenceLevel? = null,
    val limitations: List<String> = emptyList(),
    val whatWillChange: List<String> = emptyList(),
    val whatWillNotChange: List<String> = emptyList(),
    val riskAssessment: String? = null,
    val generatedAt: Long = System.currentTimeMillis()
)

/**
 * Result of comparing a technical BlueprintArtifact against its approved SuggestedImprovement.
 */
sealed class ScopeValidationResult {
    object Valid : ScopeValidationResult()
    data class Mismatch(val reason: String) : ScopeValidationResult()
    data class Incomplete(val missingFields: List<String>) : ScopeValidationResult()
}

package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.db.IntelligenceEventEntity
import com.example.data.blueprint.BlueprintSerializer
import com.example.data.blueprint.BlueprintArtifact
import com.example.data.blueprint.ReconstructionPackage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * State representing the Aura Intelligence Control Center.
 */
data class IntelligenceCenterState(
    val findings: List<Finding> = emptyList(),
    val improvements: List<SuggestedImprovement> = emptyList(),
    val summaryMetrics: IntelligenceMetrics = IntelligenceMetrics(),
    val recentChanges: List<IntelligenceChange> = emptyList(),
    val systemAnalysis: SystemAnalysisSummary? = null,
    val masterReport: MasterIntelligenceReport? = null,
    val decisionCenter: DecisionCenterState? = null,
    val isLoading: Boolean = true,
    val filter: IntelligenceFilter = IntelligenceMetricsFilter.ALL
)

sealed interface ImprovementDetailState {
    object Idle : ImprovementDetailState
    object Loading : ImprovementDetailState
    data class Success(
        val improvement: SuggestedImprovement, 
        val history: List<LifecycleEvent>,
        val artifact: com.example.data.blueprint.BlueprintArtifact? = null,
        val validation: ScopeValidationResult = ScopeValidationResult.Valid,
        val runs: List<ImplementationRun> = emptyList(),
        val verificationResults: List<VerificationResult> = emptyList(),
        val monitoringSessions: List<MonitoringSession> = emptyList(),
        val validationResults: List<ValidationResult> = emptyList(),
        val regressionAlerts: List<RegressionAlert> = emptyList(),
        val rollbackRuns: List<RollbackRun> = emptyList(),
        val implementationPackage: ImplementationPackage? = null,
        val auditHistory: List<IntegrityAuditResult> = emptyList(),
        val evidenceSnapshot: EvidenceSnapshot? = null
    ) : ImprovementDetailState
    data class Error(val message: String) : ImprovementDetailState
}

sealed interface IntelligenceFilter
enum class IntelligenceMetricsFilter : IntelligenceFilter {
    ALL, NEW, NEEDS_REVIEW, APPROVED, IMPLEMENTING, MONITORING, VALIDATED, REJECTED, REGRESSIONS
}

class IntelligenceViewModel(
    private val repository: IntelligenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(IntelligenceCenterState())
    val state: StateFlow<IntelligenceCenterState> = _state.asStateFlow()

    private val _events = MutableStateFlow<List<IntelligenceEventEntity>>(emptyList())
    val events: StateFlow<List<IntelligenceEventEntity>> = _events.asStateFlow()

    private val _attentionInbox = MutableStateFlow<List<AttentionItem>>(emptyList())
    val attentionInbox: StateFlow<List<AttentionItem>> = _attentionInbox.asStateFlow()

    private val _detailState = MutableStateFlow<ImprovementDetailState>(ImprovementDetailState.Idle)
    val detailState: StateFlow<ImprovementDetailState> = _detailState.asStateFlow()

    private val _explanation = MutableStateFlow<IntelligenceExplanation?>(null)
    val explanation: StateFlow<IntelligenceExplanation?> = _explanation.asStateFlow()

    fun loadImprovement(id: String) {
        viewModelScope.launch {
            _detailState.value = ImprovementDetailState.Loading
            repository.markAsReviewed(id)
            
            combine(
                listOf(
                    repository.getAllImprovements().map { improvements -> improvements.find { it.id == id } },
                    repository.getArtifactsForImprovement(id),
                    repository.getLifecycleHistory(id),
                    repository.getImplementationRuns(id),
                    repository.getVerificationResults(id),
                    repository.getMonitoringSessions(id),
                    repository.getValidationResults(id),
                    repository.getRegressionAlerts(id),
                    repository.getRollbackRuns(id),
                    repository.getAuditHistory(id)
                )
            ) { array ->
                @Suppress("UNCHECKED_CAST")
                val improvement = array[0] as? SuggestedImprovement
                @Suppress("UNCHECKED_CAST")
                val artifacts = array[1] as List<com.example.data.blueprint.BlueprintArtifact>
                @Suppress("UNCHECKED_CAST")
                val history = array[2] as List<LifecycleEvent>
                @Suppress("UNCHECKED_CAST")
                val runs = array[3] as List<ImplementationRun>
                @Suppress("UNCHECKED_CAST")
                val verResults = array[4] as List<VerificationResult>
                @Suppress("UNCHECKED_CAST")
                val monSessions = array[5] as List<MonitoringSession>
                @Suppress("UNCHECKED_CAST")
                val valResults = array[6] as List<ValidationResult>
                @Suppress("UNCHECKED_CAST")
                val regAlerts = array[7] as List<RegressionAlert>
                @Suppress("UNCHECKED_CAST")
                val rollRuns = array[8] as List<RollbackRun>
                @Suppress("UNCHECKED_CAST")
                val audits = array[9] as List<IntegrityAuditResult>

                if (improvement != null) {
                    val artifact = artifacts.firstOrNull()
                    val validation = if (artifact != null) {
                        repository.validateScope(improvement, artifact)
                    } else ScopeValidationResult.Valid
                    
                    ImprovementDetailState.Success(improvement, history, artifact, validation, runs, verResults, monSessions, valResults, regAlerts, rollRuns, null, audits, null)
                } else {
                    ImprovementDetailState.Error("Improvement not found")
                }
            }.collect { newState ->
                _detailState.value = newState
                if (newState is ImprovementDetailState.Success) {
                    loadImplementationPackage(id)
                    loadEvidenceSnapshot(id)
                }
            }
        }
    }

    private fun loadImplementationPackage(id: String) {
        viewModelScope.launch {
            try {
                val pkg = repository.getImplementationPackage(id)
                val current = _detailState.value as? ImprovementDetailState.Success
                if (current != null) {
                    _detailState.value = current.copy(implementationPackage = pkg)
                }
            } catch (e: Exception) {
                // Ignore if not yet approved
            }
        }
    }

    private fun loadEvidenceSnapshot(id: String) {
        viewModelScope.launch {
            val snapshot = repository.getEvidenceSnapshot(id)
            val current = _detailState.value as? ImprovementDetailState.Success
            if (current != null && snapshot != null) {
                _detailState.value = current.copy(evidenceSnapshot = snapshot)
            }
        }
    }

    fun approveImprovement(id: String, reason: String? = null) {
        viewModelScope.launch {
            val current = (_detailState.value as? ImprovementDetailState.Success)?.improvement ?: return@launch
            
            // Generate valid BlueprintArtifact for implementation
            val technical = current.technicalDetails ?: return@launch
            val artifact = com.example.data.blueprint.BlueprintArtifact(
                blueprintId = technical.id,
                strategyBlueprint = technical
            )
            repository.approveImprovement(id, artifact, reason)
        }
    }

    fun rejectImprovement(id: String, reason: String) {
        viewModelScope.launch {
            repository.rejectImprovement(id, reason)
        }
    }

    fun planImplementation(id: String) {
        viewModelScope.launch {
            try {
                repository.planImplementation(id)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateBlueprintArtifact(improvementId: String, artifact: BlueprintArtifact) {
        viewModelScope.launch {
            try {
                repository.updateBlueprintArtifact(improvementId, artifact)
            } catch (e: Exception) {
                // Surface error to UI if needed
            }
        }
    }

    fun startImplementation(runId: String) {
        viewModelScope.launch {
            repository.startImplementation(runId)
        }
    }

    fun retryImplementation(improvementId: String) {
        viewModelScope.launch {
            repository.retryImplementation(improvementId)
        }
    }

    fun resolveDeviation(improvementId: String, approveNewScope: Boolean) {
        viewModelScope.launch {
            repository.resolveDeviation(improvementId, approveNewScope)
        }
    }

    fun completeImplementation(runId: String, notes: String? = null, changedFiles: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.completeImplementation(runId, notes, changedFiles)
        }
    }

    fun startVerification(id: String, runId: String) {
        viewModelScope.launch {
            repository.startVerification(id, runId)
        }
    }

    fun recordVerificationResult(
        improvementId: String,
        runId: String,
        buildPassed: Boolean,
        testsPassed: Boolean,
        regressionPassed: Boolean,
        dbIntegrityPassed: Boolean,
        scopeCompliant: Boolean,
        acceptanceCriteriaResults: Map<String, String>,
        technicalDetails: String? = null
    ) {
        viewModelScope.launch {
            repository.recordVerificationResult(
                improvementId,
                runId,
                buildPassed,
                testsPassed,
                regressionPassed,
                dbIntegrityPassed,
                scopeCompliant,
                acceptanceCriteriaResults,
                technicalDetails
            )
        }
    }

    fun updateMonitoring(sessionId: String, samples: Int, value: Double) {
        viewModelScope.launch {
            repository.updateMonitoringProgress(sessionId, samples, value, emptyList())
        }
    }

    fun investigateRegression(alertId: String) {
        viewModelScope.launch {
            repository.investigateRegression(alertId)
        }
    }

    fun markRegressionFalsePositive(alertId: String, reason: String) {
        viewModelScope.launch {
            repository.markRegressionFalsePositive(alertId, reason)
        }
    }

    fun approveRollback(alertId: String) {
        viewModelScope.launch {
            repository.approveRollback(alertId)
        }
    }

    fun executeRollback(runId: String) {
        viewModelScope.launch {
            repository.executeRollback(runId)
        }
    }

    fun createCorrectiveImprovement(alertId: String) {
        viewModelScope.launch {
            val successState = _detailState.value as? ImprovementDetailState.Success ?: return@launch
            val artifact = successState.artifact ?: return@launch
            repository.createCorrectiveImprovement(alertId, artifact.strategyBlueprint)
        }
    }

    fun runIntegrityAudit(id: String, scope: String) {
        viewModelScope.launch {
            repository.performIntegrityAudit(id, scope)
        }
    }

    fun exportBlueprint(context: android.content.Context, id: String) {
        viewModelScope.launch {
            val success = _detailState.value as? ImprovementDetailState.Success ?: return@launch
            val artifact = success.artifact ?: return@launch
            val file = BlueprintSerializer.exportToFile(context, artifact)
            shareFile(context, file)
        }
    }

    fun exportReconstructionPackage(context: android.content.Context, id: String) {
        viewModelScope.launch {
            val pkg = repository.generateReconstructionPackage(id)
            val file = java.io.File(context.filesDir, "exports/recon_${id.take(8)}_${System.currentTimeMillis()}.json")
            file.parentFile?.mkdirs()
            file.writeText(com.example.util.IntelligenceExporter.exportReconstructionPackage(pkg))
            shareFile(context, file)
        }
    }

    fun exportImprovementPackage(context: android.content.Context, id: String) {
        viewModelScope.launch {
            val success = _detailState.value as? ImprovementDetailState.Success ?: return@launch
            val finding = repository.getAllFindings().first().find { it.id == success.improvement.findingId }
            val json = com.example.util.IntelligenceExporter.exportImprovementPackage(
                success.improvement,
                finding,
                success.artifact,
                explanation.value
            )
            val file = java.io.File(context.filesDir, "exports/improvement_${id.take(8)}_${System.currentTimeMillis()}.json")
            file.parentFile?.mkdirs()
            file.writeText(json)
            shareFile(context, file)
        }
    }

    fun exportMasterReport(context: android.content.Context, report: MasterIntelligenceReport) {
        viewModelScope.launch {
            val file = java.io.File(context.filesDir, "exports/master_report_${report.id.take(8)}_${System.currentTimeMillis()}.json")
            file.parentFile?.mkdirs()
            file.writeText(com.example.util.IntelligenceExporter.exportMasterReportToJson(report))
            shareFile(context, file)
        }
    }

    private fun shareFile(context: android.content.Context, file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share Intelligence"))
    }

    fun requestMoreInfo(id: String, reason: String) {
        viewModelScope.launch {
            repository.transitionImprovementState(id, IntelligenceLifecycleState.NEEDS_MORE_INFORMATION, reason)
        }
    }

    fun importPackage(pkg: ReconstructionPackage) {
        viewModelScope.launch {
            val result = repository.importReconstructionPackage(pkg)
            when (result) {
                is ImportResult.Success -> {
                    _explanation.value = IntelligenceExplanation(
                        sourceType = "Import",
                        sourceId = result.improvementId,
                        summary = "Successfully imported improvement ${result.improvementId}${if (result.isIdempotent) " (Idempotent)" else "."}",
                        reasoning = listOf("The package data was validated and successfully reconstructed in the local database.")
                    )
                    refreshData()
                }
                is ImportResult.Conflict -> {
                    _explanation.value = IntelligenceExplanation(
                        sourceType = "Import",
                        sourceId = result.targetId,
                        summary = "Import Conflict",
                        reasoning = listOf(result.message)
                    )
                }
                is ImportResult.Failure -> {
                    _explanation.value = IntelligenceExplanation(
                        sourceType = "Import",
                        sourceId = "N/A",
                        summary = "Import Failed",
                        reasoning = listOf(result.errorMessage)
                    )
                }
            }
        }
    }

    fun askAuraToExplain(improvement: SuggestedImprovement) {
        viewModelScope.launch {
            val history = repository.getLifecycleHistory(improvement.id).first()
            val report = improvement.technicalDetails?.closedLoopReport
            val reasoning = mutableListOf<String>()
            val limitations = mutableListOf<String>()
            val sourceType: String
            
            when (improvement.status) {
                IntelligenceLifecycleState.REGRESSION_DETECTED -> {
                    sourceType = "Regression"
                    reasoning.add("Aura detected a performance drop after this improvement was implemented.")
                    if (report != null) {
                        reasoning.add("Current production telemetry shows a result of ${"%.2f".format(report.measuredScore)}, which is below the established baseline of ${"%.2f".format(report.baselineScore)}.")
                    }
                    reasoning.add("Aura recommends investigating the affected parameters or initiating a rollback to restore system stability.")
                }
                IntelligenceLifecycleState.VALIDATED -> {
                    sourceType = "Validation"
                    reasoning.add("This improvement has been successfully validated in production.")
                    if (report != null) {
                        reasoning.add("Performance remained at ${"%.2f".format(report.measuredScore)} (+${"%.1f".format((report.measuredScore - report.baselineScore) / report.baselineScore * 100)}% over baseline) across ${report.productionSampleCount} samples.")
                    }
                    reasoning.add("The optimization is now considered an authoritative part of the system configuration.")
                }
                IntelligenceLifecycleState.MONITORING -> {
                    sourceType = "Monitoring"
                    reasoning.add("Aura is currently watching the results of this implementation in a live environment.")
                    reasoning.add("The system is comparing post-implementation telemetry against the established baseline to confirm the predicted ${improvement.expectedImpact.lowercase()}.")
                    limitations.add("Current results are preliminary and require the full sample count to be considered authoritative.")
                }
                IntelligenceLifecycleState.VERIFICATION_PASSED -> {
                    sourceType = "Verification"
                    reasoning.add("The implementation has passed all automated verification checks, including build stability and regression tests.")
                    reasoning.add("The changes are confirmed to be within the approved technical scope and are now safe to monitor in production.")
                }
                IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS -> {
                    sourceType = "Implementation"
                    reasoning.add("The technical changes are currently being applied to the codebase.")
                    reasoning.add("Aura is tracking the modified files and functions to ensure they align with the approved Blueprint Artifact.")
                }
                IntelligenceLifecycleState.APPROVED -> {
                    sourceType = "Approval"
                    val approval = history.find { it.toState == IntelligenceLifecycleState.APPROVED }
                    reasoning.add("This improvement was authorized on ${SimpleDateFormat("MMM d", Locale.US).format(Date(approval?.timestamp ?: improvement.createdAt))}.")
                    reasoning.add("The decision was based on ${improvement.evidenceCount} production samples and an expected ${improvement.expectedImpact.lowercase()}.")
                }
                else -> {
                    sourceType = "Improvement"
                    reasoning.add("Aura identified this optimization because ${improvement.rationale.lowercase()}")
                    if (report != null) {
                        reasoning.add("This conclusion is supported by ${report.productionSampleCount} production samples collected recently.")
                        if (report.productionRegressionEstablished) {
                            reasoning.add("A performance regression was detected, making this change a high priority to restore baseline stability.")
                        } else {
                            reasoning.add("The system is currently performing within expected ranges, but parameter refinement is predicted to yield a ${"%.1f".format((report.targetScore - report.measuredScore) / report.measuredScore * 100)}% improvement.")
                        }
                    }
                }
            }

            _explanation.value = IntelligenceExplanation(
                sourceType = sourceType,
                sourceId = improvement.id,
                summary = improvement.title,
                reasoning = reasoning,
                evidenceStrength = if (improvement.evidenceCount > 50) "High" else if (improvement.evidenceCount > 10) "Medium" else "Low",
                confidence = improvement.confidence,
                limitations = limitations,
                whatWillChange = improvement.proposedChanges.modifications.map { "${it.component}.${it.parameter}" },
                whatWillNotChange = listOf("Core architecture", "Unrelated system parameters"),
                riskAssessment = improvement.risk
            )
        }
    }

    fun askAuraToExplainFinding(findingId: String) {
        viewModelScope.launch {
            val finding = repository.getAllFindings().first().find { it.id == findingId } ?: return@launch
            val report = finding.technicalDetails.closedLoopReport
            val reasoning = mutableListOf<String>()
            reasoning.add("Aura observed that ${finding.summary.lowercase()}")
            
            if (report != null) {
                reasoning.add("The observed result of ${"%.2f".format(report.measuredScore)} was compared against a baseline of ${"%.2f".format(report.baselineScore)}.")
                reasoning.add("This analysis is based on ${report.productionSampleCount} production samples with a quality score of ${"%.0f%%".format(report.productionEvidenceQuality * 100)}.")
            }

            _explanation.value = IntelligenceExplanation(
                sourceType = "Finding",
                sourceId = finding.id,
                summary = finding.title,
                reasoning = reasoning,
                evidenceStrength = if ((report?.productionSampleCount ?: 0) > 50) "High" else "Medium",
                confidence = finding.confidence,
                limitations = listOf("The result reflects the current production population and may change as additional users are observed.")
            )
        }
    }

    fun clearExplanation() {
        _explanation.value = null
    }

    init {
        viewModelScope.launch {
            combine(
                repository.getAllFindings(),
                repository.getMasterReportFlow(),
                repository.getDecisionCenterFlow()
            ) { findings, master, decision ->
                _state.value.copy(
                    findings = findings,
                    improvements = master.improvementPipeline,
                    summaryMetrics = master.executiveSummary.metrics,
                    systemAnalysis = master.systemAnalysis,
                    masterReport = master,
                    decisionCenter = decision,
                    isLoading = false
                )
            }.collect { newState ->
                _state.value = newState
            }
        }

        viewModelScope.launch {
            repository.getAllIntelligenceEvents().collect {
                _events.value = it
            }
        }

        viewModelScope.launch {
            repository.getAllAttentionItems().collect {
                _attentionInbox.value = it
            }
        }
    }

    fun markAllAsReviewed() {
        viewModelScope.launch {
            repository.markAllAsReviewed()
            refreshData()
        }
    }

    fun markAsSeen(targetId: String) {
        viewModelScope.launch {
            repository.markAsSeen(targetId)
        }
    }

    fun markAsReviewed(targetId: String) {
        viewModelScope.launch {
            repository.markAsReviewed(targetId)
        }
    }

    fun saveReportSnapshot(report: MasterIntelligenceReport) {
        viewModelScope.launch {
            repository.saveReportSnapshot(report)
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            val lastReview = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            val master = repository.generateMasterReport(lastReview)
            val decision = repository.getDecisionCenterState()
            _state.value = _state.value.copy(
                masterReport = master,
                decisionCenter = decision
            )
        }
    }

    fun setFilter(filter: IntelligenceMetricsFilter) {
        _state.value = _state.value.copy(filter = filter)
    }
}

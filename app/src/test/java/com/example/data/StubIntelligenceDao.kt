package com.example.data

import com.example.data.db.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class StubIntelligenceDao : IntelligenceDao {
    override fun getAllFindings(): Flow<List<FindingEntity>> = flowOf(emptyList())
    override suspend fun getFindingById(id: String): FindingEntity? = null
    override suspend fun insertFinding(finding: FindingEntity) {}
    override suspend fun updateFinding(finding: FindingEntity) {}
    override fun getImprovementsForFinding(findingId: String): Flow<List<SuggestedImprovementEntity>> = flowOf(emptyList())
    override suspend fun getImprovementById(id: String): SuggestedImprovementEntity? = null
    override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) {}
    override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) {}
    override fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>> = flowOf(emptyList())
    override fun getLifecycleHistory(targetId: String): Flow<List<LifecycleEventEntity>> = flowOf(emptyList())
    override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) {}
    override fun getAllActions(): Flow<List<IntelligenceActionEntity>> = flowOf(emptyList())
    override fun getActionsForImprovement(improvementId: String): Flow<List<IntelligenceActionEntity>> = flowOf(emptyList())
    override suspend fun getActionById(id: String): IntelligenceActionEntity? = null
    override suspend fun insertAction(action: IntelligenceActionEntity) {}
    override suspend fun updateAction(action: IntelligenceActionEntity) {}
    override fun getAllImplementationRuns(): Flow<List<ImplementationRunEntity>> = flowOf(emptyList())
    override fun getAllMonitoringSessions(): Flow<List<MonitoringSessionEntity>> = flowOf(emptyList())
    override fun getAllRegressionAlerts(): Flow<List<RegressionAlertEntity>> = flowOf(emptyList())
    override fun getImplementationRunsForImprovement(improvementId: String): Flow<List<ImplementationRunEntity>> = flowOf(emptyList())
    override suspend fun getImplementationRunById(id: String): ImplementationRunEntity? = null
    override suspend fun insertImplementationRun(run: ImplementationRunEntity) {}
    override suspend fun updateImplementationRun(run: ImplementationRunEntity) {}
    override fun getVerificationResultsForImprovement(improvementId: String): Flow<List<VerificationResultEntity>> = flowOf(emptyList())
    override fun getVerificationResultsForRun(runId: String): Flow<List<VerificationResultEntity>> = flowOf(emptyList())
    override suspend fun insertVerificationResult(result: VerificationResultEntity) {}
    override fun getMonitoringSessionsForImprovement(improvementId: String): Flow<List<MonitoringSessionEntity>> = flowOf(emptyList())
    override suspend fun getMonitoringSessionById(id: String): MonitoringSessionEntity? = null
    override suspend fun insertMonitoringSession(session: MonitoringSessionEntity) {}
    override suspend fun updateMonitoringSession(session: MonitoringSessionEntity) {}
    override fun getValidationResultsForImprovement(improvementId: String): Flow<List<ValidationResultEntity>> = flowOf(emptyList())
    override suspend fun insertValidationResult(result: ValidationResultEntity) {}
    override fun getRegressionAlertsForImprovement(improvementId: String): Flow<List<RegressionAlertEntity>> = flowOf(emptyList())
    override suspend fun getRegressionAlertById(id: String): RegressionAlertEntity? = null
    override suspend fun insertRegressionAlert(alert: RegressionAlertEntity) {}
    override suspend fun updateRegressionAlert(alert: RegressionAlertEntity) {}
    override fun getRollbackRunsForImprovement(improvementId: String): Flow<List<RollbackRunEntity>> = flowOf(emptyList())
    override suspend fun getRollbackRunById(id: String): RollbackRunEntity? = null
    override suspend fun insertRollbackRun(run: RollbackRunEntity) {}
    override suspend fun updateRollbackRun(run: RollbackRunEntity) {}
    override suspend fun getReviewMetadata(targetId: String): ReviewMetadataEntity? = null
    override suspend fun insertReviewMetadata(metadata: ReviewMetadataEntity) {}
    override suspend fun getCheckpoint(id: String): UserCheckpointEntity? = null
    override fun observeCheckpoint(id: String): Flow<UserCheckpointEntity?> = flowOf(null)
    override suspend fun insertCheckpoint(checkpoint: UserCheckpointEntity) {}
    override fun getPendingEvents(): Flow<List<IntelligenceEventEntity>> = flowOf(emptyList())
    override suspend fun insertEvent(event: IntelligenceEventEntity) {}
    override fun getAllStoredEvidence(): Flow<List<EvidenceEntity>> = flowOf(emptyList())
    override fun getAllIntelligenceEvents(): Flow<List<IntelligenceEventEntity>> = flowOf(emptyList())
    override suspend fun updateEvent(event: IntelligenceEventEntity) {}
    override fun getAuditHistory(targetId: String): Flow<List<IntegrityAuditEntity>> = flowOf(emptyList())
    override suspend fun insertAudit(audit: IntegrityAuditEntity) {}
    override suspend fun getEvidenceSnapshot(improvementId: String): EvidenceSnapshotEntity? = null
    override suspend fun insertEvidenceSnapshot(snapshot: EvidenceSnapshotEntity) {}
    override fun getAllSavedReports(): Flow<List<SavedIntelligenceReportEntity>> = flowOf(emptyList())
    override suspend fun getSavedReportById(id: String): SavedIntelligenceReportEntity? = null
    override suspend fun insertSavedReport(report: SavedIntelligenceReportEntity) {}
    override suspend fun getArtifactById(id: String): BlueprintArtifactEntity? = null
    override fun getArtifactsForBlueprint(blueprintId: String): Flow<List<BlueprintArtifactEntity>> = flowOf(emptyList())
    override fun getArtifactsForImprovement(improvementId: String): Flow<List<BlueprintArtifactEntity>> = flowOf(emptyList())
    override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) {}
    override fun getAllAttentionItems(): Flow<List<AttentionItemEntity>> = flowOf(emptyList())
    override fun getActionableAttentionItems(): Flow<List<AttentionItemEntity>> = flowOf(emptyList())
    override suspend fun insertAttentionItem(item: AttentionItemEntity) {}
    override suspend fun updateAttentionItem(item: AttentionItemEntity) {}
    override suspend fun deleteActiveAttentionItemByDeduplicationKey(key: String) {}
}

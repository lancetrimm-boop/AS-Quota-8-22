package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IntelligenceDao {
    @Query("SELECT * FROM intelligence_findings ORDER BY dateDiscovered DESC")
    fun getAllFindings(): Flow<List<FindingEntity>>

    @Query("SELECT * FROM intelligence_findings WHERE id = :id")
    suspend fun getFindingById(id: String): FindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinding(finding: FindingEntity)

    @Update
    suspend fun updateFinding(finding: FindingEntity)

    @Query("SELECT * FROM suggested_improvements WHERE findingId = :findingId ORDER BY createdAt DESC")
    fun getImprovementsForFinding(findingId: String): Flow<List<SuggestedImprovementEntity>>

    @Query("SELECT * FROM suggested_improvements WHERE id = :id")
    suspend fun getImprovementById(id: String): SuggestedImprovementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImprovement(improvement: SuggestedImprovementEntity)

    @Update
    suspend fun updateImprovement(improvement: SuggestedImprovementEntity)

    @Query("SELECT * FROM suggested_improvements ORDER BY createdAt DESC")
    fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>>

    @Query("SELECT * FROM intelligence_lifecycle_events WHERE targetId = :targetId ORDER BY timestamp ASC")
    fun getLifecycleHistory(targetId: String): Flow<List<LifecycleEventEntity>>

    @Insert
    suspend fun insertLifecycleEvent(event: LifecycleEventEntity)

    @Query("SELECT * FROM intelligence_actions ORDER BY updatedAt DESC")
    fun getAllActions(): Flow<List<IntelligenceActionEntity>>

    @Query("SELECT * FROM intelligence_actions WHERE improvementId = :improvementId")
    fun getActionsForImprovement(improvementId: String): Flow<List<IntelligenceActionEntity>>

    @Query("SELECT * FROM intelligence_actions WHERE id = :id")
    suspend fun getActionById(id: String): IntelligenceActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: IntelligenceActionEntity)

    @Update
    suspend fun updateAction(action: IntelligenceActionEntity)

    @Query("SELECT * FROM implementation_runs ORDER BY startTime DESC")
    fun getAllImplementationRuns(): Flow<List<ImplementationRunEntity>>

    @Query("SELECT * FROM monitoring_sessions ORDER BY startTime DESC")
    fun getAllMonitoringSessions(): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM regression_alerts ORDER BY timestamp DESC")
    fun getAllRegressionAlerts(): Flow<List<RegressionAlertEntity>>

    @Query("SELECT * FROM implementation_runs WHERE improvementId = :improvementId ORDER BY startTime DESC")
    fun getImplementationRunsForImprovement(improvementId: String): Flow<List<ImplementationRunEntity>>

    @Query("SELECT * FROM implementation_runs WHERE id = :id")
    suspend fun getImplementationRunById(id: String): ImplementationRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImplementationRun(run: ImplementationRunEntity)

    @Update
    suspend fun updateImplementationRun(run: ImplementationRunEntity)

    @Query("SELECT * FROM verification_results WHERE improvementId = :improvementId ORDER BY timestamp DESC")
    fun getVerificationResultsForImprovement(improvementId: String): Flow<List<VerificationResultEntity>>

    @Query("SELECT * FROM verification_results WHERE runId = :runId")
    fun getVerificationResultsForRun(runId: String): Flow<List<VerificationResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerificationResult(result: VerificationResultEntity)

    @Query("SELECT * FROM monitoring_sessions WHERE improvementId = :improvementId ORDER BY startTime DESC")
    fun getMonitoringSessionsForImprovement(improvementId: String): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM monitoring_sessions WHERE id = :id")
    suspend fun getMonitoringSessionById(id: String): MonitoringSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonitoringSession(session: MonitoringSessionEntity)

    @Update
    suspend fun updateMonitoringSession(session: MonitoringSessionEntity)

    @Query("SELECT * FROM validation_results_history WHERE improvementId = :improvementId ORDER BY timestamp DESC")
    fun getValidationResultsForImprovement(improvementId: String): Flow<List<ValidationResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValidationResult(result: ValidationResultEntity)

    @Query("SELECT * FROM regression_alerts WHERE improvementId = :improvementId ORDER BY timestamp DESC")
    fun getRegressionAlertsForImprovement(improvementId: String): Flow<List<RegressionAlertEntity>>

    @Query("SELECT * FROM regression_alerts WHERE id = :id")
    suspend fun getRegressionAlertById(id: String): RegressionAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegressionAlert(alert: RegressionAlertEntity)

    @Update
    suspend fun updateRegressionAlert(alert: RegressionAlertEntity)

    @Query("SELECT * FROM rollback_runs WHERE improvementId = :improvementId ORDER BY startTime DESC")
    fun getRollbackRunsForImprovement(improvementId: String): Flow<List<RollbackRunEntity>>

    @Query("SELECT * FROM rollback_runs WHERE id = :id")
    suspend fun getRollbackRunById(id: String): RollbackRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRollbackRun(run: RollbackRunEntity)

    @Update
    suspend fun updateRollbackRun(run: RollbackRunEntity)

    @Query("SELECT * FROM review_metadata WHERE targetId = :targetId")
    suspend fun getReviewMetadata(targetId: String): ReviewMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewMetadata(metadata: ReviewMetadataEntity)

    @Query("SELECT * FROM user_checkpoints WHERE checkpointId = :id")
    suspend fun getCheckpoint(id: String): UserCheckpointEntity?

    @Query("SELECT * FROM user_checkpoints WHERE checkpointId = :id")
    fun observeCheckpoint(id: String): Flow<UserCheckpointEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoint(checkpoint: UserCheckpointEntity)

    @Query("SELECT * FROM intelligence_events WHERE status = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingEvents(): Flow<List<IntelligenceEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: IntelligenceEventEntity)

    @Query("SELECT * FROM evidence_records ORDER BY timestamp DESC")
    fun getAllStoredEvidence(): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM intelligence_events ORDER BY timestamp DESC")
    fun getAllIntelligenceEvents(): Flow<List<IntelligenceEventEntity>>

    @Update
    suspend fun updateEvent(event: IntelligenceEventEntity)

    @Query("SELECT * FROM integrity_audit_history WHERE targetId = :targetId ORDER BY timestamp DESC")
    fun getAuditHistory(targetId: String): Flow<List<IntegrityAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: IntegrityAuditEntity)

    @Query("SELECT * FROM evidence_snapshots WHERE improvementId = :improvementId")
    suspend fun getEvidenceSnapshot(improvementId: String): EvidenceSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidenceSnapshot(snapshot: EvidenceSnapshotEntity)

    @Query("SELECT * FROM saved_intelligence_reports ORDER BY timestamp DESC")
    fun getAllSavedReports(): Flow<List<SavedIntelligenceReportEntity>>

    @Query("SELECT * FROM saved_intelligence_reports WHERE id = :id")
    suspend fun getSavedReportById(id: String): SavedIntelligenceReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedReport(report: SavedIntelligenceReportEntity)

    @Query("SELECT * FROM blueprint_artifacts WHERE id = :id")
    suspend fun getArtifactById(id: String): BlueprintArtifactEntity?

    @Query("SELECT * FROM blueprint_artifacts WHERE blueprintId = :blueprintId ORDER BY createdAt DESC")
    fun getArtifactsForBlueprint(blueprintId: String): Flow<List<BlueprintArtifactEntity>>

    @Query("SELECT * FROM blueprint_artifacts WHERE improvementId = :improvementId ORDER BY createdAt DESC")
    fun getArtifactsForImprovement(improvementId: String): Flow<List<BlueprintArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: BlueprintArtifactEntity)

    @Query("SELECT * FROM attention_items ORDER BY createdAt DESC")
    fun getAllAttentionItems(): Flow<List<AttentionItemEntity>>

    @Query("SELECT * FROM attention_items WHERE status IN ('NEW', 'SEEN') AND requiresAction = 1")
    fun getActionableAttentionItems(): Flow<List<AttentionItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttentionItem(item: AttentionItemEntity)

    @Update
    suspend fun updateAttentionItem(item: AttentionItemEntity)

    @Query("DELETE FROM attention_items WHERE deduplicationKey = :key AND status NOT IN ('RESOLVED', 'DISMISSED')")
    suspend fun deleteActiveAttentionItemByDeduplicationKey(key: String)
}

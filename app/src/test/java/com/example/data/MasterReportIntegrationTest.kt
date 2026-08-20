package com.example.data

import com.example.data.db.*
import com.example.data.blueprint.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MasterReportIntegrationTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_GenerateAndSnapshotReport() = runTest {
        // 1. Setup baseline data
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Baseline Find")
        
        // 2. Generate Live Report
        val liveReport = repository.generateMasterReport(0L)
        assertNotNull(liveReport)
        assertFalse(liveReport.isSnapshot)
        assertEquals(1, liveReport.sinceLastReview.newFindings)

        // 3. Save Snapshot
        repository.saveReportSnapshot(liveReport)
        
        // 4. Update data (advance lifecycle)
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        // 5. Verify Live vs Snapshot
        val newLiveReport = repository.generateMasterReport(0L)
        val savedReports = repository.getAllSavedReports().first()
        
        assertEquals(1, savedReports.size)
        val snapshot = savedReports[0]
        
        assertTrue(snapshot.isSnapshot)
        // Snapshot should still only have 1 finding/change as it was frozen
        assertEquals(1, snapshot.improvementPipeline.size) // No improvements at time of snapshot
        
        // New live report should have the improvement
        assertTrue(newLiveReport.improvementPipeline.isNotEmpty())
    }

    class FakeIntelligenceDao : IntelligenceDao {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val checkpoints = mutableMapOf<String, UserCheckpointEntity>()
        val savedReports = mutableMapOf<String, SavedIntelligenceReportEntity>()

        override fun getAllFindings(): Flow<List<FindingEntity>> = MutableStateFlow(findings.values.toList())
        override suspend fun getFindingById(id: String) = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }

        override fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>> = MutableStateFlow(improvements.values.toList())
        override suspend fun getImprovementById(id: String) = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }

        override suspend fun getCheckpoint(id: String) = checkpoints[id]
        override fun observeCheckpoint(id: String): Flow<UserCheckpointEntity?> = MutableStateFlow(checkpoints[id])
        override suspend fun insertCheckpoint(checkpoint: UserCheckpointEntity) { checkpoints[checkpoint.checkpointId] = checkpoint }

        override fun getAllSavedReports(): Flow<List<SavedIntelligenceReportEntity>> = MutableStateFlow(savedReports.values.toList())
        override suspend fun getSavedReportById(id: String) = savedReports[id]
        override suspend fun insertSavedReport(report: SavedIntelligenceReportEntity) { savedReports[report.id] = report }

        // Minimal implementation for other required methods
        override fun getImprovementsForFinding(findingId: String) = MutableStateFlow<List<SuggestedImprovementEntity>>(emptyList())
        override fun getLifecycleHistory(targetId: String) = MutableStateFlow<List<LifecycleEventEntity>>(emptyList())
        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) {}
        override fun getAllActions() = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override fun getActionsForImprovement(improvementId: String) = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override suspend fun getActionById(id: String) = null
        override suspend fun insertAction(action: IntelligenceActionEntity) {}
        override suspend fun updateAction(action: IntelligenceActionEntity) {}
        override fun getAllImplementationRuns() = MutableStateFlow<List<ImplementationRunEntity>>(emptyList())
        override fun getAllMonitoringSessions() = MutableStateFlow<List<MonitoringSessionEntity>>(emptyList())
        override fun getAllRegressionAlerts() = MutableStateFlow<List<RegressionAlertEntity>>(emptyList())
        override fun getImplementationRunsForImprovement(improvementId: String) = MutableStateFlow<List<ImplementationRunEntity>>(emptyList())
        override suspend fun getImplementationRunById(id: String) = null
        override suspend fun insertImplementationRun(run: ImplementationRunEntity) {}
        override suspend fun updateImplementationRun(run: ImplementationRunEntity) {}
        override fun getVerificationResultsForImprovement(improvementId: String) = MutableStateFlow<List<VerificationResultEntity>>(emptyList())
        override fun getVerificationResultsForRun(runId: String) = MutableStateFlow<List<VerificationResultEntity>>(emptyList())
        override suspend fun insertVerificationResult(result: VerificationResultEntity) {}
        override fun getMonitoringSessionsForImprovement(improvementId: String) = MutableStateFlow<List<MonitoringSessionEntity>>(emptyList())
        override suspend fun getMonitoringSessionById(id: String) = null
        override suspend fun insertMonitoringSession(session: MonitoringSessionEntity) {}
        override suspend fun updateMonitoringSession(session: MonitoringSessionEntity) {}
        override fun getValidationResultsForImprovement(improvementId: String) = MutableStateFlow<List<ValidationResultEntity>>(emptyList())
        override suspend fun insertValidationResult(result: ValidationResultEntity) {}
        override fun getRegressionAlertsForImprovement(improvementId: String) = MutableStateFlow<List<RegressionAlertEntity>>(emptyList())
        override suspend fun getRegressionAlertById(id: String) = null
        override suspend fun insertRegressionAlert(alert: RegressionAlertEntity) {}
        override suspend fun updateRegressionAlert(alert: RegressionAlertEntity) {}
        override fun getRollbackRunsForImprovement(improvementId: String) = MutableStateFlow<List<RollbackRunEntity>>(emptyList())
        override suspend fun getRollbackRunById(id: String) = null
        override suspend fun insertRollbackRun(run: RollbackRunEntity) {}
        override suspend fun updateRollbackRun(run: RollbackRunEntity) {}
        override suspend fun getReviewMetadata(targetId: String) = null
        override suspend fun insertReviewMetadata(metadata: ReviewMetadataEntity) {}
        override fun getPendingEvents() = MutableStateFlow<List<IntelligenceEventEntity>>(emptyList())
        override suspend fun insertEvent(event: IntelligenceEventEntity) {}
        override suspend fun updateEvent(event: IntelligenceEventEntity) {}
        override fun getAllStoredEvidence() = MutableStateFlow<List<EvidenceEntity>>(emptyList())
        override fun getAllIntelligenceEvents() = MutableStateFlow<List<IntelligenceEventEntity>>(emptyList())
        override fun getAllAttentionItems() = MutableStateFlow<List<AttentionItemEntity>>(emptyList())
        override fun getActionableAttentionItems() = MutableStateFlow<List<AttentionItemEntity>>(emptyList())
        override suspend fun insertAttentionItem(item: AttentionItemEntity) {}
        override suspend fun updateAttentionItem(item: AttentionItemEntity) {}
        override suspend fun deleteActiveAttentionItemByDeduplicationKey(key: String) {}
        override fun getAuditHistory(targetId: String) = MutableStateFlow<List<IntegrityAuditEntity>>(emptyList())
        override suspend fun insertAudit(audit: IntegrityAuditEntity) {}
        override suspend fun getEvidenceSnapshot(improvementId: String) = null
        override suspend fun insertEvidenceSnapshot(snapshot: EvidenceSnapshotEntity) {}
        override fun getArtifactsForBlueprint(blueprintId: String) = MutableStateFlow<List<BlueprintArtifactEntity>>(emptyList())
        override fun getArtifactsForImprovement(improvementId: String) = MutableStateFlow<List<BlueprintArtifactEntity>>(emptyList())
        override suspend fun getArtifactById(id: String) = null
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) {}
    }
}

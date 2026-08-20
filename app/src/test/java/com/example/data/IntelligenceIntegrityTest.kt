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

class IntelligenceIntegrityTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_StaleEvidence_Detection() = runTest {
        // Create an improvement with an old creation date
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Old Finding")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        // Force the creation date to be 31 days ago
        val thirtyOneDaysAgo = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000)
        val staleEntity = fakeDao.improvements[improvement.id]!!.copy(createdAt = thirtyOneDaysAgo)
        fakeDao.updateImprovement(staleEntity)

        // Perform audit
        val result = repository.performIntegrityAudit(improvement.id, "Improvement")
        
        assertEquals(IntegrityStatus.WARNING, result.status)
        assertTrue(result.issues.any { it.code == "STALE_EVIDENCE" })
    }

    @Test
    fun test02_ConflictingIntelligence_Detection() = runTest {
        // Setup Improvement
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Personalization Boost")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        // Create a new Finding for the same target that says NO ACTION
        val noActionReport = ClosedLoopEngine.evaluate(50.0, 50.0, 70.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 50.0)
        ))
        // This report should classify as NO_ACTION_REQUIRED in my classification helper
        val newFinding = repository.createFindingFromReport(noActionReport, "System Stable")
        assertEquals(FindingClassification.NO_ACTION_REQUIRED, newFinding.classification)

        // Audit the original improvement
        val result = repository.performIntegrityAudit(improvement.id, "Improvement")
        
        // Should detect conflict because improvement is pending but latest finding says all good
        assertEquals(IntegrityStatus.FAIL, result.status) // High severity conflict
        assertTrue(result.issues.any { it.code == "CONFLICTING_INTELLIGENCE" })
    }

    @Test
    fun test03_EvidenceSnapshot_Integrity() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Boost")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        val artifact = BlueprintArtifact(
            blueprintId = finding.technicalDetails.id,
            strategyBlueprint = finding.technicalDetails
        )
        
        // Approve -> Should create snapshot
        repository.approveImprovement(improvement.id, artifact)
        
        val snapshot = fakeDao.snapshots[improvement.id]
        assertNotNull(snapshot)
        // Manual check for now or move helpers
        assertTrue(snapshot!!.reportJson.contains(report.baselineScore.toString()))
    }

    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val events = mutableListOf<LifecycleEventEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()
        val audits = mutableMapOf<String, IntegrityAuditEntity>()
        val snapshots = mutableMapOf<String, EvidenceSnapshotEntity>()

        override fun getAllFindings(): Flow<List<FindingEntity>> = MutableStateFlow(findings.values.toList())
        override suspend fun getFindingById(id: String): FindingEntity? = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }

        override fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>> = MutableStateFlow(improvements.values.toList())
        override suspend fun getImprovementById(id: String): SuggestedImprovementEntity? = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }

        override fun getLifecycleHistory(targetId: String): Flow<List<LifecycleEventEntity>> = 
            MutableStateFlow(events.filter { it.targetId == targetId })
        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) { events.add(event) }

        override fun getArtifactsForImprovement(improvementId: String): Flow<List<BlueprintArtifactEntity>> = 
            MutableStateFlow(artifacts.values.filter { it.improvementId == improvementId })
        override suspend fun getArtifactById(id: String): BlueprintArtifactEntity? = artifacts[id]
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) { artifacts[artifact.id] = artifact }

        override fun getAuditHistory(targetId: String): Flow<List<IntegrityAuditEntity>> = 
            MutableStateFlow(audits.values.filter { it.targetId == targetId }.sortedByDescending { it.timestamp })
        override suspend fun insertAudit(audit: IntegrityAuditEntity) { audits[audit.id] = audit }

        override suspend fun getEvidenceSnapshot(improvementId: String): EvidenceSnapshotEntity? = snapshots[improvementId]
        override suspend fun insertEvidenceSnapshot(snapshot: EvidenceSnapshotEntity) { snapshots[snapshot.improvementId] = snapshot }

        // Unimplemented for this test
        override fun getImprovementsForFinding(findingId: String) = MutableStateFlow<List<SuggestedImprovementEntity>>(emptyList())
        override fun getAllActions() = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override fun getActionsForImprovement(improvementId: String) = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override suspend fun getActionById(id: String) = null
        override suspend fun insertAction(action: IntelligenceActionEntity) {}
        override suspend fun updateAction(action: IntelligenceActionEntity) {}
        override fun getArtifactsForBlueprint(blueprintId: String) = MutableStateFlow<List<BlueprintArtifactEntity>>(emptyList())
        override fun getVerificationResultsForImprovement(improvementId: String) = MutableStateFlow<List<VerificationResultEntity>>(emptyList())
        override fun getVerificationResultsForRun(runId: String) = MutableStateFlow<List<VerificationResultEntity>>(emptyList())
        override suspend fun insertVerificationResult(result: VerificationResultEntity) {}
        override fun getAllImplementationRuns() = MutableStateFlow<List<ImplementationRunEntity>>(emptyList())
        override fun getImplementationRunsForImprovement(improvementId: String) = MutableStateFlow<List<ImplementationRunEntity>>(emptyList())
        override suspend fun getImplementationRunById(id: String) = null
        override suspend fun insertImplementationRun(run: ImplementationRunEntity) {}
        override suspend fun updateImplementationRun(run: ImplementationRunEntity) {}
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
        override suspend fun getCheckpoint(id: String) = null
        override suspend fun insertCheckpoint(checkpoint: UserCheckpointEntity) {}
    }
}

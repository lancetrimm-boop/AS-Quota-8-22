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

class ReconstructionPackageTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_ExportReconstructionPackage() = runTest {
        // 1. Setup sample data
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Personalization Opportunity")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        // 2. Generate Package
        val pkg = repository.generateReconstructionPackage(improvement.id)
        
        assertNotNull(pkg)
        assertEquals(improvement.id, pkg.improvementId)
        assertEquals(finding.id, pkg.findingId)
        assertNotNull(pkg.blueprintArtifact)
    }

    @Test
    fun test02_ImportReconstructionPackage() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, emptyList())
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        val artifact = BlueprintArtifact(
            blueprintId = "ART-1",
            strategyBlueprint = blueprint
        )
        
        val pkg = ReconstructionPackage(
            improvementId = "IMP-RECON",
            findingId = "FIND-RECON",
            blueprintArtifact = artifact,
            approvalState = "APPROVED"
        )
        
        repository.importReconstructionPackage(pkg)
        
        val imported = fakeDao.getImprovementById("IMP-RECON")
        assertNotNull(imported)
        assertEquals("Title", imported?.title)
        assertEquals(IntelligenceLifecycleState.APPROVED, imported?.status)
    }

    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()
        val runs = mutableMapOf<String, ImplementationRunEntity>()
        val verResults = mutableMapOf<String, VerificationResultEntity>()
        val monSessions = mutableMapOf<String, MonitoringSessionEntity>()
        val valResults = mutableMapOf<String, ValidationResultEntity>()

        override fun getAllFindings(): Flow<List<FindingEntity>> = MutableStateFlow(findings.values.toList())
        override suspend fun getFindingById(id: String): FindingEntity? = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }

        override fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>> = MutableStateFlow(improvements.values.toList())
        override suspend fun getImprovementById(id: String): SuggestedImprovementEntity? = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }

        override fun getArtifactsForImprovement(improvementId: String): Flow<List<BlueprintArtifactEntity>> = 
            MutableStateFlow(artifacts.values.filter { it.improvementId == improvementId })
        override suspend fun getArtifactById(id: String): BlueprintArtifactEntity? = artifacts[id]
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) { artifacts[artifact.id] = artifact }

        override fun getImplementationRunsForImprovement(improvementId: String): Flow<List<ImplementationRunEntity>> = 
            MutableStateFlow(runs.values.filter { it.improvementId == improvementId })
        override suspend fun getImplementationRunById(id: String): ImplementationRunEntity? = runs[id]
        override suspend fun insertImplementationRun(run: ImplementationRunEntity) { runs[run.id] = run }
        override suspend fun updateImplementationRun(run: ImplementationRunEntity) { runs[run.id] = run }

        override fun getVerificationResultsForImprovement(improvementId: String): Flow<List<VerificationResultEntity>> = 
            MutableStateFlow(verResults.values.filter { it.improvementId == improvementId })
        override suspend fun insertVerificationResult(result: VerificationResultEntity) { verResults[result.id] = result }

        override fun getMonitoringSessionsForImprovement(improvementId: String): Flow<List<MonitoringSessionEntity>> = 
            MutableStateFlow(monSessions.values.filter { it.improvementId == improvementId })
        override suspend fun getMonitoringSessionById(id: String): MonitoringSessionEntity? = monSessions[id]
        override suspend fun insertMonitoringSession(session: MonitoringSessionEntity) { monSessions[session.id] = session }
        override suspend fun updateMonitoringSession(session: MonitoringSessionEntity) { monSessions[session.id] = session }

        override fun getValidationResultsForImprovement(improvementId: String): Flow<List<ValidationResultEntity>> = 
            MutableStateFlow(valResults.values.filter { it.improvementId == improvementId })
        override suspend fun insertValidationResult(result: ValidationResultEntity) { valResults[result.id] = result }

        // Unimplemented
        override fun getImprovementsForFinding(findingId: String) = MutableStateFlow<List<SuggestedImprovementEntity>>(emptyList())
        override fun getLifecycleHistory(targetId: String) = MutableStateFlow<List<LifecycleEventEntity>>(emptyList())
        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) {}
        override fun getAllActions() = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override fun getActionsForImprovement(improvementId: String) = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override suspend fun getActionById(id: String) = null
        override suspend fun insertAction(action: IntelligenceActionEntity) {}
        override suspend fun updateAction(action: IntelligenceActionEntity) {}
        override fun getArtifactsForBlueprint(blueprintId: String) = MutableStateFlow<List<BlueprintArtifactEntity>>(emptyList())
        override fun getAllImplementationRuns() = MutableStateFlow<List<ImplementationRunEntity>>(emptyList())
        override fun getVerificationResultsForRun(runId: String) = MutableStateFlow<List<VerificationResultEntity>>(emptyList())
        override fun getAllMonitoringSessions() = MutableStateFlow<List<MonitoringSessionEntity>>(emptyList())
        override fun getAllRegressionAlerts() = MutableStateFlow<List<RegressionAlertEntity>>(emptyList())
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
        override fun getAuditHistory(targetId: String) = MutableStateFlow<List<IntegrityAuditEntity>>(emptyList())
        override suspend fun insertAudit(audit: IntegrityAuditEntity) {}
        override suspend fun getEvidenceSnapshot(improvementId: String) = null
        override suspend fun insertEvidenceSnapshot(snapshot: EvidenceSnapshotEntity) {}
        override fun getPendingEvents() = MutableStateFlow<List<IntelligenceEventEntity>>(emptyList())
        override suspend fun insertEvent(event: IntelligenceEventEntity) {}
        override suspend fun updateEvent(event: IntelligenceEventEntity) {}
        override fun getAllStoredEvidence() = MutableStateFlow<List<EvidenceEntity>>(emptyList())
    }
}

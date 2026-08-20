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

/**
 * Integration tests for the Approval-to-Android-Studio Execution Pipeline.
 */
class IntelligenceExecutionPipelineTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_FullExecutionPipeline_Success() = runTest {
        // 1. Setup Approved Improvement
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Execution Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        val artifact = BlueprintArtifact(
            blueprintId = finding.technicalDetails.id,
            strategyBlueprint = finding.technicalDetails
        )
        repository.approveImprovement(improvement.id, artifact)
        
        // 2. Plan Implementation (Approval Gate)
        repository.planImplementation(improvement.id)
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_PLANNED, fakeDao.getImprovementById(improvement.id)?.status)
        
        // 3. Verify Implementation Package
        val pkg = repository.getImplementationPackage(improvement.id)
        assertNotNull(pkg.androidStudioPrompt)
        assertTrue(pkg.approvedScope.isNotEmpty())
        
        // 4. Start Execution (Implementation Run)
        val runs = repository.getImplementationRuns(improvement.id).first()
        val runId = runs[0].id
        repository.startImplementation(runId)
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS, fakeDao.getImprovementById(improvement.id)?.status)
        
        // 5. Complete Execution
        repository.completeImplementation(runId, "Done", listOf("MediaRepository.kt"))
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE, fakeDao.getImprovementById(improvement.id)?.status)
    }

    @Test(expected = IllegalStateException::class)
    fun test02_ApprovalGate_BlocksUnapproved() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Blocked Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        // Try to plan without approval
        repository.planImplementation(improvement.id)
    }

    @Test(expected = IllegalStateException::class)
    fun test03_IntegrityCheck_BlocksModifiedBlueprint() = runTest {
        // Setup approved
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Integrity Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        
        // Modify technical details silently (bypassing versioning)
        val modImp = fakeDao.getImprovementById(improvement.id)!!.copy(blueprintArtifactId = "MALICIOUS-CHANGE")
        fakeDao.updateImprovement(modImp)
        
        // Should block
        repository.planImplementation(improvement.id)
    }

    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val events = mutableListOf<LifecycleEventEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()
        val runs = mutableMapOf<String, ImplementationRunEntity>()

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

        override fun getAllImplementationRuns(): Flow<List<ImplementationRunEntity>> = MutableStateFlow(runs.values.toList())
        override fun getImplementationRunsForImprovement(improvementId: String): Flow<List<ImplementationRunEntity>> = 
            MutableStateFlow(runs.values.filter { it.improvementId == improvementId })
        override suspend fun getImplementationRunById(id: String): ImplementationRunEntity? = runs[id]
        override suspend fun insertImplementationRun(run: ImplementationRunEntity) { runs[run.id] = run }
        override suspend fun updateImplementationRun(run: ImplementationRunEntity) { runs[run.id] = run }

        // Unimplemented
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

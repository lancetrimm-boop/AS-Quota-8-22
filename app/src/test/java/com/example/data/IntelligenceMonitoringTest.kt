package com.example.data

import com.example.data.db.*
import com.example.data.blueprint.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Integration tests for the Aura Intelligence Monitoring and Validation Workflow.
 */
class IntelligenceMonitoringTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_MonitoringCycle_SuccessfulValidation() = runTest {
        // 1. Setup Approved, Implemented, Verified Improvement
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Monitoring Success Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        repository.planImplementation(improvement.id)
        val run = repository.getImplementationRuns(improvement.id).first()[0]
        repository.startImplementation(run.id)
        repository.completeImplementation(run.id)
        
        // 2. Pass Verification -> Should trigger Monitoring
        repository.recordVerificationResult(
            improvementId = improvement.id,
            runId = run.id,
            buildPassed = true,
            testsPassed = true,
            regressionPassed = true,
            dbIntegrityPassed = true,
            scopeCompliant = true,
            acceptanceCriteriaResults = emptyMap()
        )

        assertEquals(IntelligenceLifecycleState.MONITORING, fakeDao.getImprovementById(improvement.id)?.status)
        val sessions = repository.getMonitoringSessions(improvement.id).first()
        assertEquals(1, sessions.size)
        val session = sessions[0]
        assertEquals(MonitoringStatus.ACTIVE, session.status)
        assertEquals(0, session.currentSampleCount)

        // 3. Update Progress (Under Threshold)
        repository.updateMonitoringProgress(session.id, 50, 55.0, listOf("EV-1"))
        val updatedSession = fakeDao.getMonitoringSessionById(session.id)
        assertEquals(50, updatedSession?.currentSampleCount)
        assertEquals(IntelligenceLifecycleState.MONITORING, fakeDao.getImprovementById(improvement.id)?.status)

        // 4. Update Progress (Reach Threshold)
        repository.updateMonitoringProgress(session.id, 100, 62.0, listOf("EV-1", "EV-2"))
        
        // 5. Verify Validation
        val finalImp = fakeDao.getImprovementById(improvement.id)
        assertEquals(IntelligenceLifecycleState.VALIDATED, finalImp?.status)
        
        val results = repository.getValidationResults(improvement.id).first()
        assertEquals(1, results.size)
        assertEquals(IntelligenceLifecycleState.VALIDATED, results[0].outcome)
        assertEquals(62.0, results[0].finalValue, 0.1)
        assertEquals(12.0, results[0].change, 0.1)
        
        // Verify session is completed
        assertEquals(MonitoringStatus.COMPLETED, fakeDao.getMonitoringSessionById(session.id)?.status)
    }

    @Test
    fun test02_RegressionDetection_TriggersAlert() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Regression Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        repository.planImplementation(improvement.id)
        val run = repository.getImplementationRuns(improvement.id).first()[0]
        repository.startImplementation(run.id)
        repository.completeImplementation(run.id)
        repository.recordVerificationResult(improvement.id, run.id, true, true, true, true, true, emptyMap())

        val session = repository.getMonitoringSessions(improvement.id).first()[0]
        
        // Simulate sharp regression
        repository.updateMonitoringProgress(session.id, 20, 40.0, listOf("EV-BAD"))

        val finalImp = fakeDao.getImprovementById(improvement.id)
        assertEquals(IntelligenceLifecycleState.REGRESSION_DETECTED, finalImp?.status)
        
        val results = repository.getValidationResults(improvement.id).first()
        assertEquals(IntelligenceLifecycleState.REGRESSION_DETECTED, results[0].outcome)
        assertTrue(fakeDao.getMonitoringSessionById(session.id)?.regressionDetected == true)
    }

    /**
     * Fake DAO for Monitoring tests.
     */
    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val events = mutableListOf<LifecycleEventEntity>()
        val actions = mutableMapOf<String, IntelligenceActionEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()
        val runs = mutableMapOf<String, ImplementationRunEntity>()
        val results = mutableMapOf<String, VerificationResultEntity>()
        val sessions = mutableMapOf<String, MonitoringSessionEntity>()
        val validationResults = mutableMapOf<String, ValidationResultEntity>()

        override fun getAllFindings(): Flow<List<FindingEntity>> = MutableStateFlow(findings.values.toList())
        override suspend fun getFindingById(id: String): FindingEntity? = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }

        override fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>> = MutableStateFlow(improvements.values.toList())
        override fun getImprovementsForFinding(findingId: String): Flow<List<SuggestedImprovementEntity>> = 
            MutableStateFlow(improvements.values.filter { it.findingId == findingId })
        override suspend fun getImprovementById(id: String): SuggestedImprovementEntity? = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }

        override fun getLifecycleHistory(targetId: String): Flow<List<LifecycleEventEntity>> = 
            MutableStateFlow(events.filter { it.targetId == targetId })
        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) { events.add(event) }

        override fun getAllActions(): Flow<List<IntelligenceActionEntity>> = MutableStateFlow(actions.values.toList())
        override fun getActionsForImprovement(improvementId: String): Flow<List<IntelligenceActionEntity>> = 
            MutableStateFlow(actions.values.filter { it.improvementId == improvementId })
        override suspend fun getActionById(id: String): IntelligenceActionEntity? = actions[id]
        override suspend fun insertAction(action: IntelligenceActionEntity) { actions[action.id] = action }
        override suspend fun updateAction(action: IntelligenceActionEntity) { actions[action.id] = action }

        override fun getArtifactsForBlueprint(blueprintId: String): Flow<List<BlueprintArtifactEntity>> = 
            MutableStateFlow(artifacts.values.filter { it.blueprintId == blueprintId })
        override fun getArtifactsForImprovement(improvementId: String): Flow<List<BlueprintArtifactEntity>> = 
            MutableStateFlow(artifacts.values.filter { it.improvementId == improvementId })
        override suspend fun getArtifactById(id: String): BlueprintArtifactEntity? = artifacts[id]
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) { artifacts[artifact.id] = artifact }

        override fun getImplementationRunsForImprovement(improvementId: String): Flow<List<ImplementationRunEntity>> = 
            MutableStateFlow(runs.values.filter { it.improvementId == improvementId }.sortedByDescending { it.startTime })
        override suspend fun getImplementationRunById(id: String): ImplementationRunEntity? = runs[id]
        override suspend fun insertImplementationRun(run: ImplementationRunEntity) { runs[run.id] = run }
        override suspend fun updateImplementationRun(run: ImplementationRunEntity) { runs[run.id] = run }

        override fun getVerificationResultsForImprovement(improvementId: String): Flow<List<VerificationResultEntity>> = 
            MutableStateFlow(results.values.filter { it.improvementId == improvementId })
        override fun getVerificationResultsForRun(runId: String): Flow<List<VerificationResultEntity>> = 
            MutableStateFlow(results.values.filter { it.runId == runId })
        override suspend fun insertVerificationResult(result: VerificationResultEntity) { results[result.id] = result }

        override fun getMonitoringSessionsForImprovement(improvementId: String): Flow<List<MonitoringSessionEntity>> = 
            MutableStateFlow(sessions.values.filter { it.improvementId == improvementId }.sortedByDescending { it.startTime })
        override suspend fun getMonitoringSessionById(id: String): MonitoringSessionEntity? = sessions[id]
        override suspend fun insertMonitoringSession(session: MonitoringSessionEntity) { sessions[session.id] = session }
        override suspend fun updateMonitoringSession(session: MonitoringSessionEntity) { sessions[session.id] = session }

        override fun getValidationResultsForImprovement(improvementId: String): Flow<List<ValidationResultEntity>> = 
            MutableStateFlow(validationResults.values.filter { it.improvementId == improvementId })
        override suspend fun insertValidationResult(result: ValidationResultEntity) { validationResults[result.id] = result }
    }
}

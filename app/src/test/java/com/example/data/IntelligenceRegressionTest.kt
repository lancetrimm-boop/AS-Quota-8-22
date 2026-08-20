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
 * Integration tests for the Aura Intelligence Regression Response and Rollback Workflow.
 */
class IntelligenceRegressionTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_FullRollbackWorkflow() = runTest {
        // 1. Setup Verified Improvement in Monitoring
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Rollback Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        repository.planImplementation(improvement.id)
        val runId = repository.getImplementationRuns(improvement.id).first()[0].id
        repository.startImplementation(runId)
        repository.completeImplementation(runId)
        repository.recordVerificationResult(improvement.id, runId, true, true, true, true, true, emptyMap())
        
        val sessionId = repository.getMonitoringSessions(improvement.id).first()[0].id

        // 2. Detect Regression -> Alert Created
        repository.updateMonitoringProgress(sessionId, 20, 30.0, listOf("EV-BAD"))
        assertEquals(IntelligenceLifecycleState.REGRESSION_DETECTED, fakeDao.getImprovementById(improvement.id)?.status)
        
        val alerts = repository.getRegressionAlerts(improvement.id).first()
        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals(RegressionAlertStatus.ACTIVE, alert.status)

        // 3. Investigate
        repository.investigateRegression(alert.id)
        assertEquals(RegressionAlertStatus.INVESTIGATING, fakeDao.getRegressionAlertById(alert.id)?.status)

        // 4. Approve Rollback
        repository.approveRollback(alert.id)
        assertEquals(IntelligenceLifecycleState.ROLLBACK_RECOMMENDED, fakeDao.getImprovementById(improvement.id)?.status)
        
        val rollbackRuns = repository.getRollbackRuns(improvement.id).first()
        assertEquals(1, rollbackRuns.size)
        val rollbackRun = rollbackRuns[0]
        assertEquals(alert.id, rollbackRun.regressionId)

        // 5. Execute Rollback
        repository.executeRollback(rollbackRun.id)
        
        // 6. Verify State
        assertEquals(IntelligenceLifecycleState.ROLLED_BACK, fakeDao.getImprovementById(improvement.id)?.status)
        assertEquals(IntelligenceActionStatus.COMPLETED, fakeDao.getRollbackRunById(rollbackRun.id)?.status)
    }

    @Test
    fun test02_CorrectiveImprovementLinkage() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Corrective Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        repository.planImplementation(improvement.id)
        val runId = repository.getImplementationRuns(improvement.id).first()[0].id
        repository.completeImplementation(runId)
        repository.recordVerificationResult(improvement.id, runId, true, true, true, true, true, emptyMap())
        val sessionId = repository.getMonitoringSessions(improvement.id).first()[0].id
        repository.updateMonitoringProgress(sessionId, 20, 30.0, listOf("EV-BAD"))

        val alert = repository.getRegressionAlerts(improvement.id).first()[0]
        
        // Create Corrective Improvement
        val corrective = repository.createCorrectiveImprovement(alert.id, finding.technicalDetails)
        
        assertTrue(corrective.id.startsWith("COR-"))
        assertEquals("Corrective for ${improvement.id}", corrective.findingId)
        assertEquals(IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT, corrective.status)
    }

    /**
     * Fake DAO for Regression tests.
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
        val alerts = mutableMapOf<String, RegressionAlertEntity>()
        val rollbackRuns = mutableMapOf<String, RollbackRunEntity>()

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

        override fun getRegressionAlertsForImprovement(improvementId: String): Flow<List<RegressionAlertEntity>> = 
            MutableStateFlow(alerts.values.filter { it.improvementId == improvementId })
        override suspend fun getRegressionAlertById(id: String): RegressionAlertEntity? = alerts[id]
        override suspend fun insertRegressionAlert(alert: RegressionAlertEntity) { alerts[alert.id] = alert }
        override suspend fun updateRegressionAlert(alert: RegressionAlertEntity) { alerts[alert.id] = alert }

        override fun getRollbackRunsForImprovement(improvementId: String): Flow<List<RollbackRunEntity>> = 
            MutableStateFlow(rollbackRuns.values.filter { it.improvementId == improvementId })
        override suspend fun getRollbackRunById(id: String): RollbackRunEntity? = rollbackRuns[id]
        override suspend fun insertRollbackRun(run: RollbackRunEntity) { rollbackRuns[run.id] = run }
        override suspend fun updateRollbackRun(run: RollbackRunEntity) { rollbackRuns[run.id] = run }
    }
}

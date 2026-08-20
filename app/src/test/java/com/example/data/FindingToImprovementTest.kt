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
 * Integration tests for the Finding-to-Suggested-Improvement Pipeline.
 */
class FindingToImprovementTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_ActionableFinding_GeneratesImprovement() = runTest {
        // 1. Setup production evidence (Sufficient for actionability)
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 65.0,
            targetScore = 70.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 65.0)
            )
        )
        
        // 2. Create Finding
        val finding = repository.createFindingFromReport(report, "Performance Boost")
        
        // 3. Verify Classification
        assertEquals(FindingClassification.IMPROVEMENT_OPPORTUNITY, finding.classification)
        
        // 4. Verify Automatic Improvement Generation
        val improvements = fakeDao.improvements.values.toList()
        assertEquals(1, improvements.size)
        val imp = improvements[0]
        assertEquals(finding.id, imp.findingId)
        assertEquals(IntelligenceLifecycleState.NEEDS_REVIEW, imp.status)
        assertEquals(finding.classification, imp.classification)
        
        // 5. Verify Blueprint Artifact Generation
        val artifacts = fakeDao.artifacts.values.filter { it.improvementId == imp.id }
        assertEquals(1, artifacts.size)
        assertEquals("1.0.0", artifacts[0].version)
    }

    @Test
    fun test02_InsufficientEvidence_DoesNotGenerateImprovement() = runTest {
        // 1. Setup minimal evidence (Insufficient)
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 55.0,
            targetScore = 60.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 2, score = 55.0)
            )
        )
        
        val finding = repository.createFindingFromReport(report, "Low Data Finding")
        
        assertEquals(FindingClassification.INSUFFICIENT_EVIDENCE, finding.classification)
        assertTrue(fakeDao.improvements.isEmpty())
    }

    @Test
    fun test03_RegressionFinding_RoutesToRegressionAlert() = runTest {
        // 1. Setup regression evidence
        val report = ClosedLoopEngine.evaluate(
            baselineScore = 50.0,
            measuredScore = 40.0,
            targetScore = 60.0,
            evidenceList = listOf(
                EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 40.0)
            )
        )
        
        val finding = repository.createFindingFromReport(report, "Regression Detected")
        
        assertEquals(FindingClassification.REGRESSION, finding.classification)
        
        // Should NOT create a SuggestedImprovement
        assertTrue(fakeDao.improvements.isEmpty())
        
        // SHOULD create a RegressionAlert
        val alerts = fakeDao.alerts.values.toList()
        assertEquals(1, alerts.size)
        assertEquals(RegressionSeverity.HIGH, alerts[0].severity)
        assertEquals(RegressionAlertStatus.ACTIVE, alerts[0].status)
    }

    @Test
    fun test04_DuplicatePrevention_LinksRecurringFinding() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 65.0)
        ))
        
        // 1. Create first finding and improvement
        val finding1 = repository.createFindingFromReport(report, "Optimization A")
        assertEquals(1, fakeDao.improvements.size)
        val impId = fakeDao.improvements.values.first().id
        
        // 2. Create second equivalent finding
        val finding2 = repository.createFindingFromReport(report, "Optimization A")
        
        // 3. Verify no duplicate improvement
        assertEquals(1, fakeDao.improvements.size)
        
        // 4. Verify finding2 transitioned (linked)
        val updatedFinding2 = fakeDao.getFindingById(finding2.id)
        // Transition might happen via recordLifecycleEvent in my impl
        // Check events
        val history = fakeDao.events.filter { it.targetId == impId }
        assertTrue(history.any { it.reason?.contains(finding2.id) == true })
    }

    /**
     * Fake DAO for Pipeline tests.
     */
    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val events = mutableListOf<LifecycleEventEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()
        val alerts = mutableMapOf<String, RegressionAlertEntity>()

        override fun getAllFindings(): Flow<List<FindingEntity>> = MutableStateFlow(findings.values.toList())
        override suspend fun getFindingById(id: String): FindingEntity? = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }

        override fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>> = MutableStateFlow(improvements.values.toList())
        override suspend fun getImprovementById(id: String): SuggestedImprovementEntity? = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }

        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) { events.add(event) }
        override fun getLifecycleHistory(targetId: String): Flow<List<LifecycleEventEntity>> = 
            MutableStateFlow(events.filter { it.targetId == targetId })

        override fun getArtifactsForImprovement(improvementId: String): Flow<List<BlueprintArtifactEntity>> = 
            MutableStateFlow(artifacts.values.filter { it.improvementId == improvementId })
        override suspend fun getArtifactById(id: String): BlueprintArtifactEntity? = artifacts[id]
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) { artifacts[artifact.id] = artifact }

        override fun getRegressionAlertsForImprovement(improvementId: String): Flow<List<RegressionAlertEntity>> = 
            MutableStateFlow(alerts.values.filter { it.improvementId == improvementId })
        override suspend fun getRegressionAlertById(id: String): RegressionAlertEntity? = alerts[id]
        override suspend fun insertRegressionAlert(alert: RegressionAlertEntity) { alerts[alert.id] = alert }
        override suspend fun updateRegressionAlert(alert: RegressionAlertEntity) { alerts[alert.id] = alert }

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

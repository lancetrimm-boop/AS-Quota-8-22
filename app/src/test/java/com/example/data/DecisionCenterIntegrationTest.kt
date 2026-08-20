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

class DecisionCenterIntegrationTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_NoActionRequired_State() = runTest {
        val state = repository.getDecisionCenterState()
        assertTrue(state.attentionItems.isEmpty())
        assertTrue(state.criticalIssues.isEmpty())
        assertEquals(0L, state.lastReviewedAt)
    }

    @Test
    fun test02_Regression_Prioritization() = runTest {
        // Setup regression alert
        val alert = RegressionAlertEntity(
            id = "REG-1", improvementId = "IMP-1", runId = "RUN-1", artifactId = "ART-1", sessionId = "MON-1",
            severity = RegressionSeverity.CRITICAL, affectedMetric = "Engagement", baselineValue = 50.0,
            preRegressionValue = 50.0, currentResult = 40.0, change = -10.0, evidenceIdsJson = "[]",
            confidence = 1.0, status = RegressionAlertStatus.ACTIVE, recommendation = "Rollback"
        )
        fakeDao.insertRegressionAlert(alert)
        
        // Also add a low priority finding
        val finding = FindingEntity(
            id = "FIND-1", title = "Minor Finding", summary = "...", classification = FindingClassification.INFORMATIONAL,
            confidence = ConfidenceLevel.LOW, dateDiscovered = System.currentTimeMillis(),
            technicalDetailsJson = "{}", lifecycleState = IntelligenceLifecycleState.FINDING_DETECTED
        )
        fakeDao.insertFinding(finding)

        val state = repository.getDecisionCenterState()
        
        // Regression should be in Critical Issues
        assertEquals(1, state.criticalIssues.size)
        assertEquals("REG-1", state.criticalIssues[0].id)
        assertEquals(DecisionPriority.CRITICAL, state.criticalIssues[0].priority)
    }

    @Test
    fun test03_ReviewTracking() = runTest {
        val targetId = "IMP-1"
        
        // Mark as seen
        repository.markAsSeen(targetId)
        val metaSeen = fakeDao.getReviewMetadata(targetId)
        assertEquals(ReviewStatus.SEEN, metaSeen?.status)
        assertNotNull(metaSeen?.firstSeenTimestamp)

        // Mark as reviewed
        repository.markAsReviewed(targetId)
        val metaReviewed = fakeDao.getReviewMetadata(targetId)
        assertEquals(ReviewStatus.REVIEWED, metaReviewed?.status)
        assertNotNull(metaReviewed?.reviewedTimestamp)
    }

    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val alerts = mutableMapOf<String, RegressionAlertEntity>()
        val reviews = mutableMapOf<String, ReviewMetadataEntity>()
        val checkpoints = mutableMapOf<String, UserCheckpointEntity>()

        override fun getAllFindings(): Flow<List<FindingEntity>> = MutableStateFlow(findings.values.toList())
        override suspend fun getFindingById(id: String): FindingEntity? = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }

        override fun getAllImprovements(): Flow<List<SuggestedImprovementEntity>> = MutableStateFlow(improvements.values.toList())
        override suspend fun getImprovementById(id: String): SuggestedImprovementEntity? = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }

        override fun getRegressionAlertsForImprovement(improvementId: String): Flow<List<RegressionAlertEntity>> = 
            MutableStateFlow(alerts.values.filter { it.improvementId == improvementId })
        override suspend fun getRegressionAlertById(id: String): RegressionAlertEntity? = alerts[id]
        override suspend fun insertRegressionAlert(alert: RegressionAlertEntity) { alerts[alert.id] = alert }
        override suspend fun updateRegressionAlert(alert: RegressionAlertEntity) { alerts[alert.id] = alert }

        override suspend fun getReviewMetadata(targetId: String): ReviewMetadataEntity? = reviews[targetId]
        override suspend fun insertReviewMetadata(metadata: ReviewMetadataEntity) { reviews[metadata.targetId] = metadata }

        override suspend fun getCheckpoint(id: String): UserCheckpointEntity? = checkpoints[id]
        override suspend fun insertCheckpoint(checkpoint: UserCheckpointEntity) { checkpoints[checkpoint.checkpointId] = checkpoint }

        // Unimplemented for this test
        override fun getImprovementsForFinding(findingId: String) = MutableStateFlow<List<SuggestedImprovementEntity>>(emptyList())
        override fun getLifecycleHistory(targetId: String) = MutableStateFlow<List<LifecycleEventEntity>>(emptyList())
        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) {}
        override fun getAllActions() = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override fun getActionsForImprovement(improvementId: String) = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override suspend fun getActionById(id: String) = null
        override suspend fun insertAction(action: IntelligenceActionEntity) {}
        override suspend fun updateAction(action: IntelligenceActionEntity) {}
        override fun getArtifactsForBlueprint(blueprintId: String) = MutableStateFlow<List<BlueprintArtifactEntity>>(emptyList())
        override fun getArtifactsForImprovement(improvementId: String) = MutableStateFlow<List<BlueprintArtifactEntity>>(emptyList())
        override suspend fun getArtifactById(id: String) = null
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) {}
        override fun getAllImplementationRuns() = MutableStateFlow<List<ImplementationRunEntity>>(emptyList())
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
        override fun getRollbackRunsForImprovement(improvementId: String) = MutableStateFlow<List<RollbackRunEntity>>(emptyList())
        override suspend fun getRollbackRunById(id: String) = null
        override suspend fun insertRollbackRun(run: RollbackRunEntity) {}
        override suspend fun updateRollbackRun(run: RollbackRunEntity) {}
    }
}

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

class EndToEndLifecycleValidationTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test_01_Full_Success_Lifecycle() = runTest {
        println("STARTING TEST: Full Success Lifecycle")

        // 1. SYSTEM OBSERVES & FINDING CREATED
        val evidenceId = "EV-001"
        val evidence = EvidenceEntity(evidenceId, "PRODUCTION", 100, 60.0, 0.9, "Telemetry", System.currentTimeMillis())
        fakeDao.storedEvidence[evidenceId] = evidence
        
        val report = repository.generateClosedLoopReport()
        val finding = repository.createFindingFromReport(report, "Personalization Optimization")
        
        assertEquals(IntelligenceLifecycleState.FINDING_DETECTED, finding.lifecycleState)
        assertEquals(1, fakeDao.findings.size)
        println("PASS: Finding Created")

        // 2. SUGGESTED IMPROVEMENT CREATED (Automated Pipeline)
        val improvements = fakeDao.getAllImprovements().first()
        assertEquals(1, improvements.size)
        val improvement = improvements[0].toDomain()
        assertEquals(IntelligenceLifecycleState.NEEDS_REVIEW, improvement.status)
        println("PASS: Suggested Improvement Created")

        // 3. USER REVIEWS & APPROVES
        val artifact = BlueprintArtifact(
            blueprintId = improvement.technicalDetails?.id ?: "BP-001",
            strategyBlueprint = improvement.technicalDetails!!,
            lifecycleState = BlueprintLifecycleState.PROPOSED
        )
        repository.approveImprovement(improvement.id, artifact, "User Approved")
        
        val approvedImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.APPROVED, approvedImp.status)
        println("PASS: User Approved & Version Locked")

        // 4. IMPLEMENTATION READY & HANDOFF
        repository.planImplementation(improvement.id)
        val readyImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_PLANNED, readyImp.status)
        
        val implementationPackage = repository.getImplementationPackage(improvement.id)
        assertNotNull(implementationPackage.androidStudioPrompt)
        assertTrue(implementationPackage.androidStudioPrompt!!.contains("APPROVED IMPLEMENTATION CONTRACT"))
        println("PASS: Implementation Package & Prompt Generated")

        // 5. IMPLEMENTATION EXECUTED & SCOPE VERIFIED
        val runs = fakeDao.getAllImplementationRuns().first()
        val runId = runs[0].id
        repository.startImplementation(runId)
        
        // Match approved scope
        val approvedFiles = implementationPackage.filesAffected
        repository.completeImplementation(runId, "Completed by Builder", approvedFiles)
        
        val completedImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE, completedImp.status)
        println("PASS: Implementation Executed (Scope Match)")

        // 6. VERIFICATION PERFORMED
        repository.recordVerificationResult(
            improvement.id, runId,
            buildPassed = true, testsPassed = true, regressionPassed = true,
            dbIntegrityPassed = true, scopeCompliant = true,
            acceptanceCriteriaResults = mapOf("Metric" to "Passed")
        )
        
        val verifiedImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.MONITORING, verifiedImp.status)
        println("PASS: Verification Passed")

        // 7. MONITORING & VALIDATION
        val sessions = fakeDao.getAllMonitoringSessions().first()
        val sessionId = sessions[0].id
        repository.updateMonitoringProgress(sessionId, 5000, 65.0, emptyList())
        
        val validatedImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.VALIDATED, validatedImp.status)
        println("PASS: Monitoring Completed & Validated")

        // 8. MASTER REPORT UPDATED
        val masterReport = repository.generateMasterReport(0L)
        assertEquals(1, masterReport.executiveSummary.metrics.validated)
        println("PASS: Master Intelligence Report Updated")
    }

    @Test
    fun test_02_FailurePath_Implementation_Failure() = runTest {
        println("STARTING TEST: Implementation Failure Path")
        val improvement = setupApprovedImprovement()
        repository.planImplementation(improvement.id)
        val runs = fakeDao.getAllImplementationRuns().first()
        val runId = runs[0].id
        
        repository.startImplementation(runId)
        repository.failImplementation(runId, "Syntax Error: missing semicolon")
        
        val failedImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_FAILED, failedImp.status)
        
        // Verify Attention Item
        val attentionItems = fakeDao.getAllAttentionItems().first()
        assertTrue(attentionItems.any { it.attentionType == AttentionType.EXECUTION_FAILURE })
        println("PASS: Implementation Failure Handled")
    }

    @Test
    fun test_03_FailurePath_Scope_Deviation() = runTest {
        println("STARTING TEST: Scope Deviation Path")
        val improvement = setupApprovedImprovement()
        repository.planImplementation(improvement.id)
        val runs = fakeDao.getAllImplementationRuns().first()
        val runId = runs[0].id
        
        repository.startImplementation(runId)
        
        // Deviation: modified extra file
        val unexpectedFiles = listOf("com/example/data/TasteDna.kt", "com/example/data/Unauthorized.kt")
        repository.completeImplementation(runId, "Modified extra stuff", unexpectedFiles)
        
        val devImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.DEVIATION_DETECTED, devImp.status)
        
        // Resolve: Reject & Rework
        repository.resolveDeviation(improvement.id, false)
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_PLANNED, fakeDao.getImprovementById(improvement.id)!!.status)
        println("PASS: Scope Deviation Detected & Rejected")
    }

    @Test
    fun test_04_FailurePath_Regression_Detected() = runTest {
        println("STARTING TEST: Regression Detection Path")
        val improvement = setupApprovedImprovement()
        repository.planImplementation(improvement.id)
        val runs = fakeDao.getAllImplementationRuns().first()
        val runId = runs[0].id
        repository.startImplementation(runId)
        repository.completeImplementation(runId, "Done", emptyList())
        repository.recordVerificationResult(improvement.id, runId, true, true, true, true, true, emptyMap())
        
        val sessions = fakeDao.getAllMonitoringSessions().first()
        val sessionId = sessions[0].id
        // Trigger regression (baseline 50.0, current 40.0)
        repository.updateMonitoringProgress(sessionId, 100, 40.0, emptyList())
        
        val regImp = fakeDao.getImprovementById(improvement.id)!!.toDomain()
        assertEquals(IntelligenceLifecycleState.REGRESSION_DETECTED, regImp.status)
        
        val alerts = fakeDao.getRegressionAlertsForImprovement(improvement.id).first()
        assertTrue(alerts.isNotEmpty())
        println("PASS: Regression Detected in Monitoring")
    }

    private suspend fun setupApprovedImprovement(): SuggestedImprovement {
        val evidenceId = "EV-TEST"
        fakeDao.storedEvidence[evidenceId] = EvidenceEntity(evidenceId, "PRODUCTION", 100, 60.0, 0.9, "Test", System.currentTimeMillis())
        val report = repository.generateClosedLoopReport()
        repository.createFindingFromReport(report, "Test Case")
        val entities = fakeDao.getAllImprovements().first()
        val improvement = entities[0].toDomain()
        val artifact = BlueprintArtifact(
            blueprintId = improvement.technicalDetails?.id ?: "BP-TEST", 
            strategyBlueprint = improvement.technicalDetails!!, 
            lifecycleState = BlueprintLifecycleState.PROPOSED
        )
        repository.approveImprovement(improvement.id, artifact, "System Test")
        return fakeDao.getImprovementById(improvement.id)!!.toDomain()
    }

    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()
        val implementationRuns = mutableMapOf<String, ImplementationRunEntity>()
        val verificationResults = mutableMapOf<String, VerificationResultEntity>()
        val monitoringSessions = mutableMapOf<String, MonitoringSessionEntity>()
        val validationResults = mutableMapOf<String, ValidationResultEntity>()
        val regressionAlerts = mutableMapOf<String, RegressionAlertEntity>()
        val attentionItems = mutableMapOf<String, AttentionItemEntity>()
        val history = mutableListOf<LifecycleEventEntity>()
        val storedEvidence = mutableMapOf<String, EvidenceEntity>()
        val evidenceSnapshots = mutableMapOf<String, EvidenceSnapshotEntity>()
        val checkpoints = mutableMapOf<String, UserCheckpointEntity>()

        override fun getAllFindings() = MutableStateFlow(findings.values.toList())
        override suspend fun getFindingById(id: String) = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }

        override fun getAllImprovements() = MutableStateFlow(improvements.values.toList())
        override suspend fun getImprovementById(id: String) = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }

        override fun getArtifactsForImprovement(improvementId: String) = MutableStateFlow(artifacts.values.filter { it.improvementId == improvementId })
        override suspend fun getArtifactById(id: String) = artifacts[id]
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) { artifacts[artifact.id] = artifact }

        override fun getAllImplementationRuns() = MutableStateFlow(implementationRuns.values.toList())
        override fun getImplementationRunsForImprovement(improvementId: String) = MutableStateFlow(implementationRuns.values.filter { it.improvementId == improvementId })
        override suspend fun getImplementationRunById(id: String) = implementationRuns[id]
        override suspend fun insertImplementationRun(run: ImplementationRunEntity) { implementationRuns[run.id] = run }
        override suspend fun updateImplementationRun(run: ImplementationRunEntity) { implementationRuns[run.id] = run }

        override fun getVerificationResultsForImprovement(improvementId: String) = MutableStateFlow(verificationResults.values.filter { it.improvementId == improvementId })
        override suspend fun insertVerificationResult(result: VerificationResultEntity) { verificationResults[result.id] = result }

        override fun getAllMonitoringSessions() = MutableStateFlow(monitoringSessions.values.toList())
        override fun getMonitoringSessionsForImprovement(improvementId: String) = MutableStateFlow(monitoringSessions.values.filter { it.improvementId == improvementId })
        override suspend fun getMonitoringSessionById(id: String) = monitoringSessions[id]
        override suspend fun insertMonitoringSession(session: MonitoringSessionEntity) { monitoringSessions[session.id] = session }
        override suspend fun updateMonitoringSession(session: MonitoringSessionEntity) { monitoringSessions[session.id] = session }

        override fun getValidationResultsForImprovement(improvementId: String) = MutableStateFlow(validationResults.values.filter { it.improvementId == improvementId })
        override suspend fun insertValidationResult(result: ValidationResultEntity) { validationResults[result.id] = result }

        override fun getAllRegressionAlerts() = MutableStateFlow(regressionAlerts.values.toList())
        override fun getRegressionAlertsForImprovement(improvementId: String) = MutableStateFlow(regressionAlerts.values.filter { it.improvementId == improvementId })
        override suspend fun getRegressionAlertById(id: String) = regressionAlerts[id]
        override suspend fun insertRegressionAlert(alert: RegressionAlertEntity) { regressionAlerts[alert.id] = alert }
        override suspend fun updateRegressionAlert(alert: RegressionAlertEntity) { regressionAlerts[alert.id] = alert }

        override fun getAllAttentionItems() = MutableStateFlow(attentionItems.values.toList())
        override fun getActionableAttentionItems() = MutableStateFlow(attentionItems.values.filter { it.requiresAction })
        override suspend fun insertAttentionItem(item: AttentionItemEntity) { attentionItems[item.id] = item }
        override suspend fun updateAttentionItem(item: AttentionItemEntity) { attentionItems[item.id] = item }
        override suspend fun deleteActiveAttentionItemByDeduplicationKey(key: String) { attentionItems.entries.removeIf { it.value.deduplicationKey == key && it.value.status == AttentionStatus.NEW } }

        override fun getLifecycleHistory(targetId: String) = MutableStateFlow(history.filter { it.targetId == targetId })
        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) { history.add(event) }

        override fun getAllStoredEvidence() = MutableStateFlow(storedEvidence.values.toList())
        override suspend fun getEvidenceSnapshot(improvementId: String) = evidenceSnapshots[improvementId]
        override suspend fun insertEvidenceSnapshot(snapshot: EvidenceSnapshotEntity) { evidenceSnapshots[snapshot.improvementId] = snapshot }

        override suspend fun getCheckpoint(id: String) = checkpoints[id]
        override fun observeCheckpoint(id: String) = MutableStateFlow(checkpoints[id])
        override suspend fun insertCheckpoint(checkpoint: UserCheckpointEntity) { checkpoints[checkpoint.checkpointId] = checkpoint }

        override fun getPendingEvents() = MutableStateFlow<List<IntelligenceEventEntity>>(emptyList())
        override suspend fun insertEvent(event: IntelligenceEventEntity) {}
        override suspend fun updateEvent(event: IntelligenceEventEntity) {}
        override fun getAllIntelligenceEvents() = MutableStateFlow<List<IntelligenceEventEntity>>(emptyList())
        override fun getAllSavedReports() = MutableStateFlow<List<SavedIntelligenceReportEntity>>(emptyList())
        override suspend fun getSavedReportById(id: String) = null
        override suspend fun insertSavedReport(report: SavedIntelligenceReportEntity) {}
        override fun getAuditHistory(targetId: String) = MutableStateFlow<List<IntegrityAuditEntity>>(emptyList())
        override suspend fun insertAudit(audit: IntegrityAuditEntity) {}
        override fun getArtifactsForBlueprint(blueprintId: String) = MutableStateFlow<List<BlueprintArtifactEntity>>(emptyList())
        override fun getImprovementsForFinding(findingId: String) = MutableStateFlow<List<SuggestedImprovementEntity>>(emptyList())
        override fun getAllActions() = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override fun getActionsForImprovement(improvementId: String) = MutableStateFlow<List<IntelligenceActionEntity>>(emptyList())
        override suspend fun getActionById(id: String) = null
        override suspend fun insertAction(action: IntelligenceActionEntity) {}
        override suspend fun updateAction(action: IntelligenceActionEntity) {}
        override fun getVerificationResultsForRun(runId: String) = MutableStateFlow<List<VerificationResultEntity>>(emptyList())
        override fun getRollbackRunsForImprovement(improvementId: String) = MutableStateFlow<List<RollbackRunEntity>>(emptyList())
        override suspend fun getRollbackRunById(id: String) = null
        override suspend fun insertRollbackRun(run: RollbackRunEntity) {}
        override suspend fun updateRollbackRun(run: RollbackRunEntity) {}
        override suspend fun getReviewMetadata(targetId: String) = null
        override suspend fun insertReviewMetadata(metadata: ReviewMetadataEntity) {}
    }
}

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
 * End-to-End integration tests for the Aura Intelligence Implementation and Verification Workflow.
 */
class IntelligenceWorkflowTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_FullSuccessWorkflow() = runTest {
        // 1. Setup Approved Improvement
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 70.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 65.0)
        ))
        val finding = repository.createFindingFromReport(report, "Success Path Test")
        val blueprint = finding.technicalDetails
        val improvement = repository.proposeImprovement(finding.id, blueprint)
        
        val artifact = BlueprintArtifact(
            blueprintId = blueprint.id,
            strategyBlueprint = blueprint
        )
        repository.approveImprovement(improvement.id, artifact)
        
        // 2. Plan Implementation
        repository.planImplementation(improvement.id)
        val runs = repository.getImplementationRuns(improvement.id).first()
        assertEquals(1, runs.size)
        val run = runs[0]
        assertEquals(IntelligenceActionStatus.PENDING, run.status)
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_PLANNED, fakeDao.getImprovementById(improvement.id)?.status)

        // 3. Start Implementation
        repository.startImplementation(run.id)
        assertEquals(IntelligenceActionStatus.IN_PROGRESS, fakeDao.getImplementationRunById(run.id)?.status)
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS, fakeDao.getImprovementById(improvement.id)?.status)

        // 4. Complete Implementation
        repository.completeImplementation(run.id, "Done", listOf("AISkipEngine.kt"))
        assertEquals(IntelligenceActionStatus.COMPLETED, fakeDao.getImplementationRunById(run.id)?.status)
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE, fakeDao.getImprovementById(improvement.id)?.status)

        // 5. Start Verification
        repository.startVerification(improvement.id, run.id)
        assertEquals(IntelligenceLifecycleState.VERIFICATION_IN_PROGRESS, fakeDao.getImprovementById(improvement.id)?.status)

        // 6. Record Success
        repository.recordVerificationResult(
            improvementId = improvement.id,
            runId = run.id,
            buildPassed = true,
            testsPassed = true,
            regressionPassed = true,
            dbIntegrityPassed = true,
            scopeCompliant = true,
            acceptanceCriteriaResults = mapOf("Metric Increase" to "Passed")
        )

        // 7. Verify Final State
        val finalImp = fakeDao.getImprovementById(improvement.id)
        assertEquals(IntelligenceLifecycleState.MONITORING, finalImp?.status)
        
        val results = repository.getVerificationResults(improvement.id).first()
        assertTrue(results[0].overallPassed)
    }

    @Test
    fun test02_VerificationFailure_BlocksMonitoring() = runTest {
        // Setup up to implementation complete
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Failure Path Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        repository.planImplementation(improvement.id)
        val run = repository.getImplementationRuns(improvement.id).first()[0]
        repository.startImplementation(run.id)
        repository.completeImplementation(run.id)
        repository.startVerification(improvement.id, run.id)

        // Record Failure
        repository.recordVerificationResult(
            improvementId = improvement.id,
            runId = run.id,
            buildPassed = true,
            testsPassed = false, // Test failed!
            regressionPassed = true,
            dbIntegrityPassed = true,
            scopeCompliant = true,
            acceptanceCriteriaResults = mapOf("Criteria 1" to "Failed")
        )

        val finalImp = fakeDao.getImprovementById(improvement.id)
        assertEquals(IntelligenceLifecycleState.VERIFICATION_FAILED, finalImp?.status)
        assertNotEquals(IntelligenceLifecycleState.MONITORING, finalImp?.status)
    }

    @Test
    fun test03_ScopeDeviation_BlocksVerification() = runTest {
        // Setup
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Deviation Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        repository.planImplementation(improvement.id)
        val run = repository.getImplementationRuns(improvement.id).first()[0]
        repository.startImplementation(run.id)

        // Complete with unauthorized file
        repository.completeImplementation(run.id, "Changed unauthorized file", listOf("UnauthorizedFile.kt"))
        
        val updatedRun = fakeDao.getImplementationRunById(run.id)
        assertTrue(updatedRun?.deviationDetected == true)
        assertNotNull(updatedRun?.deviationDetails)

        // Try to start verification - should fail
        try {
            repository.startVerification(improvement.id, run.id)
            fail("Should have thrown IllegalStateException due to scope deviation")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Scope deviation") == true)
        }
        
        // Improvement state should NOT have transitioned to VERIFICATION_IN_PROGRESS
        assertEquals(IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE, fakeDao.getImprovementById(improvement.id)?.status)
    }

    /**
     * Re-using and extending FakeIntelligenceDao for workflow testing.
     */
    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val events = mutableListOf<LifecycleEventEntity>()
        val actions = mutableMapOf<String, IntelligenceActionEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()
        val runs = mutableMapOf<String, ImplementationRunEntity>()
        val results = mutableMapOf<String, VerificationResultEntity>()

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
            MutableStateFlow(results.values.filter { it.improvementId == improvementId }.sortedByDescending { it.timestamp })
        override fun getVerificationResultsForRun(runId: String): Flow<List<VerificationResultEntity>> = 
            MutableStateFlow(results.values.filter { it.runId == runId })
        override suspend fun insertVerificationResult(result: VerificationResultEntity) { results[result.id] = result }
    }
}

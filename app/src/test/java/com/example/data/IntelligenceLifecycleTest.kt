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
 * Unit tests for the Aura Intelligence Lifecycle and Repository.
 */
class IntelligenceLifecycleTest {

    private lateinit var repository: IntelligenceRepository
    private lateinit var fakeDao: FakeIntelligenceDao

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
    }

    @Test
    fun test01_CreateFinding_PersistsCorrectData() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 60.0)
        ))
        
        val finding = repository.createFindingFromReport(report, "Performance Optimization")
        
        assertEquals("Performance Optimization", finding.title)
        assertEquals(IntelligenceLifecycleState.FINDING_DETECTED, finding.lifecycleState)
        assertEquals(FindingClassification.IMPROVEMENT_OPPORTUNITY, finding.classification)
        assertTrue(finding.id.startsWith("FINDING-"))
        
        val persisted = fakeDao.findings[finding.id]
        assertNotNull(persisted)
        assertEquals(finding.title, persisted?.title)
        assertEquals(IntelligenceLifecycleState.FINDING_DETECTED, persisted?.lifecycleState)
    }

    @Test
    fun test02_ProposeImprovement_PersistsAndTransitionsFinding() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 60.0)
        ))
        val finding = repository.createFindingFromReport(report, "Test Finding")
        
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Optimize Weights", "Desc", report)
        val improvement = repository.proposeImprovement(finding.id, blueprint)
        
        assertTrue(improvement.id.startsWith("IMP-"))
        assertEquals(finding.id, improvement.findingId)
        assertEquals(IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT, improvement.status)
        
        val updatedFinding = fakeDao.findings[finding.id]
        assertEquals(IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT, updatedFinding?.lifecycleState)
        
        val history = fakeDao.events.filter { it.targetId == finding.id }
        assertTrue(history.any { it.toState == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT })
    }

    @Test(expected = IllegalStateException::class)
    fun test03_InvalidTransition_IsRejected() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Test")
        
        // Skip required intermediate states and go straight to APPROVED
        repository.transitionFindingState(finding.id, IntelligenceLifecycleState.APPROVED)
    }

    @Test
    fun test04_ValidLifecycleFlow() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Test")
        
        repository.transitionFindingState(finding.id, IntelligenceLifecycleState.SYSTEM_ANALYSIS)
        assertEquals(IntelligenceLifecycleState.SYSTEM_ANALYSIS, fakeDao.findings[finding.id]?.lifecycleState)
        
        repository.transitionFindingState(finding.id, IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT)
        assertEquals(IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT, fakeDao.findings[finding.id]?.lifecycleState)
        
        repository.transitionFindingState(finding.id, IntelligenceLifecycleState.NEEDS_REVIEW)
        assertEquals(IntelligenceLifecycleState.NEEDS_REVIEW, fakeDao.findings[finding.id]?.lifecycleState)
        
        repository.transitionFindingState(finding.id, IntelligenceLifecycleState.APPROVED, "Manager approved")
        assertEquals(IntelligenceLifecycleState.APPROVED, fakeDao.findings[finding.id]?.lifecycleState)
    }

    @Test
    fun test05_BackwardCompatibility_StrategyBlueprintSerialization() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 10, score = 60.0)
        ))
        val blueprint = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        
        val finding = repository.createFindingFromReport(report, "Title")
        val persisted = fakeDao.findings[finding.id]!!
        
        // Verify Moshi can deserialize the technicalDetailsJson back into StrategyBlueprint
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(StrategyBlueprint::class.java)
        val deserializedBlueprint = adapter.fromJson(persisted.technicalDetailsJson)
            
        assertNotNull(deserializedBlueprint)
        assertEquals(blueprint.identity.blueprintId, deserializedBlueprint?.identity?.blueprintId)
        assertEquals(blueprint.evidence.productionCount, deserializedBlueprint?.evidence?.productionCount)
    }

    @Test
    fun test06_ImmutableLifecycleHistory() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Test History")
        
        repository.transitionFindingState(finding.id, IntelligenceLifecycleState.SYSTEM_ANALYSIS, "Analyzing system impact")
        
        val history = fakeDao.events.filter { it.targetId == finding.id }
        assertEquals(2, history.size)
        
        assertEquals(IntelligenceLifecycleState.FINDING_DETECTED, history[0].toState)
        assertNull(history[0].fromState)
        
        assertEquals(IntelligenceLifecycleState.SYSTEM_ANALYSIS, history[1].toState)
        assertEquals(IntelligenceLifecycleState.FINDING_DETECTED, history[1].fromState)
        assertEquals("Analyzing system impact", history[1].reason)
    }

    @Test
    fun test07_ApprovedImprovement_CreatesVersionedArtifact() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 65.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Boost")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        val artifact = BlueprintArtifact(
            blueprintId = finding.technicalDetails.id,
            strategyBlueprint = finding.technicalDetails
        )
        
        repository.approveImprovement(improvement.id, artifact)
        
        val artifacts = repository.getArtifactsForImprovement(improvement.id).first()
        assertEquals(1, artifacts.size)
        assertEquals(finding.technicalDetails.id, artifacts[0].blueprintId)
        
        val updatedImp = fakeDao.getImprovementById(improvement.id)
        assertEquals(IntelligenceLifecycleState.APPROVED, updatedImp?.status)
    }

    @Test
    fun test08_ScopeMismatch_RequiresReapproval() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 65.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Initial")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        repository.approveImprovement(improvement.id, BlueprintArtifact(strategyBlueprint = finding.technicalDetails))
        
        // Create a new version with an EXTRA modification not in the original proposal
        val modifiedBlueprint = finding.technicalDetails.copy(
            proposedModifications = finding.technicalDetails.proposedModifications + ProposedModification(
                component = "Unauthorized", parameter = "x", currentValue = "0", proposedValue = "1", delta = "1", reason = "hacker", supportingEvidence = "none", expectedEffect = "bad", confidence = 1.0, modificationType = ModificationType.EXPERIMENTAL_CHANGE
            )
        )
        
        repository.createBlueprintArtifactVersion(improvement.id, BlueprintArtifact(strategyBlueprint = modifiedBlueprint))
        
        val updatedImp = fakeDao.getImprovementById(improvement.id)
        assertEquals(IntelligenceLifecycleState.NEEDS_REVIEW, updatedImp?.status)
        
        val history = fakeDao.events.filter { it.targetId == improvement.id }
        assertTrue(history.last().reason?.contains("Technical scope mismatch") == true)
    }

    /**
     * Fake DAO implementation for Unit Testing.
     */
    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val events = mutableListOf<LifecycleEventEntity>()
        val actions = mutableMapOf<String, IntelligenceActionEntity>()
        val artifacts = mutableMapOf<String, BlueprintArtifactEntity>()

        override fun getAllFindings(): Flow<List<FindingEntity>> = MutableStateFlow(findings.values.toList().sortedByDescending { it.dateDiscovered })
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
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) { artifacts[artifact.id] = artifact }
    }
}

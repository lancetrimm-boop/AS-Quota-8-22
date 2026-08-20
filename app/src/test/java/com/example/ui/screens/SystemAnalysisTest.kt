package com.example.ui.screens

import com.example.data.*
import com.example.data.db.*
import com.example.data.blueprint.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for System Analysis logic in IntelligenceViewModel.
 */
class SystemAnalysisTest {

    private lateinit var viewModel: IntelligenceViewModel
    private lateinit var fakeDao: FakeIntelligenceDao
    private lateinit var repository: IntelligenceRepository

    @Before
    fun setup() {
        fakeDao = FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
        viewModel = IntelligenceViewModel(repository)
    }

    @Test
    fun test01_ProblemRequiresAction() = runTest {
        // 1. Create a finding with a regression
        val report = ClosedLoopEngine.evaluate(50.0, 35.0, 60.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 50, score = 35.0)
        ))
        repository.createFindingFromReport(report, "Regression Detected")
        
        val state = viewModel.state.first { !it.isLoading }
        val analysis = state.systemAnalysis
        
        assertNotNull(analysis)
        assertEquals(ActionStatus.ACTION_REQUIRED, analysis?.actionStatus)
        assertTrue(analysis?.actionRequired == true)
        assertTrue(analysis?.whatsNotWorking?.any { it.contains("regression", ignoreCase = true) } == true)
    }

    @Test
    fun test02_OpportunityReviewRecommended() = runTest {
        // 1. Create a finding with improvement
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 65.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 65.0)
        ))
        repository.createFindingFromReport(report, "Performance Boost")
        
        val state = viewModel.state.first { !it.isLoading }
        val analysis = state.systemAnalysis
        
        assertNotNull(analysis)
        assertEquals(ActionStatus.REVIEW_RECOMMENDED, analysis?.actionStatus)
        assertTrue(analysis?.whatsWorking?.any { it.contains("positive", ignoreCase = true) || it.contains("increase", ignoreCase = true) } == true)
    }

    @Test
    fun test03_LowConfidenceMoreEvidenceNeeded() = runTest {
        // 1. Create a finding with very few samples
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 2, score = 60.0)
        ))
        repository.createFindingFromReport(report, "Potential Improvement")
        
        val state = viewModel.state.first { !it.isLoading }
        val analysis = state.systemAnalysis
        
        assertNotNull(analysis)
        assertEquals(ActionStatus.MORE_EVIDENCE_NEEDED, analysis?.actionStatus)
        assertTrue(analysis?.confidenceExplanation?.contains("limited sample size") == true)
    }

    @Test
    fun test04_NoActionRequired() = runTest {
        // 1. Create an informational finding (no change)
        val report = ClosedLoopEngine.evaluate(50.0, 50.0, 50.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 50, score = 50.0)
        ))
        repository.createFindingFromReport(report, "System Stable")
        
        val state = viewModel.state.first { !it.isLoading }
        val analysis = state.systemAnalysis
        
        assertNotNull(analysis)
        assertEquals(ActionStatus.NO_ACTION_REQUIRED, analysis?.actionStatus)
        assertFalse(analysis?.actionRequired == true)
    }

    /**
     * Re-using the fake DAO from the repository test.
     */
    class FakeIntelligenceDao : StubIntelligenceDao() {
        val findings = mutableMapOf<String, FindingEntity>()
        val improvements = mutableMapOf<String, SuggestedImprovementEntity>()
        val events = mutableListOf<LifecycleEventEntity>()
        val actions = mutableMapOf<String, IntelligenceActionEntity>()

        override fun getAllFindings() = kotlinx.coroutines.flow.MutableStateFlow(findings.values.toList().sortedByDescending { it.dateDiscovered })
        override suspend fun getFindingById(id: String) = findings[id]
        override suspend fun insertFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override suspend fun updateFinding(finding: FindingEntity) { findings[finding.id] = finding }
        override fun getAllImprovements() = kotlinx.coroutines.flow.MutableStateFlow(improvements.values.toList())
        override fun getImprovementsForFinding(findingId: String) = kotlinx.coroutines.flow.MutableStateFlow(improvements.values.filter { it.findingId == findingId })
        override suspend fun getImprovementById(id: String) = improvements[id]
        override suspend fun insertImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override suspend fun updateImprovement(improvement: SuggestedImprovementEntity) { improvements[improvement.id] = improvement }
        override fun getLifecycleHistory(targetId: String) = kotlinx.coroutines.flow.MutableStateFlow(events.filter { it.targetId == targetId })
        override suspend fun insertLifecycleEvent(event: LifecycleEventEntity) { events.add(event) }
        override fun getAllActions() = kotlinx.coroutines.flow.MutableStateFlow(actions.values.toList())
        override fun getActionsForImprovement(improvementId: String) = kotlinx.coroutines.flow.MutableStateFlow(actions.values.filter { it.improvementId == improvementId })
        override suspend fun getActionById(id: String) = actions[id]
        override suspend fun insertAction(action: IntelligenceActionEntity) { actions[action.id] = action }
        override suspend fun updateAction(action: IntelligenceActionEntity) { actions[action.id] = action }
        override fun getArtifactsForBlueprint(blueprintId: String) = kotlinx.coroutines.flow.MutableStateFlow(emptyList<BlueprintArtifactEntity>())
        override suspend fun insertArtifact(artifact: BlueprintArtifactEntity) {}
    }
}

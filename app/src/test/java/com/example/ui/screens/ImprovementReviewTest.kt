package com.example.ui.screens

import com.example.data.*
import com.example.data.db.*
import com.example.data.blueprint.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the Suggested Improvement Review Workflow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ImprovementReviewTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: IntelligenceViewModel
    private lateinit var fakeDao: SystemAnalysisTest.FakeIntelligenceDao
    private lateinit var repository: IntelligenceRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = SystemAnalysisTest.FakeIntelligenceDao()
        repository = IntelligenceRepository(fakeDao)
        viewModel = IntelligenceViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test01_ApproveImprovement_TransitionsStateAndCreatesArtifact() = runTest {
        // 1. Create a finding and improvement
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 65.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 100, score = 65.0)
        ))
        val finding = repository.createFindingFromReport(report, "Boost")
        val blueprint = finding.technicalDetails
        val improvement = repository.proposeImprovement(finding.id, blueprint)
        
        viewModel.loadImprovement(improvement.id)
        
        // 2. Approve
        viewModel.approveImprovement(improvement.id, "Validated by human")
        
        val updatedImp = fakeDao.improvements[improvement.id]
        assertEquals(IntelligenceLifecycleState.APPROVED, updatedImp?.status)
        assertNotNull(updatedImp?.blueprintArtifactId)
        
        val updatedFinding = fakeDao.findings[finding.id]
        assertEquals(IntelligenceLifecycleState.APPROVED, updatedFinding?.lifecycleState)
        
        val events = fakeDao.events.filter { it.targetId == improvement.id }
        assertTrue(events.any { it.toState == IntelligenceLifecycleState.APPROVED && it.reason == "Validated by human" })
    }

    @Test
    fun test02_RejectImprovement_PersistsReason() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 60.0, 70.0, emptyList())
        val finding = repository.createFindingFromReport(report, "Test")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        viewModel.rejectImprovement(improvement.id, "Too risky for now")
        
        val updatedImp = fakeDao.improvements[improvement.id]
        assertEquals(IntelligenceLifecycleState.REJECTED, updatedImp?.status)
        
        val events = fakeDao.events.filter { it.targetId == improvement.id }
        assertEquals("Too risky for now", events.last().reason)
    }

    @Test
    fun test03_AuraExplanation_UsesActualEvidence() = runTest {
        val report = ClosedLoopEngine.evaluate(50.0, 65.0, 65.0, listOf(
            EvidenceRecord(tier = EvidenceTier.PRODUCTION, sampleCount = 204, score = 65.0)
        ))
        val finding = repository.createFindingFromReport(report, "Personalization Calibration")
        val improvement = repository.proposeImprovement(finding.id, finding.technicalDetails)
        
        viewModel.askAuraToExplain(improvement)
        
        val explanation = viewModel.explanation.first { it != null }
        assertNotNull(explanation)
        val explanationText = explanation!!.reasoning.joinToString() + " " + explanation.evidenceStrength
        assertTrue(explanationText.contains("204 production samples"))
        assertTrue(explanationText.contains("high", ignoreCase = true))
    }
}

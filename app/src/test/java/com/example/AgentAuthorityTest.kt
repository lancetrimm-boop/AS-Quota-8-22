package com.example

import com.example.data.*
import com.example.data.blueprint.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentAuthorityTest {

    @Before
    fun setup() {
        AgentAuthorityEnforcer.registerContract(AgentContract(
            agentId = "AISkipEngine",
            writePermissions = listOf("AISkipEngine"),
            prohibitedActions = listOf("AuraDatabase")
        ))
    }

    @Test
    fun testAuthorizedProposal() {
        val blueprint = createTestBlueprint("AISkipEngine", "skip_deduction_weight")
        val result = AgentAuthorityEnforcer.validate(blueprint)
        assertEquals(AuthorityDecision.AUTHORIZED, result.decision)
    }

    @Test
    fun testMissingPermission() {
        // Agent is AISkipEngine but tries to modify PairwiseSystem
        val blueprint = createTestBlueprint("AISkipEngine", "pairwise_weight", component = "PairwiseSystem")
        val result = AgentAuthorityEnforcer.validate(blueprint)
        assertEquals(AuthorityDecision.MISSING_PERMISSION, result.decision)
        assertTrue(result.reason.contains("lacks write permission"))
    }

    @Test
    fun testProhibitedAction() {
        val blueprint = createTestBlueprint("AISkipEngine", "db_param", component = "AuraDatabase")
        val result = AgentAuthorityEnforcer.validate(blueprint)
        assertEquals(AuthorityDecision.PROHIBITED_ACTION, result.decision)
        assertTrue(result.reason.contains("explicitly prohibited"))
    }

    @Test
    fun testInsufficientConfidence() {
        val blueprint = createTestBlueprint("AISkipEngine", "param", confidence = 0.5) // Contract default is 0.7
        val result = AgentAuthorityEnforcer.validate(blueprint)
        assertEquals(AuthorityDecision.REJECTED, result.decision)
        assertTrue(result.reason.contains("confidence"))
    }

    private fun createTestBlueprint(
        affectedComponent: String,
        parameter: String,
        component: String = affectedComponent,
        confidence: Double = 0.9
    ): StrategyBlueprint {
        val report = ClosedLoopEngine.evaluate(50.0, 50.0, 50.0, emptyList())
        val bp = StrategyBlueprintGenerator.generateBlueprint("Title", "Desc", report)
        return bp.copy(
            diagnosis = bp.diagnosis.copy(
                affectedComponent = affectedComponent,
                diagnosticConfidence = confidence
            ),
            proposedModifications = listOf(
                ProposedModification(
                    component = component,
                    parameter = parameter,
                    currentValue = "1.0",
                    proposedValue = "1.1",
                    delta = "0.1",
                    reason = "Test",
                    supportingEvidence = "Evidence",
                    expectedEffect = "Effect",
                    confidence = confidence,
                    modificationType = ModificationType.EXPERIMENTAL_CHANGE
                )
            )
        )
    }
}

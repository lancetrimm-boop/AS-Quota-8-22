package com.example.data.blueprint

import com.example.data.StrategyBlueprint

/**
 * Runtime enforcement engine for Agent Contracts.
 * Prevents agents from proposing unauthorized or prohibited actions.
 */
object AgentAuthorityEnforcer {

    private val contracts = mutableMapOf<String, AgentContract>()

    /**
     * Registers an agent contract for enforcement.
     */
    fun registerContract(contract: AgentContract) {
        contracts[contract.agentId] = contract
    }

    /**
     * Validates a complete Strategy Blueprint against the originating agent's contract.
     */
    fun validate(blueprint: StrategyBlueprint): AuthorityValidationResult {
        val agentId = blueprint.diagnosis.affectedComponent // Placeholder: should be originatingAgentId
        val contract = contracts[agentId] ?: return AuthorityValidationResult(
            decision = AuthorityDecision.INVALID_CONTRACT,
            reason = "No registered contract found for agent '$agentId'."
        )

        // 1. Check prohibited actions
        blueprint.proposedModifications.forEach { mod ->
            if (contract.prohibitedActions.any { it.contains(mod.component, ignoreCase = true) }) {
                return AuthorityValidationResult(
                    decision = AuthorityDecision.PROHIBITED_ACTION,
                    reason = "Action on component '${mod.component}' is explicitly prohibited for agent '$agentId'.",
                    ruleReferences = listOf("prohibitedActions")
                )
            }
        }

        // 2. Check write permissions
        blueprint.proposedModifications.forEach { mod ->
            if (!contract.writePermissions.contains(mod.component) && 
                !contract.writePermissions.contains("ALL")) {
                return AuthorityValidationResult(
                    decision = AuthorityDecision.MISSING_PERMISSION,
                    reason = "Agent '$agentId' lacks write permission for component '${mod.component}'.",
                    ruleReferences = listOf("writePermissions")
                )
            }
        }

        // 3. Check confidence requirements
        if (blueprint.diagnosis.diagnosticConfidence < contract.confidenceRequirements) {
            return AuthorityValidationResult(
                decision = AuthorityDecision.REJECTED,
                reason = "Diagnostic confidence (${blueprint.diagnosis.diagnosticConfidence}) is below contract requirements (${contract.confidenceRequirements}).",
                ruleReferences = listOf("confidenceRequirements")
            )
        }

        return AuthorityValidationResult(
            decision = AuthorityDecision.AUTHORIZED,
            reason = "Proposal matches all agent contract parameters."
        )
    }
}

package com.example.data.blueprint

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * Formal operating contract for an intelligence agent.
 */
@JsonClass(generateAdapter = true)
data class AgentContract(
    @Json(name = "agentId") val agentId: String,
    @Json(name = "responsibilities") val responsibilities: List<String> = emptyList(),
    @Json(name = "readPermissions") val readPermissions: List<String> = emptyList(),
    @Json(name = "writePermissions") val writePermissions: List<String> = emptyList(),
    @Json(name = "prohibitedActions") val prohibitedActions: List<String> = emptyList(),
    @Json(name = "humanApprovalRequired") val humanApprovalRequired: Boolean = true,
    @Json(name = "confidenceRequirements") val confidenceRequirements: Double = 0.7
)

/**
 * Result of an authority validation check.
 */
data class AuthorityValidationResult(
    val decision: AuthorityDecision,
    val reason: String,
    val ruleReferences: List<String> = emptyList()
)

enum class AuthorityDecision {
    AUTHORIZED,
    REJECTED,
    INVALID_CONTRACT,
    MISSING_PERMISSION,
    PROHIBITED_ACTION,
    MISSING_EVIDENCE,
    INVALID_SCOPE
}

/**
 * Auditable record of an authority validation event.
 */
@JsonClass(generateAdapter = true)
data class AuthorityValidationRecord(
    @Json(name = "validation_id") val validationId: String = UUID.randomUUID().toString(),
    @Json(name = "agent_id") val agentId: String,
    @Json(name = "contract_version") val contractVersion: String,
    @Json(name = "proposal_id") val proposalId: String,
    @Json(name = "requested_action") val requestedAction: String,
    @Json(name = "target_component") val target: String,
    @Json(name = "decision") val decision: AuthorityDecision,
    @Json(name = "decision_reason") val decisionReason: String,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @Json(name = "rule_references") val ruleReferences: List<String> = emptyList()
)

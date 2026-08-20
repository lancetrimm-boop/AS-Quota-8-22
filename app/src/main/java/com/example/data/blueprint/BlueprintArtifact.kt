package com.example.data.blueprint

import com.example.data.StrategyBlueprint
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * Explicit lifecycle state for a managed strategy blueprint artifact.
 */
enum class BlueprintLifecycleState {
    LOADED,
    VALIDATED,
    PROPOSED,
    APPROVED,
    EXECUTED
}

/**
 * First-Class Editable Blueprint Artifact container.
 * Encapsulates schema versioning, artifact identity, lineage tracking,
 * explicit lifecycle state, and the full 15-section Strategy Blueprint.
 */
@JsonClass(generateAdapter = true)
data class BlueprintArtifact(
    @Json(name = "schema_version") val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    @Json(name = "blueprint_version") val blueprintVersion: String = "1.0.0",
    @Json(name = "blueprint_id") val blueprintId: String = UUID.randomUUID().toString(),
    @Json(name = "parent_blueprint_id") val parentBlueprintId: String? = null,
    @Json(name = "lifecycle_state") val lifecycleState: BlueprintLifecycleState = BlueprintLifecycleState.LOADED,
    @Json(name = "strategy_blueprint") val strategyBlueprint: StrategyBlueprint,
    @Json(name = "builder_instructions") val builderInstructions: BuilderInstructionSet? = null,
    @Json(name = "implementation_manifest") val implementationManifest: BlueprintImplementationManifest? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = "1.0.0"
        val COMPATIBLE_SCHEMA_PREFIXES = listOf("1.")
    }
}

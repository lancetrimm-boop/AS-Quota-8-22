package com.example.data.blueprint

import com.example.data.BaselineState
import com.example.data.StrategyBlueprint
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Single Builder prompt within a sequence of AI Studio / Google Builder prompts.
 */
@JsonClass(generateAdapter = true)
data class BuilderPrompt(
    @Json(name = "step_number") val stepNumber: Int,
    @Json(name = "title") val title: String,
    @Json(name = "prompt_text") val promptText: String
)

/**
 * Complete set of generated Builder prompts for implementing a Strategy Blueprint.
 */
@JsonClass(generateAdapter = true)
data class BuilderInstructionSet(
    @Json(name = "prompts") val prompts: List<BuilderPrompt> = emptyList()
) {
    fun toFormattedString(): String {
        return prompts.joinToString("\n\n" + "=".repeat(60) + "\n\n") {
            "PROMPT ${it.stepNumber} — ${it.title}\n\n${it.promptText}"
        }
    }
}

/**
 * Generates a sequence of standalone, executable Google AI Studio / Google Builder prompts
 * from a complete Strategy Blueprint artifact.
 */
object BuilderInstructionGenerator {

    fun generate(
        blueprint: StrategyBlueprint,
        manifest: BlueprintImplementationManifest? = null
    ): BuilderInstructionSet {
        val prompts = mutableListOf<BuilderPrompt>()
        var stepCounter = 1

        // PROMPT 1 — FORENSIC INSPECTION
        prompts.add(
            BuilderPrompt(
                stepNumber = stepCounter++,
                title = "FORENSIC INSPECTION",
                promptText = generateContractHeader(blueprint, manifest) + "\n\n" + generateForensicInspectionPrompt(blueprint, manifest)
            )
        )

        // PROMPT 2 — DATA MODEL (If modifications or custom data fields exist)
        if (hasDataModelChanges(blueprint)) {
            prompts.add(
                BuilderPrompt(
                    stepNumber = stepCounter++,
                    title = "DATA MODEL",
                    promptText = generateDataModelPrompt(blueprint, manifest)
                )
            )
        }

        // PROMPT 3 — BUSINESS LOGIC (If logic modifications or strategy execution steps exist)
        if (hasBusinessLogicChanges(blueprint)) {
            prompts.add(
                BuilderPrompt(
                    stepNumber = stepCounter++,
                    title = "BUSINESS LOGIC",
                    promptText = generateBusinessLogicPrompt(blueprint, manifest)
                )
            )
        }

        // PROMPT 4 — UI (If actions or visual notices exist)
        if (hasUiChanges(blueprint)) {
            prompts.add(
                BuilderPrompt(
                    stepNumber = stepCounter++,
                    title = "UI",
                    promptText = generateUiPrompt(blueprint)
                )
            )
        }

        // PROMPT 5 — PERSISTENCE (If persistence requirements exist)
        if (hasPersistenceChanges(blueprint)) {
            prompts.add(
                BuilderPrompt(
                    stepNumber = stepCounter++,
                    title = "PERSISTENCE",
                    promptText = generatePersistencePrompt(blueprint)
                )
            )
        }

        // PROMPT 6 — INTEGRATION (If multiple components or telemetry integration exist)
        if (hasIntegrationChanges(blueprint)) {
            prompts.add(
                BuilderPrompt(
                    stepNumber = stepCounter++,
                    title = "INTEGRATION",
                    promptText = generateIntegrationPrompt(blueprint)
                )
            )
        }

        // PROMPT 7 — TESTING (Always included)
        prompts.add(
            BuilderPrompt(
                stepNumber = stepCounter++,
                title = "TESTING",
                promptText = generateTestingPrompt(blueprint, manifest)
            )
        )

        // PROMPT 8 — REGRESSION AUDIT (Always included)
        prompts.add(
            BuilderPrompt(
                stepNumber = stepCounter++,
                title = "REGRESSION AUDIT",
                promptText = generateRegressionAuditPrompt(blueprint)
            )
        )

        // PROMPT 9 — FINAL VERIFICATION (Always included)
        prompts.add(
            BuilderPrompt(
                stepNumber = stepCounter++,
                title = "FINAL VERIFICATION",
                promptText = generateFinalVerificationPrompt(blueprint)
            )
        )

        return BuilderInstructionSet(prompts = prompts)
    }

    private fun hasDataModelChanges(blueprint: StrategyBlueprint): Boolean {
        return blueprint.proposedModifications.isNotEmpty() ||
                blueprint.tasteDnaModifications.isNotEmpty() ||
                blueprint.recommendationEngineModifications.isNotEmpty() ||
                blueprint.baselineState != BaselineState()
    }

    private fun hasBusinessLogicChanges(blueprint: StrategyBlueprint): Boolean {
        return blueprint.strategySelection.selectedStrategy.isNotBlank() ||
                blueprint.executionPlan.intendedActions.isNotEmpty()
    }

    private fun hasUiChanges(blueprint: StrategyBlueprint): Boolean {
        return blueprint.actions.isNotEmpty() ||
                blueprint.recommendationNotice.isNotBlank()
    }

    private fun hasPersistenceChanges(blueprint: StrategyBlueprint): Boolean {
        return blueprint.executionPlan.persistenceRequirements.isNotBlank()
    }

    private fun hasIntegrationChanges(blueprint: StrategyBlueprint): Boolean {
        return blueprint.executionPlan.affectedComponents.size >= 2 ||
                blueprint.evidence.provenance.isNotBlank()
    }

    private fun formatModifications(blueprint: StrategyBlueprint, manifest: BlueprintImplementationManifest?): String {
        val mods = mutableListOf<String>()

        if (manifest != null) {
            manifest.proposedModifications.forEach { plan ->
                mods.add(
                    """
                    - Target Component: ${plan.componentName}
                      • Source File: ${plan.sourceFile}
                      • Target Method/Property: ${plan.methodOrProperty}
                      • Change Type: ${plan.changeType.name}
                      • Current Value: ${plan.blueprintExpectedValue}
                      • Proposed Value: ${plan.proposedValue}
                      • Dependencies: ${plan.dependencies.joinToString(", ")}
                      • Validation Criteria: ${plan.validationCriteria}
                      • Rollback Procedure: ${plan.rollbackProcedure}
                    """.trimIndent()
                )
            }
            return mods.joinToString("\n\n")
        }

        blueprint.proposedModifications.forEach { mod ->
            mods.add(
                """
                - Affected Component: ${mod.component}
                  • Parameter: ${mod.parameter}
                  • Current Value: ${mod.currentValue}
                  • Proposed Value: ${mod.proposedValue}
                  • Reason: ${mod.reason}
                  • Expected Outcome: ${mod.expectedEffect}
                  • Modification Type: ${mod.modificationType.name}
                  • Validation Method: Verify parameter update in ${mod.component} and run unit test suite.
                """.trimIndent()
            )
        }

        blueprint.tasteDnaModifications.forEach { mod ->
            mods.add(
                """
                - Affected Component: TasteDNA
                  • Parameter: ${mod.dimension}
                  • Current Value: ${mod.previousValue}
                  • Proposed Value: ${mod.proposedValue}
                  • Reason: ${mod.evidence}
                  • Expected Outcome: Delta ${mod.delta}
                  • Modification Tier: ${mod.modificationType.name}
                  • Modification Type: ${if (mod.isAutomatic) "AUTOMATIC_TASTE_TUNE" else "MANUAL_TASTE_TUNE"}
                  • Validation Method: Verify TasteDNA weight update and test score calculation.
                """.trimIndent()
            )
        }

        blueprint.recommendationEngineModifications.forEach { mod ->
            mods.add(
                """
                - Affected Component: RecommendationEngine
                  • Parameter: ${mod.parameterOrWeightName}
                  • Current Value: ${mod.previousValue}
                  • Proposed Value: ${mod.proposedValue}
                  • Reason: ${mod.justification}
                  • Expected Outcome: Delta ${mod.delta}
                  • Modification Type: ${mod.modificationType.name}
                  • Validation Method: Verify recommendation weight update and run test suite.
                """.trimIndent()
            )
        }

        return if (mods.isEmpty()) {
            "No explicit weight or parameter modifications proposed in this blueprint."
        } else {
            mods.joinToString("\n\n")
        }
    }

    private fun generateContractHeader(bp: StrategyBlueprint, manifest: BlueprintImplementationManifest?): String {
        val files = manifest?.proposedModifications?.flatMap { it.filesToModify }?.distinct() ?: emptyList()
        return """
            THIS IS AN APPROVED IMPLEMENTATION CONTRACT.
            DO NOT EXPAND SCOPE. DO NOT REFACTOR UNRELATED SYSTEMS.

            Blueprint ID: ${bp.identity.blueprintId}
            Blueprint Version: ${bp.identity.version}
            Status: ${bp.identity.status.name}
            
            OBJECTIVE: ${bp.diagnosis.problemStatement}
            
            APPROVED SCOPE:
            ${manifest?.proposedModifications?.joinToString("\n") { "  • ${it.componentName}.${it.methodOrProperty} in ${it.sourceFile}" } ?: "See Strategy Modifications"}

            FILES EXPECTED TO CHANGE:
            ${files.joinToString("\n") { "  • $it" }}

            CONSTRAINTS:
            - Do not modify authoritative intelligence logic (ClosedLoopEngine, etc.) unless explicitly scoped.
            - Maintain backward compatibility for all data models.
            - Ensure all technical changes align exactly with the approved StrategyBlueprint.

            ACCEPTANCE CRITERIA:
            ${bp.targetState.successCriteria.joinToString("\n") { "  [ ] $it" }}

            VERIFICATION REQUIREMENTS:
            ${bp.productionValidationRequirements.joinToString("\n") { "  - $it" }}
        """.trimIndent()
    }

    private fun generateForensicInspectionPrompt(bp: StrategyBlueprint, manifest: BlueprintImplementationManifest?): String {
        val components = (bp.executionPlan.affectedComponents + bp.diagnosis.affectedComponent).distinct()
        val factsText = bp.diagnosis.knownFacts.joinToString("\n") { "  - FACT: $it" }
        val inferencesText = bp.diagnosis.inferences.joinToString("\n") { "  - INFERENCE: $it" }
        val hypothesesText = bp.diagnosis.hypotheses.joinToString("\n") { "  - HYPOTHESIS: $it" }
        
        val filesToInspect = if (manifest != null) {
            manifest.proposedModifications.map { it.sourceFile }.distinct().joinToString(", ")
        } else components.joinToString(", ")

        return """
            Inspect existing code and system state before making any modifications.

            1. Files and Components to Inspect:
            - Source Files: $filesToInspect
            - Target Components: ${components.joinToString(", ")}
            - Primary Diagnostic Component: ${bp.diagnosis.affectedComponent}
            - If exact file paths are unknown in your workspace, locate them using workspace search tools or directory listing (e.g., search for class definitions matching ${bp.diagnosis.affectedComponent}).

            2. Evidence Provenance & Proven Facts:
            - Evidence Tier: ${bp.validationState.name}
            - Evidence Quality: ${"%.2f".format(bp.evidence.evidenceQuality)}
            - Production Samples: ${bp.evidence.productionCount}
            
            Verified Facts and Contextual Inferences:
            $factsText
            $inferencesText
            $hypothesesText

            3. Standard Safety Rules:
            - Inspect existing code first before making any changes.
            - Preserve working functionality across all existing features.
            - Avoid destructive changes or modifying unrelated components.
            - Avoid deleting existing data, state logs, or historical records.

            4. Exact Implementation Goals:
            - Inspect current parameter values and configuration for component '${bp.diagnosis.affectedComponent}'.
            - Examine evidence records: ${bp.evidence.productionCount} production samples, ${bp.evidence.experimentalCount} experimental samples, ${bp.evidence.simulationCount} simulation samples.
            - Verify problem diagnosis statement: "${bp.diagnosis.problemStatement}".

            5. Acceptance Criteria:
            - Forensic report completed identifying existing implementation files.
            - Baseline metrics verified against baseline engagement score of ${bp.baselineState.engagementScore}.

            6. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a checkpoint before making code changes.
        """.trimIndent()
    }

    private fun generateDataModelPrompt(bp: StrategyBlueprint, manifest: BlueprintImplementationManifest?): String {
        return """
            Implement Data Model updates required for Strategy Blueprint "${bp.title}".

            1. Files and Components to Inspect:
            - Data models and data classes in package `com.example.data` (e.g., `StrategyBlueprint.kt`, `BaselineState.kt`, `TasteDna.kt`, or locate them if exact file paths are unknown).

            2. Standard Safety Rules:
            - Inspect existing data model code first before making changes.
            - Preserve working functionality and ensure backward compatibility for serialized state.
            - Avoid destructive schema breaking changes or dropping stored fields.
            - Avoid deleting existing user data or persisted records.

            3. Exact Implementation Goals:
            Update data models to support the following parameter changes:

            ${formatModifications(bp, manifest)}

            Target State Specifications:
            - Target Metric: ${bp.targetState.targetMetric}
            - Target Value: ${bp.targetState.targetValue}
            - Desired Behavioral Outcome: ${bp.targetState.desiredBehavioralOutcome}

            4. Acceptance Criteria:
            - Data models cleanly reflect target parameter structures.
            - Schema serialization complies with Blueprint schema version 1.0.0.

            5. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a checkpoint after completing data model updates.
        """.trimIndent()
    }

    private fun generateBusinessLogicPrompt(bp: StrategyBlueprint, manifest: BlueprintImplementationManifest?): String {
        val steps = bp.executionPlan.intendedActions.joinToString("\n") {
            "  Step ${it.stepOrder}: ${it.action} (Target: ${it.targetComponent}, Persistence: ${it.persistenceRequirements})"
        }
        val implementationOrder = manifest?.implementationOrder?.joinToString(" -> ") ?: bp.executionPlan.executionOrderDescription

        return """
            Implement Business Logic changes for strategy "${bp.strategySelection.selectedStrategy}".

            1. Files and Components to Inspect:
            - Business logic engine files for components: ${bp.executionPlan.affectedComponents.joinToString(", ")} (locate exact files in `com.example.data` if paths are unknown).

            2. Standard Safety Rules:
            - Inspect existing business logic implementations first before modifying code.
            - Preserve working functionality and fallback logic paths.
            - Avoid destructive overrides or disabling existing safety checks.
            - Avoid deleting existing operational state or historical metrics.

            3. Exact Implementation Goals:
            - Implement selected strategy: "${bp.strategySelection.selectedStrategy}".
            - Strategy Rationale: ${bp.strategySelection.rationale}.
            - Implement parameter modifications:

            ${formatModifications(bp, manifest)}

            - Execute intended action steps:
            $steps

            4. Acceptance Criteria:
            - Engine calculations correctly apply updated parameters.
            - Execution follows the designated sequence: $implementationOrder.
            - Expected improvement: ${bp.expectedOutcome.expectedImprovement}.

            5. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a checkpoint after business logic updates.
        """.trimIndent()
    }

    private fun generateUiPrompt(bp: StrategyBlueprint): String {
        val actionsText = bp.actions.joinToString("\n") {
            "  - [${it.priority}] ${it.title}: ${it.description} (Target: ${it.targetComponent})"
        }
        return """
            Update UI components and screens to reflect Strategy Blueprint status and user actions.

            1. Files and Components to Inspect:
            - UI Composables and screens in `com.example.ui` (e.g. `EngagementDebuggerScreen.kt`, `EditableBlueprintStudioCard.kt`, or locate relevant UI files if paths are unknown).

            2. Standard Safety Rules:
            - Inspect existing UI composable implementations first.
            - Preserve working functionality, visual styling, and responsive design layouts.
            - Avoid destructive removal of existing UI widgets or debugger tools.
            - Avoid deleting user interface state or resetting user inputs.

            3. Exact Implementation Goals:
            - Display Recommendation Notice: "${bp.recommendationNotice}".
            - Display Risk Assessment: "${bp.riskAssessment}".
            - Present action items in UI:
            $actionsText

            4. Acceptance Criteria:
            - UI clearly displays strategy status, lifecycle badges, and validation state (${bp.validationState.name}).
            - User interactions allow loading, editing, validating, and exporting blueprints safely.

            5. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a checkpoint after UI updates.
        """.trimIndent()
    }

    private fun generatePersistencePrompt(bp: StrategyBlueprint): String {
        return """
            Implement Persistence layer storage and snapshot mechanisms for Strategy Blueprint.

            1. Files and Components to Inspect:
            - Persistence classes: `AuraDatabase.kt`, `MediaRepository.kt`, `BlueprintArtifactManager.kt` (or locate DAOs and storage handlers if paths are unknown).

            2. Standard Safety Rules:
            - Inspect existing persistence logic first.
            - Preserve working functionality and existing stored records.
            - Avoid destructive database migrations or dropping tables.
            - Avoid deleting existing user preferences, media items, or historical telemetry logs.

            3. Exact Implementation Goals:
            - Fulfill persistence requirements: "${bp.executionPlan.persistenceRequirements}".
            - Ensure strategy snapshot and parameter updates persist across application launches.
            - Reversibility support: ${if (bp.executionPlan.isReversible) "Reversible with snapshot rollback" else "Irreversible"}.

            4. Acceptance Criteria:
            - Blueprint artifact states and parameter updates persist reliably.
            - Storage read/write operations execute without I/O exceptions or data loss.

            5. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a checkpoint after persistence changes.
        """.trimIndent()
    }

    private fun generateIntegrationPrompt(bp: StrategyBlueprint): String {
        return """
            Wire Integration points between components and telemetry pipelines.

            1. Files and Components to Inspect:
            - Integration wiring in `AuraTelemetryService.kt`, `MediaRepository.kt`, `ClosedLoopEngine.kt`, and target components: ${bp.executionPlan.affectedComponents.joinToString(", ")} (locate wiring classes if paths are unknown).

            2. Standard Safety Rules:
            - Inspect existing integration contracts first.
            - Preserve working functionality and existing event pipelines.
            - Avoid breaking component contracts or creating circular dependencies.
            - Avoid deleting or suppressing telemetry signal collection.

            3. Exact Implementation Goals:
            - Connect telemetry provenance ("${bp.evidence.provenance}") with strategy execution pipeline.
            - Verify feedback loop between closed-loop report diagnostics and engine parameter updates.
            - Experiment integration: Control Group = "${bp.experimentDesign.controlGroupConfig}", Experimental Group = "${bp.experimentDesign.experimentalGroupConfig}".

            4. Acceptance Criteria:
            - Inter-module communication operates without race conditions.
            - Experiment group switching and rollback trigger condition ("${bp.experimentDesign.rollbackCondition}") execute properly.

            5. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a checkpoint after completing integration wiring.
        """.trimIndent()
    }

    private fun generateTestingPrompt(bp: StrategyBlueprint, manifest: BlueprintImplementationManifest?): String {
        return """
            Implement and execute Unit Tests for Strategy Blueprint "${bp.title}".

            1. Files and Components to Inspect:
            - Unit test files: `ClosedLoopIntelligenceTest.kt`, `BlueprintArtifactTest.kt` (locate test directory under `src/test/java` if paths are unknown).

            2. Standard Safety Rules:
            - Inspect existing unit test implementations first.
            - Preserve working functionality and keep all existing unit tests passing.
            - Avoid removing or skipping failing assertions.
            - Avoid deleting test fixture data.

            3. Exact Implementation Goals:
            - Write unit test cases verifying:
              • Baseline score (${bp.baselineState.engagementScore}) vs Target score (${bp.targetState.targetValue}).
              • Parameter modifications:
            ${formatModifications(bp, manifest)}
              • Actual outcome verification and classification (${bp.actualOutcome.outcomeClassification}).

            4. Acceptance Criteria:
            - 100% of unit tests pass cleanly without errors.
            - Test suite verifies all structural sections of the strategy blueprint.

            5. Mandatory Build & Checkpoint Rules:
            - Verify unit tests pass (`gradle :app:testDebugUnitTest`).
            - Create a checkpoint after test execution.
        """.trimIndent()
    }

    private fun generateRegressionAuditPrompt(bp: StrategyBlueprint): String {
        return """
            Conduct a comprehensive Regression Audit across the codebase for Strategy Blueprint "${bp.title}".

            1. Files and Components to Inspect:
            - All modified source files in `com.example.data`, `com.example.ui`, and `src/test/java`.

            2. Standard Safety Rules:
            - Inspect git diff and modified code paths first.
            - Preserve working functionality across core media discovery and recommendation features.
            - Avoid destructive uncommitted edits or leaving debug code.
            - Avoid deleting existing data or state persistence.

            3. Exact Implementation Goals:
            - Verify no breaking changes were introduced to existing API contracts or data models.
            - Confirm risk mitigations ("${bp.expectedOutcome.riskMitigation}") are intact.
            - Verify risk assessment level ("${bp.expectedOutcome.riskLevel}") is correctly handled.

            4. Acceptance Criteria:
            - Zero regression in core features, pairwise evaluation, or telemetry collection.
            - Zero build errors or unhandled warnings.

            5. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a checkpoint post-audit.
        """.trimIndent()
    }

    private fun generateFinalVerificationPrompt(bp: StrategyBlueprint): String {
        val valReqsText = bp.productionValidationRequirements.joinToString("\n") { "  - $it" }
        return """
            Perform Final Verification and finalize Blueprint Artifact.

            1. Files and Components to Inspect:
            - Full applet compilation logs and unit test reports.

            2. Post-Implementation Validation Requirements:
            - Evidence Tier Target: ${bp.validationState.name}
            - Next Recommended Step: ${bp.nextExperimentRecommendation}
            
            Production Validation Requirements:
            $valReqsText

            3. Standard Safety Rules:
            - Inspect full build results first.
            - Preserve working functionality and system stability.
            - Avoid destructive residual files or broken artifacts.
            - Avoid deleting valid strategy logs or exported blueprint JSON files.

            4. Exact Implementation Goals:
            - Execute full applet compilation (`compile_applet`) to confirm build succeeds.
            - Run complete unit test suite (`gradle :app:testDebugUnitTest`) to ensure all tests pass green.
            - Confirm Blueprint Artifact lifecycle state transition (${bp.identity.status}).
            - Verify exported blueprint artifact contains complete 1-9 sequence of Builder instructions in clear English.

            5. Acceptance Criteria:
            - Clean build, 0 test failures, valid blueprint artifact serialization.
            - Generated Builder instructions are standalone, clear, and executable by another Google AI Studio Builder session.

            6. Mandatory Build & Checkpoint Rules:
            - Verify code builds cleanly (`compile_applet` or `gradle :app:testDebugUnitTest`).
            - Create a final checkpoint before concluding work.
        """.trimIndent()
    }
}

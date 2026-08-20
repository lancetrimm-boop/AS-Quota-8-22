package com.example.data

import com.squareup.moshi.JsonClass
import java.util.UUID

/**
 * Execution status of a Strategy Blueprint.
 */
enum class StrategyStatus {
    DRAFT,
    PROPOSED,
    EXECUTING,
    COMPLETED,
    ROLLED_BACK,
    REJECTED
}

/**
 * Validation state of a strategy blueprint based on evidence tier and evaluation outcome.
 */
enum class StrategyValidationState {
    /** Validated in live production with confirmed empirical improvement. */
    PRODUCTION_VALIDATED,
    /** Supported by experimental trials or A/B testing, requiring production rollout validation. */
    EXPERIMENTALLY_SUPPORTED,
    /** Supported by predictive models or benchmark simulations, unverified in live environments. */
    SIMULATION_SUPPORTED,
    /** Theoretical hypothesis or draft strategy with no supporting empirical evidence. */
    UNVALIDATED_HYPOTHESIS
}

/**
 * Classification of modifications proposed within a blueprint.
 */
enum class ModificationType {
    RECOMMENDED_CHANGE,
    EXPERIMENTAL_CHANGE,
    SIMULATED_CHANGE,
    NO_CHANGE
}

// 1. BLUEPRINT IDENTITY
@JsonClass(generateAdapter = true)
data class BlueprintIdentity(
    val blueprintId: String = UUID.randomUUID().toString(),
    val version: String = "1.0.0",
    val createdAt: Long = System.currentTimeMillis(),
    val parentBlueprintId: String? = null,
    val trigger: String = "AURA_EVALUATION_CYCLE",
    val status: StrategyStatus = StrategyStatus.PROPOSED
)

// 2. PROBLEM DIAGNOSIS
@JsonClass(generateAdapter = true)
data class ProblemDiagnosis(
    val problemStatement: String = "",
    val beliefDescription: String = "",
    val affectedComponent: String = "",
    val knownFacts: List<String> = emptyList(),
    val inferences: List<String> = emptyList(),
    val hypotheses: List<String> = emptyList(),
    val diagnosticConfidence: Double = 0.0
)

// 3. EVIDENCE
@JsonClass(generateAdapter = true)
data class EvidenceSummarySection(
    val productionEvidence: List<String> = emptyList(),
    val experimentalEvidence: List<String> = emptyList(),
    val simulationEvidence: List<String> = emptyList(),
    val productionCount: Int = 0,
    val experimentalCount: Int = 0,
    val simulationCount: Int = 0,
    val provenance: String = "AuraTelemetryService",
    val evidenceQuality: Double = 0.0,
    val knownLimitations: List<String> = emptyList()
)

// 4. BASELINE STATE
@JsonClass(generateAdapter = true)
data class BaselineState(
    val engagementScore: Double = 50.0,
    val pairwiseWeights: Map<String, Double> = mapOf("pairwiseWeight" to 0.35),
    val aiSkipWeights: Map<String, Double> = mapOf("skipThreshold" to 0.25),
    val tasteDnaWeights: Map<String, Double> = mapOf("vibrancy" to 0.20, "aesthetic" to 0.20),
    val explicitRatingWeights: Map<String, Double> = mapOf("like" to 0.15, "bookmark" to 0.25),
    val aestheticWeights: Map<String, Double> = mapOf("composition" to 0.10, "lighting" to 0.10),
    val recommendationWeights: Map<String, Double> = mapOf("contentSimilarity" to 0.40, "collaborative" to 0.30),
    val diversityNoveltyParameters: Map<String, Double> = mapOf("diversity" to 0.15, "noveltyDecay" to 0.05),
    val otherStrategyParameters: Map<String, String> = mapOf("engine" to "ClosedLoopEngine")
)

// 5. TARGET STATE
@JsonClass(generateAdapter = true)
data class TargetState(
    val targetMetric: String = "Personalization Score",
    val targetValue: Double = 50.0,
    val desiredBehavioralOutcome: String = "Sustained engagement and high pairwise signal accuracy",
    val successCriteria: List<String> = emptyList()
)

// 6. STRATEGY
@JsonClass(generateAdapter = true)
data class StrategySelection(
    val selectedStrategy: String = "",
    val rationale: String = "",
    val alternativesConsidered: List<String> = emptyList(),
    val rejectionReasons: List<String> = emptyList()
)

// 7. PROPOSED MODIFICATIONS
@JsonClass(generateAdapter = true)
data class ProposedModification(
    val modificationId: String = UUID.randomUUID().toString(),
    val component: String = "",
    val parameter: String = "",
    val currentValue: String = "",
    val proposedValue: String = "",
    val delta: String = "",
    val reason: String = "",
    val supportingEvidence: String = "",
    val expectedEffect: String = "",
    val confidence: Double = 0.0,
    val modificationType: ModificationType = ModificationType.NO_CHANGE
)

// 8. TASTE DNA MODIFICATIONS
@JsonClass(generateAdapter = true)
data class TasteDnaModification(
    val dimension: String = "",
    val previousValue: Double = 0.0,
    val proposedValue: Double = 0.0,
    val delta: Double = 0.0,
    val evidence: String = "",
    val confidence: Double = 0.0,
    val isAutomatic: Boolean = false,
    val modificationType: ModificationType = ModificationType.NO_CHANGE
)

// 9. RECOMMENDATION ENGINE MODIFICATIONS
@JsonClass(generateAdapter = true)
data class RecommendationEngineModification(
    val parameterOrWeightName: String = "",
    val previousValue: Double = 0.0,
    val proposedValue: Double = 0.0,
    val delta: Double = 0.0,
    val justification: String = "",
    val modificationType: ModificationType = ModificationType.NO_CHANGE
)

// 10. EXECUTION PLAN
@JsonClass(generateAdapter = true)
data class ActionStep(
    val stepOrder: Int = 0,
    val action: String = "",
    val targetComponent: String = "",
    val persistenceRequirements: String = "",
    val isReversible: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ExecutionPlan(
    val intendedActions: List<ActionStep> = emptyList(),
    val affectedComponents: List<String> = emptyList(),
    val executionOrderDescription: String = "",
    val persistenceRequirements: String = "",
    val isReversible: Boolean = true
)

// 11. EXPERIMENT DESIGN
@JsonClass(generateAdapter = true)
data class ExperimentDesign(
    val controlGroupConfig: String = "",
    val experimentalGroupConfig: String = "",
    val sampleRequirements: Int = 0,
    val durationDescription: String = "",
    val successThreshold: String = "",
    val failureThreshold: String = "",
    val rollbackCondition: String = ""
)

// 12. EXPECTED OUTCOME
@JsonClass(generateAdapter = true)
data class ExpectedOutcomeSection(
    val expectedImprovement: String = "",
    val timeline: String = "",
    val riskLevel: String = "",
    val riskMitigation: String = ""
)

// 13. ACTUAL OUTCOME
@JsonClass(generateAdapter = true)
data class ActualOutcomeSection(
    val measuredScore: Double? = null,
    val outcomeClassification: String = "PENDING_EVALUATION",
    val deltaVsTarget: Double? = null,
    val verificationTimestamp: Long? = null
)

// 14. LEARNING
@JsonClass(generateAdapter = true)
data class LearningSection(
    val keyInsights: List<String> = emptyList(),
    val generalizablePatterns: List<String> = emptyList(),
    val modelAdjustments: List<String> = emptyList(),
    val futureRecommendations: List<String> = emptyList()
)

// 15. VERSION HISTORY
@JsonClass(generateAdapter = true)
data class BlueprintVersionEntry(
    val version: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val authorOrEngine: String = "Aura Engine",
    val notes: String = ""
)

/**
 * Tactical action item defined within a Strategy Blueprint for legacy / UI compatibility.
 */
@JsonClass(generateAdapter = true)
data class StrategyAction(
    val title: String = "",
    val description: String = "",
    val priority: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val targetComponent: String = "System",
    val expectedImpact: String = "Optimization"
)

/**
 * Complete, evidence-governed Strategy Blueprint containing all 15 structural sections.
 */
@JsonClass(generateAdapter = true)
data class StrategyBlueprint(
    // Section 1: BLUEPRINT IDENTITY
    val identity: BlueprintIdentity,

    // Section 2: PROBLEM DIAGNOSIS
    val diagnosis: ProblemDiagnosis,

    // Section 3: EVIDENCE
    val evidence: EvidenceSummarySection,

    // Section 4: BASELINE STATE
    val baselineState: BaselineState,

    // Section 5: TARGET STATE
    val targetState: TargetState,

    // Section 6: STRATEGY
    val strategySelection: StrategySelection,

    // Section 7: PROPOSED MODIFICATIONS
    val proposedModifications: List<ProposedModification> = emptyList(),

    // Section 8: TASTE DNA MODIFICATIONS
    val tasteDnaModifications: List<TasteDnaModification> = emptyList(),

    // Section 9: RECOMMENDATION ENGINE MODIFICATIONS
    val recommendationEngineModifications: List<RecommendationEngineModification> = emptyList(),

    // Section 10: EXECUTION PLAN
    val executionPlan: ExecutionPlan,

    // Section 11: EXPERIMENT DESIGN
    val experimentDesign: ExperimentDesign,

    // Section 12: EXPECTED OUTCOME
    val expectedOutcome: ExpectedOutcomeSection,

    // Section 13: ACTUAL OUTCOME
    val actualOutcome: ActualOutcomeSection = ActualOutcomeSection(),

    // Section 14: LEARNING
    val learning: LearningSection = LearningSection(),

    // Section 15: VERSION HISTORY
    val versionHistory: List<BlueprintVersionEntry> = emptyList(),

    // Recommendation declaration notice
    val recommendationNotice: String = "NO PRODUCTION MODIFICATION RECOMMENDED",

    // Contextual ClosedLoopReport
    val closedLoopReport: ClosedLoopReport? = null,

    // Validation state
    val validationState: StrategyValidationState = StrategyValidationState.UNVALIDATED_HYPOTHESIS,

    // Recommended next steps
    val nextExperimentRecommendation: String = "",
    val productionValidationRequirements: List<String> = emptyList(),

    // Risk assessment
    val riskAssessment: String = "",

    // Generated Builder prompts for AI Studio / Builder session implementation
    val builderInstructions: com.example.data.blueprint.BuilderInstructionSet? = null
) {
    // Convenience properties for backward compatibility
    val id: String get() = identity.blueprintId
    val title: String get() = strategySelection.selectedStrategy
    val description: String get() = diagnosis.problemStatement
    val createdAt: Long get() = identity.createdAt
    val actions: List<StrategyAction> get() = executionPlan.intendedActions.map {
        StrategyAction(
            title = it.action,
            description = "Component: ${it.targetComponent}. Persistence: ${it.persistenceRequirements}",
            priority = if (expectedOutcome.riskLevel == "HIGH" || expectedOutcome.riskLevel == "CRITICAL") "HIGH" else "MEDIUM",
            targetComponent = it.targetComponent,
            expectedImpact = expectedOutcome.expectedImprovement
        )
    }
    val requiresProductionValidation: Boolean get() = (evidence.productionCount == 0 || closedLoopReport?.productionImprovementEstablished != true)
    val validationNotes: String get() = recommendationNotice
    val targetValidity: TargetValidity get() = closedLoopReport?.targetValidity ?: TargetValidity.VALID
}

/**
 * Generator that builds evidence-aware Strategy Blueprints from Closed Loop reports.
 * 
 * DATA GOVERNANCE RULE:
 * This generator is for SYSTEM-LEVEL prototype optimization only.
 * It is EXPLICITLY PROHIBITED from consuming individual user personalization data 
 * (e.g., Taste DNA slider values, user-specific fine-tuning history) as evidence 
 * for global changes. Individual user improvements remain isolated in the 
 * User Personalization Loop.
 */
object StrategyBlueprintGenerator {

    /**
     * Generates a strategy blueprint consuming the provided [ClosedLoopReport].
     */
    fun generateBlueprint(
        title: String,
        description: String,
        report: ClosedLoopReport,
        actions: List<StrategyAction> = emptyList()
    ): StrategyBlueprint {
        // Determine validation state
        val validationState = when {
            (report.productionImprovementEstablished || report.productionRegressionEstablished) && report.productionSampleCount >= 5 -> {
                StrategyValidationState.PRODUCTION_VALIDATED
            }
            report.experimentalSampleCount > 0 -> {
                StrategyValidationState.EXPERIMENTALLY_SUPPORTED
            }
            report.simulationSampleCount > 0 -> {
                StrategyValidationState.SIMULATION_SUPPORTED
            }
            else -> {
                StrategyValidationState.UNVALIDATED_HYPOTHESIS
            }
        }

        val hasProductionEvidence = report.productionSampleCount > 0
        val isProdValidated = report.productionImprovementEstablished && hasProductionEvidence

        // Determine recommendation notice and modification type
        val (recommendationNotice, defaultModType) = when {
            !hasProductionEvidence -> {
                "NO PRODUCTION MODIFICATION RECOMMENDED" to if (report.experimentalSampleCount > 0) {
                    ModificationType.EXPERIMENTAL_CHANGE
                } else if (report.simulationSampleCount > 0) {
                    ModificationType.SIMULATED_CHANGE
                } else {
                    ModificationType.NO_CHANGE
                }
            }
            isProdValidated -> {
                "RECOMMENDED CHANGE: Production validated with ${report.productionSampleCount} samples" to ModificationType.RECOMMENDED_CHANGE
            }
            else -> {
                "NO PRODUCTION MODIFICATION RECOMMENDED" to ModificationType.NO_CHANGE
            }
        }

        // Section 1: BLUEPRINT IDENTITY
        val identity = BlueprintIdentity(
            blueprintId = UUID.randomUUID().toString(),
            version = "1.0.0",
            createdAt = System.currentTimeMillis(),
            parentBlueprintId = null,
            trigger = "ClosedLoopEngine Evaluation (${report.id.take(8)})",
            status = if (isProdValidated) StrategyStatus.PROPOSED else StrategyStatus.DRAFT
        )

        // Section 2: PROBLEM DIAGNOSIS
        val diagnosis = ProblemDiagnosis(
            problemStatement = description.ifEmpty { "Personalization performance evaluation under ${report.outcomeClassification.name}." },
            beliefDescription = report.summaryMessage,
            affectedComponent = "Personalization & Recommendation Engine",
            knownFacts = report.knownFacts,
            inferences = report.inferences,
            hypotheses = if (!hasProductionEvidence) listOf("Current parameters may be sub-optimal for the current user profile segment.") else emptyList(),
            diagnosticConfidence = report.overallConfidence
        )

        // Section 3: EVIDENCE
        val evidenceSummary = EvidenceSummarySection(
            productionEvidence = if (hasProductionEvidence) listOf("Production telemetry: ${report.productionSampleCount} samples, Quality: ${"%.2f".format(report.productionEvidenceQuality)}") else emptyList(),
            experimentalEvidence = report.experimentalFindings,
            simulationEvidence = report.simulatedFindings,
            productionCount = report.productionSampleCount,
            experimentalCount = report.experimentalSampleCount,
            simulationCount = report.simulationSampleCount,
            provenance = "AuraTelemetryService",
            evidenceQuality = report.overallConfidence,
            knownLimitations = report.unknowns + if (!hasProductionEvidence) listOf("NO PRODUCTION SAMPLES: Zero live telemetry samples available.") else emptyList()
        )

        // Section 4: BASELINE STATE
        val baselineState = BaselineState(
            engagementScore = report.baselineScore,
            pairwiseWeights = mapOf("pairwiseWeight" to 0.35, "confidenceThreshold" to 0.60),
            aiSkipWeights = mapOf("skipDeduction" to 0.25, "skipVelocityThreshold" to 1.5),
            tasteDnaWeights = mapOf("vibrancy" to 0.20, "aestheticPreference" to 0.20),
            explicitRatingWeights = mapOf("like" to 0.15, "bookmark" to 0.25),
            aestheticWeights = mapOf("composition" to 0.10, "lighting" to 0.10),
            recommendationWeights = mapOf("contentSimilarity" to 0.40, "collaborativeSignal" to 0.30, "exploration" to 0.30),
            diversityNoveltyParameters = mapOf("diversity" to 0.15, "noveltyDecay" to 0.05),
            otherStrategyParameters = mapOf("evaluationEngine" to "ClosedLoopEngine", "confidenceFloor" to "0.70")
        )

        // Section 5: TARGET STATE
        val targetState = TargetState(
            targetMetric = "Personalization Score",
            targetValue = report.targetScore,
            desiredBehavioralOutcome = "Optimize media selection accuracy and maintain zero regression across sessions.",
            successCriteria = listOf(
                "Measured score >= ${report.targetScore}",
                "Production sample count >= 5",
                "Target validity classification == VALID_INCREASE"
            )
        )

        // Section 6: STRATEGY
        val strategySelection = StrategySelection(
            selectedStrategy = title.ifEmpty { "Evidence-Gated Personalization Optimization" },
            rationale = "Strategy calibrated according to evidence tier classification (${report.outcomeClassification.name}).",
            alternativesConsidered = listOf("Unchecked parameter deployment", "Static heuristic rules"),
            rejectionReasons = listOf("Deploying changes without production evidence risks regression", "Static rules ignore live user feedback")
        )

        // Section 7: PROPOSED MODIFICATIONS
        val proposedMods = mutableListOf<ProposedModification>()
        val isChangeProposed = defaultModType != ModificationType.NO_CHANGE

        if (actions.isNotEmpty()) {
            actions.forEachIndexed { idx, act ->
                proposedMods.add(
                    ProposedModification(
                        modificationId = UUID.randomUUID().toString(),
                        component = act.targetComponent,
                        parameter = act.title.lowercase().replace(" ", "_"),
                        currentValue = "1.0",
                        proposedValue = if (isChangeProposed) "1.1" else "1.0",
                        delta = if (isChangeProposed) "+0.1" else "0.0",
                        reason = act.description,
                        supportingEvidence = "Evidence samples: Prod=${report.productionSampleCount}, Exp=${report.experimentalSampleCount}, Sim=${report.simulationSampleCount}",
                        expectedEffect = act.expectedImpact,
                        confidence = if (hasProductionEvidence) report.productionConfidence else report.overallConfidence,
                        modificationType = defaultModType
                    )
                )
            }
        } else {
            proposedMods.add(
                ProposedModification(
                    component = "AISkipEngine",
                    parameter = "skip_deduction_weight",
                    currentValue = "0.25",
                    proposedValue = if (isChangeProposed) "0.20" else "0.25",
                    delta = if (isChangeProposed) "-0.05" else "0.00",
                    reason = "Calibrate skip deduction weight based on user feedback.",
                    supportingEvidence = "Production samples: ${report.productionSampleCount}, Experimental: ${report.experimentalSampleCount}",
                    expectedEffect = "Reduced false-positive skip deductions",
                    confidence = report.overallConfidence,
                    modificationType = defaultModType
                )
            )
            proposedMods.add(
                ProposedModification(
                    component = "PairwiseSystem",
                    parameter = "pairwise_comparison_weight",
                    currentValue = "0.35",
                    proposedValue = if (isChangeProposed) "0.40" else "0.35",
                    delta = if (isChangeProposed) "+0.05" else "0.00",
                    reason = "Gather and prioritize pairwise comparison preferences.",
                    supportingEvidence = "Evidence quality: ${"%.2f".format(report.overallConfidence)}",
                    expectedEffect = "Higher taste alignment accuracy",
                    confidence = report.overallConfidence,
                    modificationType = defaultModType
                )
            )
        }

        // Section 8: TASTE DNA MODIFICATIONS
        val tasteDnaMods = listOf(
            TasteDnaModification(
                dimension = "Vibrancy Preference",
                previousValue = 0.50,
                proposedValue = if (isChangeProposed) 0.55 else 0.50,
                delta = if (isChangeProposed) 0.05 else 0.0,
                evidence = "Production samples: ${report.productionSampleCount}",
                confidence = report.overallConfidence,
                isAutomatic = true,
                modificationType = defaultModType
            ),
            TasteDnaModification(
                dimension = "Aesthetic Composition",
                previousValue = 0.60,
                proposedValue = if (isChangeProposed) 0.65 else 0.60,
                delta = if (isChangeProposed) 0.05 else 0.0,
                evidence = "Production samples: ${report.productionSampleCount}",
                confidence = report.overallConfidence,
                isAutomatic = true,
                modificationType = defaultModType
            )
        )

        // Section 9: RECOMMENDATION ENGINE MODIFICATIONS
        val recMods = listOf(
            RecommendationEngineModification(
                parameterOrWeightName = "contentSimilarity",
                previousValue = 0.40,
                proposedValue = if (isChangeProposed) 0.45 else 0.40,
                delta = if (isChangeProposed) 0.05 else 0.0,
                justification = "Optimizing recommendation filtering under closed loop evaluation",
                modificationType = defaultModType
            )
        )

        // Section 10: EXECUTION PLAN
        val actionSteps = if (actions.isNotEmpty()) {
            actions.mapIndexed { idx, act ->
                ActionStep(
                    stepOrder = idx + 1,
                    action = act.title,
                    targetComponent = act.targetComponent,
                    persistenceRequirements = "RoomDB persistent storage",
                    isReversible = true
                )
            }
        } else {
            listOf(
                ActionStep(1, "Verify Telemetry Integrity", "AuraTelemetryService", "InMemory", true),
                ActionStep(2, "Calibrate Parameter Weights", "RecommendationEngine", "RoomDB", true),
                ActionStep(3, "Activate Circuit Breaker Monitoring", "ClosedLoopEngine", "System", true)
            )
        }

        val executionPlan = ExecutionPlan(
            intendedActions = actionSteps,
            affectedComponents = actionSteps.map { it.targetComponent }.distinct(),
            executionOrderDescription = "1. Verify telemetry -> 2. Calibrate parameters -> 3. Monitor circuit breaker",
            persistenceRequirements = "RoomDB persistent storage with automatic rollback snapshot",
            isReversible = true
        )

        // Section 11: EXPERIMENT DESIGN
        val experimentDesign = ExperimentDesign(
            controlGroupConfig = "Baseline config (score=${report.baselineScore})",
            experimentalGroupConfig = "Target config (score=${report.targetScore})",
            sampleRequirements = 100,
            durationDescription = "7 days or 100 production samples",
            successThreshold = "Statistically significant score increase (+5% over baseline)",
            failureThreshold = "Metric regression detected",
            rollbackCondition = "Automatic rollback if score drops below baseline or error rate > 1%"
        )

        // Section 12: EXPECTED OUTCOME
        val riskLevel = when {
            report.targetValidity != TargetValidity.VALID -> "HIGH"
            !hasProductionEvidence -> "MODERATE"
            report.productionRegressionEstablished -> "CRITICAL"
            else -> "LOW"
        }

        val expectedOutcome = ExpectedOutcomeSection(
            expectedImprovement = "Target score improvement from ${report.baselineScore} to ${report.targetScore}",
            timeline = "48 hours post-deployment",
            riskLevel = riskLevel,
            riskMitigation = "Telemetry circuit-breakers with automatic rollback"
        )

        // Section 13: ACTUAL OUTCOME
        val actualOutcome = ActualOutcomeSection(
            measuredScore = report.measuredScore,
            outcomeClassification = report.outcomeClassification.name,
            deltaVsTarget = report.measuredScore - report.targetScore,
            verificationTimestamp = report.timestamp
        )

        // Section 14: LEARNING
        val learning = LearningSection(
            keyInsights = report.knownFacts,
            generalizablePatterns = listOf(
                "Production telemetry evidence is required before justifying live parameter changes.",
                "Experimental and simulation evidence provide directional support but require live validation."
            ),
            modelAdjustments = report.inferences,
            futureRecommendations = listOf(
                "Gather at least 5 production evidence samples.",
                "Maintain strict evidence tier segregation in evaluation."
            )
        )

        // Section 15: VERSION HISTORY
        val versionHistory = listOf(
            BlueprintVersionEntry(
                version = "1.0.0",
                timestamp = System.currentTimeMillis(),
                authorOrEngine = "Aura Engine",
                notes = "Generated complete strategy blueprint from Closed Loop report ${report.id.take(8)}"
            )
        )

        // Risk Assessment
        val riskAssessment = when {
            report.targetValidity == TargetValidity.UNCHANGED_TARGET -> {
                "HIGH RISK / UNCHANGED TARGET: Target score equals baseline score (${report.baselineScore}). Blueprint specifies no net metric increase."
            }
            report.targetValidity == TargetValidity.INVALID_TARGET -> {
                "CRITICAL RISK / INVALID TARGET: Target score (${report.targetScore}) is lower than baseline (${report.baselineScore})."
            }
            report.productionRegressionEstablished -> {
                "CRITICAL RISK / REGRESSION: Live production regression detected (${report.measuredScore} vs baseline ${report.baselineScore}). Immediate revision required."
            }
            !hasProductionEvidence -> {
                "MODERATE RISK / UNVALIDATED: Zero production evidence. Deploy with active telemetry and circuit-breakers."
            }
            else -> {
                "LOW RISK: Strategy is production-validated with positive metric feedback."
            }
        }

        // Recommended next steps
        val nextExp = when (validationState) {
            StrategyValidationState.PRODUCTION_VALIDATED -> "Sustained monitoring and quarterly recalibration."
            StrategyValidationState.EXPERIMENTALLY_SUPPORTED -> "Graduate to limited production rollout (5-10% cohort) to verify live performance."
            StrategyValidationState.SIMULATION_SUPPORTED -> "Conduct controlled experimental trials or A/B testing with synthetic users."
            StrategyValidationState.UNVALIDATED_HYPOTHESIS -> "Perform baseline simulation and Monte Carlo sensitivity analysis."
        }

        val prodValReqs = mutableListOf<String>()
        if (report.productionSampleCount < 5) {
            prodValReqs.add("Minimum of 5 production telemetry samples required for statistical significance.")
        }
        if (report.productionConfidence < 0.7) {
            prodValReqs.add("Improve production data quality score above 0.70 via high-fidelity event logging.")
        }
        if (report.productionRegressionEstablished) {
            prodValReqs.add("Immediate rollback and root cause analysis required due to detected production regression.")
        }

        val bp = StrategyBlueprint(
            identity = identity,
            diagnosis = diagnosis,
            evidence = evidenceSummary,
            baselineState = baselineState,
            targetState = targetState,
            strategySelection = strategySelection,
            proposedModifications = proposedMods,
            tasteDnaModifications = tasteDnaMods,
            recommendationEngineModifications = recMods,
            executionPlan = executionPlan,
            experimentDesign = experimentDesign,
            expectedOutcome = expectedOutcome,
            actualOutcome = actualOutcome,
            learning = learning,
            versionHistory = versionHistory,
            recommendationNotice = recommendationNotice,
            closedLoopReport = report,
            validationState = validationState,
            nextExperimentRecommendation = nextExp,
            productionValidationRequirements = prodValReqs,
            riskAssessment = riskAssessment
        )

        return bp.copy(
            builderInstructions = com.example.data.blueprint.BuilderInstructionGenerator.generate(bp)
        )
    }
}

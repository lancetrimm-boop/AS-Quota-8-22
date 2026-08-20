package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.*
import com.example.data.blueprint.BlueprintImplementationManifest
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit modular report types for Aura Intelligence exporting.
 */
enum class ModularReportType(val displayName: String) {
    PROTOTYPE_INTELLIGENCE("Prototype Intelligence Report"),
    ENGAGEMENT_INTELLIGENCE("Engagement Intelligence Report"),
    CLOSED_LOOP_INTELLIGENCE("Closed Loop Intelligence Report"),
    STRATEGY_BLUEPRINT("Strategy Blueprint Report"),
    AGENT_INTELLIGENCE("Agent Intelligence Report"),
    EVIDENCE_INTEGRITY("Evidence Integrity Report"),
    BUILDER_INSTRUCTIONS("Builder Instructions Implementation Doc"),
    COMBINED_PACKAGE("Combined Intelligence Package"),
    MASTER_INTELLIGENCE_REPORT("Master Intelligence Report"),
    RECONSTRUCTION_PACKAGE("Intelligence Reconstruction Package"),
    SUGGESTED_IMPROVEMENT_PACKAGE("Suggested Improvement Package")
}

/**
 * Exporter utility for generating evidence-aware Closed Loop Intelligence reports
 * in JSON, Plain Text, Markdown, HTML, and native PDF formats.
 */
object IntelligenceExporter {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Exports report and blueprint into formatted JSON.
     */
    fun exportToJson(
        report: ClosedLoopReport,
        blueprint: StrategyBlueprint? = null,
        manifest: BlueprintImplementationManifest? = null,
        master: MasterIntelligenceReport? = null
    ): String {
        val adapter = moshi.adapter(ExportContainer::class.java).indent("  ")
        val container = ExportContainer(
            report = report,
            blueprint = blueprint,
            manifest = manifest,
            master = master,
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        )
        return adapter.toJson(container)
    }

    fun exportMasterReportToJson(report: MasterIntelligenceReport): String {
        val adapter = moshi.adapter(MasterIntelligenceReport::class.java).indent("  ")
        return adapter.toJson(report)
    }

    /**
     * Exports a Master Intelligence Report into Plain Text format.
     */
    fun exportMasterReportToText(report: MasterIntelligenceReport): String {
        return buildString {
            appendLine("=== AURA MASTER INTELLIGENCE REPORT ===")
            appendLine("Report ID: ${report.id}")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))}")
            appendLine("Data Through: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(report.dataThrough))}")
            appendLine()
            
            appendLine("--- EXECUTIVE SUMMARY ---")
            appendLine("System Health: ${report.executiveSummary.systemHealth}")
            appendLine(report.executiveSummary.plainEnglishSummary)
            appendLine()
            
            appendLine("--- SYSTEM ANALYSIS ---")
            appendLine(report.systemAnalysis.summary)
            appendLine("What's Working:")
            report.systemAnalysis.whatsWorking.forEach { appendLine("  - $it") }
            appendLine("What Needs Attention:")
            report.systemAnalysis.whatsNotWorking.forEach { appendLine("  - $it") }
            appendLine()
            
            appendLine("--- DOMAIN INTELLIGENCE ---")
            val domains = mutableListOf(report.productIntelligence, report.engagement, report.retention, report.monetization)
            report.discovery?.let { domains.add(it) }
            
            domains.forEach { domain ->
                appendLine("[${domain.domainName}] - ${domain.status}")
                if (domain.whatsWorking.isNotEmpty()) {
                    appendLine("  Highlights:")
                    domain.whatsWorking.forEach { appendLine("    - $it") }
                }
                if (domain.recommendedActions.isNotEmpty()) {
                    appendLine("  Recommendations:")
                    domain.recommendedActions.forEach { appendLine("    - $it") }
                }
                appendLine()
            }
            
            appendLine("--- PERSONALIZATION ---")
            appendLine(report.personalization.performanceSummary)
            appendLine("  Baseline: ${report.personalization.baselineScore}")
            appendLine("  Current: ${report.personalization.currentScore}")
            appendLine("  Samples: ${report.personalization.sampleCount}")
            appendLine()
            
            appendLine("--- RISKS AND REGRESSIONS ---")
            if (report.risksAndRegressions.isEmpty()) {
                appendLine("No active production regressions detected.")
            } else {
                report.risksAndRegressions.forEach { alert ->
                    appendLine("  * [${alert.severity}] ${alert.id}: ${alert.affectedMetric} regression (${alert.change})")
                }
            }
            
            if (report.openQuestions.isNotEmpty()) {
                appendLine()
                appendLine("--- OPEN INTELLIGENCE QUESTIONS ---")
                report.openQuestions.forEach { appendLine("  - $it") }
            }
            
            if (report.recommendedAreasToWatch.isNotEmpty()) {
                appendLine()
                appendLine("--- RECOMMENDED AREAS TO WATCH ---")
                report.recommendedAreasToWatch.forEach { appendLine("  - $it") }
            }
        }
    }

    /**
     * Exports a complete intelligence reconstruction package into formatted JSON.
     */
    fun exportReconstructionPackage(pkg: com.example.data.blueprint.ReconstructionPackage): String {
        val adapter = moshi.adapter(com.example.data.blueprint.ReconstructionPackage::class.java).indent("  ")
        return adapter.toJson(pkg)
    }

    /**
     * Exports a complete suggested improvement package (Layer 2).
     */
    fun exportImprovementPackage(
        improvement: SuggestedImprovement,
        finding: Finding?,
        artifact: com.example.data.blueprint.BlueprintArtifact?,
        explanation: IntelligenceExplanation?
    ): String {
        val adapter = moshi.adapter(ImprovementPackageContainer::class.java).indent("  ")
        val container = ImprovementPackageContainer(
            improvement = improvement,
            finding = finding,
            artifact = artifact,
            explanation = explanation,
            exportedAt = System.currentTimeMillis()
        )
        return adapter.toJson(container)
    }

    /**
     * Exports report and blueprint into Plain Text format.
     */
    fun exportToText(
        report: ClosedLoopReport, 
        blueprint: StrategyBlueprint? = null,
        manifest: BlueprintImplementationManifest? = null
    ): String {
        return buildString {
            appendLine("=== AURA CLOSED LOOP INTELLIGENCE REPORT ===")
            appendLine("Report ID: ${report.id}")
            appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))}")
            appendLine()

            if (manifest != null) {
                appendLine("--- IMPLEMENTATION & VALIDATION STATUS ---")
                appendLine("Manifest ID         : ${manifest.manifestId}")
                appendLine("Implementation Status: ${manifest.manifestStatus.name}")
                appendLine("Approval State      : ${manifest.approvalState.name}")
                appendLine("Validation Result   : ${manifest.validationStatus.name}")
                appendLine("Closed Loop Outcome : ${manifest.closedLoopOutcome?.name ?: "PENDING"}")
                appendLine("Measured Post-Score : ${manifest.postImplementationScore ?: "N/A"}")
                appendLine("Causality Summary   : ${manifest.causalValidationSummary}")
                appendLine("Long-term Monitoring:")
                manifest.monitoringRequirements.forEach { appendLine("  - $it") }
                appendLine()
            }
            
            appendLine("--- EVIDENCE SUMMARY ---")
            appendLine("Production Evidence Samples : ${report.productionSampleCount}")
            appendLine("Production Evidence Quality : ${"%.2f".format(report.productionEvidenceQuality * 100)}%")
            appendLine("Experimental Evidence Count : ${report.experimentalSampleCount}")
            appendLine("Experimental Quality        : ${"%.2f".format(report.experimentalEvidenceQuality * 100)}%")
            appendLine("Simulation Evidence Count   : ${report.simulationSampleCount}")
            appendLine("Simulation Quality          : ${"%.2f".format(report.simulationEvidenceQuality * 100)}%")
            appendLine()

            appendLine("--- METRICS & OUTCOME ---")
            appendLine("Baseline Score          : ${"%.2f".format(report.baselineScore)}")
            appendLine("Measured Score          : ${"%.2f".format(report.measuredScore)}")
            appendLine("Target Score            : ${"%.2f".format(report.targetScore)}")
            appendLine("Target Validity         : ${report.targetValidity.name}")
            appendLine("Outcome Classification  : ${report.outcomeClassification.name}")
            appendLine("Production Confidence   : ${"%.2f".format(report.productionConfidence * 100)}%")
            appendLine("Overall Confidence      : ${"%.2f".format(report.overallConfidence * 100)}%")
            appendLine()

            appendLine("--- PRODUCTION VALIDATION ---")
            appendLine("Production Evidence Samples    : ${report.productionSampleCount}")
            appendLine("Production Improvement Established : ${if (report.productionImprovementEstablished) "YES" else "NO"}")
            appendLine("Production Regression Established  : ${if (report.productionRegressionEstablished) "YES" else "NO"}")
            if (report.productionSampleCount == 0) {
                appendLine("CONCLUSION: No production improvement has been established.")
            } else {
                appendLine("CONCLUSION: ${report.summaryMessage}")
            }
            appendLine()

            appendLine("--- EVIDENCE INTERPRETATION ---")
            appendLine("[WHAT IS KNOWN]")
            report.knownFacts.forEach { appendLine("  - $it") }
            appendLine("[WHAT IS INFERRED]")
            report.inferences.forEach { appendLine("  - $it") }
            appendLine("[EXPERIMENTAL FINDINGS]")
            report.experimentalFindings.forEach { appendLine("  - $it") }
            appendLine("[SIMULATED FINDINGS]")
            report.simulatedFindings.forEach { appendLine("  - $it") }
            appendLine("[WHAT IS UNKNOWN]")
            report.unknowns.forEach { appendLine("  - $it") }
            appendLine()

            if (blueprint != null) {
                appendLine("--- STRATEGY BLUEPRINT ---")
                appendLine("Title: ${blueprint.title}")
                appendLine("Notice: ${blueprint.recommendationNotice}")
                appendLine("Validation State: ${blueprint.validationState.name}")
                appendLine("Requires Prod Validation: ${if (blueprint.requiresProductionValidation) "YES" else "NO"}")
                appendLine("Risk Assessment: ${blueprint.riskAssessment}")
                appendLine()
                appendLine("[1. BLUEPRINT IDENTITY]")
                appendLine("  ID: ${blueprint.identity.blueprintId}")
                appendLine("  Version: ${blueprint.identity.version}")
                appendLine("  Status: ${blueprint.identity.status.name}")
                appendLine("  Trigger: ${blueprint.identity.trigger}")
                appendLine()
                appendLine("[2. PROBLEM DIAGNOSIS]")
                appendLine("  Problem: ${blueprint.diagnosis.problemStatement}")
                appendLine("  Belief: ${blueprint.diagnosis.beliefDescription}")
                appendLine("  Component: ${blueprint.diagnosis.affectedComponent}")
                appendLine("  Confidence: ${"%.1f".format(blueprint.diagnosis.diagnosticConfidence * 100)}%")
                appendLine("  Known Facts:")
                blueprint.diagnosis.knownFacts.forEach { appendLine("    - $it") }
                appendLine("  Inferences:")
                blueprint.diagnosis.inferences.forEach { appendLine("    - $it") }
                appendLine("  Hypotheses:")
                blueprint.diagnosis.hypotheses.forEach { appendLine("    - $it") }
                appendLine()
                appendLine("[3. EVIDENCE]")
                appendLine("  Production Samples: ${blueprint.evidence.productionCount}")
                appendLine("  Experimental Samples: ${blueprint.evidence.experimentalCount}")
                appendLine("  Simulation Samples: ${blueprint.evidence.simulationCount}")
                appendLine("  Provenance: ${blueprint.evidence.provenance}")
                appendLine("  Quality: ${"%.1f".format(blueprint.evidence.evidenceQuality * 100)}%")
                appendLine()
                appendLine("[4. BASELINE STATE]")
                appendLine("  Engagement Score: ${blueprint.baselineState.engagementScore}")
                appendLine("  Pairwise Weights: ${blueprint.baselineState.pairwiseWeights}")
                appendLine("  AI Skip Weights: ${blueprint.baselineState.aiSkipWeights}")
                appendLine("  Taste DNA Weights: ${blueprint.baselineState.tasteDnaWeights}")
                appendLine()
                appendLine("[5. TARGET STATE]")
                appendLine("  Target Metric: ${blueprint.targetState.targetMetric}")
                appendLine("  Target Value: ${blueprint.targetState.targetValue}")
                appendLine("  Desired Outcome: ${blueprint.targetState.desiredBehavioralOutcome}")
                appendLine()
                appendLine("[6. STRATEGY]")
                appendLine("  Selected Strategy: ${blueprint.strategySelection.selectedStrategy}")
                appendLine("  Rationale: ${blueprint.strategySelection.rationale}")
                appendLine()
                appendLine("[7. PROPOSED MODIFICATIONS]")
                blueprint.proposedModifications.forEach { m ->
                    appendLine("  * [${m.modificationType.name}] ${m.component}.${m.parameter}: ${m.currentValue} -> ${m.proposedValue} (${m.delta}) | ${m.reason}")
                }
                appendLine()
                appendLine("[8. TASTE DNA MODIFICATIONS]")
                blueprint.tasteDnaModifications.forEach { td ->
                    appendLine("  * [${td.modificationType.name}] ${td.dimension}: ${td.previousValue} -> ${td.proposedValue} (${td.delta}) | Auto: ${td.isAutomatic}")
                }
                appendLine()
                appendLine("[9. RECOMMENDATION ENGINE MODIFICATIONS]")
                blueprint.recommendationEngineModifications.forEach { rm ->
                    appendLine("  * [${rm.modificationType.name}] ${rm.parameterOrWeightName}: ${rm.previousValue} -> ${rm.proposedValue} (${rm.delta})")
                }
                appendLine()
                appendLine("[10. EXECUTION PLAN]")
                appendLine("  Execution Order: ${blueprint.executionPlan.executionOrderDescription}")
                appendLine("  Persistence: ${blueprint.executionPlan.persistenceRequirements}")
                appendLine("  Reversible: ${blueprint.executionPlan.isReversible}")
                blueprint.executionPlan.intendedActions.forEach { act ->
                    appendLine("  Step ${act.stepOrder}: ${act.action} (${act.targetComponent})")
                }
                appendLine()
                appendLine("[11. EXPERIMENT DESIGN]")
                appendLine("  Control: ${blueprint.experimentDesign.controlGroupConfig}")
                appendLine("  Experimental: ${blueprint.experimentDesign.experimentalGroupConfig}")
                appendLine("  Sample Requirements: ${blueprint.experimentDesign.sampleRequirements}")
                appendLine("  Rollback Condition: ${blueprint.experimentDesign.rollbackCondition}")
                appendLine()
                appendLine("[12. EXPECTED OUTCOME]")
                appendLine("  Expected Improvement: ${blueprint.expectedOutcome.expectedImprovement}")
                appendLine("  Timeline: ${blueprint.expectedOutcome.timeline}")
                appendLine("  Risk Level: ${blueprint.expectedOutcome.riskLevel}")
                appendLine("  Risk Mitigation: ${blueprint.expectedOutcome.riskMitigation}")
                appendLine("  Next Recommendation: ${blueprint.nextExperimentRecommendation}")
                appendLine("  Production Validation Reqs:")
                blueprint.productionValidationRequirements.forEach { appendLine("    - $it") }
                appendLine()
                appendLine("[13. ACTUAL OUTCOME]")
                appendLine("  Measured Score: ${blueprint.actualOutcome.measuredScore}")
                appendLine("  Classification: ${blueprint.actualOutcome.outcomeClassification}")
                appendLine()
                appendLine("[14. LEARNING]")
                blueprint.learning.keyInsights.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("[15. VERSION HISTORY]")
                blueprint.versionHistory.forEach { v ->
                    appendLine("  - v${v.version} by ${v.authorOrEngine}: ${v.notes}")
                }
            }
        }
    }

    /**
     * Exports report and blueprint into Markdown format.
     */
    fun exportToMarkdown(
        report: ClosedLoopReport, 
        blueprint: StrategyBlueprint? = null,
        manifest: BlueprintImplementationManifest? = null
    ): String {
        return buildString {
            appendLine("# Aura Closed Loop Intelligence Report")
            appendLine("**Report ID:** `${report.id}`  ")
            appendLine("**Date:** `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))}`  ")
            appendLine()

            if (manifest != null) {
                appendLine("## 🛠️ Implementation & Validation Status")
                appendLine("- **Manifest ID:** `${manifest.manifestId}`")
                appendLine("- **Implementation Status:** **`${manifest.manifestStatus.name}`**")
                appendLine("- **Approval State:** `${manifest.approvalState.name}`")
                appendLine("- **Validation Result:** **`${manifest.validationStatus.name}`**")
                appendLine("- **Closed Loop Outcome:** `${manifest.closedLoopOutcome?.name ?: "PENDING"}`")
                appendLine("- **Measured Post-Score:** `${manifest.postImplementationScore ?: "N/A"}`")
                appendLine("- **Causality Summary:** ${manifest.causalValidationSummary}")
                appendLine()
                appendLine("### Long-term Monitoring Requirements")
                manifest.monitoringRequirements.forEach { appendLine("- $it") }
                appendLine()
            }

            appendLine("## 📊 Evidence Summary")
            appendLine("| Evidence Tier | Sample Count | Data Quality | Eligible for Prod Outcome |")
            appendLine("|---|---|---|---|")
            appendLine("| **Production** | `${report.productionSampleCount}` | `${"%.1f".format(report.productionEvidenceQuality * 100)}%` | ${if (report.productionSampleCount > 0) "✅ YES" else "❌ NO"} |")
            appendLine("| **Experimental** | `${report.experimentalSampleCount}` | `${"%.1f".format(report.experimentalEvidenceQuality * 100)}%` | ❌ NO |")
            appendLine("| **Simulation** | `${report.simulationSampleCount}` | `${"%.1f".format(report.simulationEvidenceQuality * 100)}%` | ❌ NO |")
            appendLine()

            appendLine("## 🎯 Metrics & Outcome")
            appendLine("- **Baseline Score:** `${"%.2f".format(report.baselineScore)}`")
            appendLine("- **Measured Score:** `${"%.2f".format(report.measuredScore)}`")
            appendLine("- **Target Score:** `${"%.2f".format(report.targetScore)}` (`${report.targetValidity.name}`)")
            appendLine("- **Outcome Classification:** **`${report.outcomeClassification.name}`**")
            appendLine("- **Production Confidence:** `${"%.1f".format(report.productionConfidence * 100)}%`")
            appendLine()

            appendLine("## 🛡️ Production Validation Status")
            appendLine("- **Production Evidence Samples:** `${report.productionSampleCount}`")
            appendLine("- **Production Improvement Established:** **`${if (report.productionImprovementEstablished) "YES" else "NO"}`**")
            appendLine("- **Production Regression Established:** **`${if (report.productionRegressionEstablished) "YES" else "NO"}`**")
            appendLine()
            if (report.productionSampleCount == 0) {
                appendLine("> **CRITICAL:** No production improvement has been established due to 0 production evidence samples.")
            } else {
                appendLine("> **Summary:** ${report.summaryMessage}")
            }
            appendLine()

            appendLine("## 🔍 Evidence Interpretation")
            appendLine("### What Is Known")
            report.knownFacts.forEach { appendLine("- $it") }
            appendLine("### What Is Inferred")
            report.inferences.forEach { appendLine("- $it") }
            appendLine("### Experimental Findings")
            report.experimentalFindings.forEach { appendLine("- $it") }
            appendLine("### Simulated Findings")
            report.simulatedFindings.forEach { appendLine("- $it") }
            appendLine("### What Is Unknown")
            report.unknowns.forEach { appendLine("- $it") }
            appendLine()

            if (blueprint != null) {
                appendLine("## 📐 Strategy Blueprint: ${blueprint.title}")
                appendLine("> **Notice:** `${blueprint.recommendationNotice}`")
                appendLine()
                appendLine("### 1. Blueprint Identity")
                appendLine("- **ID:** `${blueprint.identity.blueprintId}`")
                appendLine("- **Version:** `${blueprint.identity.version}`")
                appendLine("- **Trigger:** `${blueprint.identity.trigger}`")
                appendLine("- **Status:** `${blueprint.identity.status.name}`")
                appendLine()
                appendLine("### 2. Problem Diagnosis")
                appendLine("- **Problem Statement:** ${blueprint.diagnosis.problemStatement}")
                appendLine("- **Belief:** ${blueprint.diagnosis.beliefDescription}")
                appendLine("- **Affected Component:** `${blueprint.diagnosis.affectedComponent}`")
                appendLine("- **Diagnostic Confidence:** `${"%.1f".format(blueprint.diagnosis.diagnosticConfidence * 100)}%`")
                appendLine("- **Known Facts:**")
                blueprint.diagnosis.knownFacts.forEach { appendLine("  - $it") }
                appendLine("- **Inferences:**")
                blueprint.diagnosis.inferences.forEach { appendLine("  - $it") }
                appendLine("- **Hypotheses:**")
                blueprint.diagnosis.hypotheses.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("### 3. Evidence")
                appendLine("- **Production Samples:** `${blueprint.evidence.productionCount}`")
                appendLine("- **Experimental Samples:** `${blueprint.evidence.experimentalCount}`")
                appendLine("- **Simulation Samples:** `${blueprint.evidence.simulationCount}`")
                appendLine("- **Provenance:** `${blueprint.evidence.provenance}`")
                appendLine("- **Quality:** `${"%.1f".format(blueprint.evidence.evidenceQuality * 100)}%`")
                appendLine()
                appendLine("### 4. Baseline State")
                appendLine("- **Engagement Score:** `${blueprint.baselineState.engagementScore}`")
                appendLine("- **Pairwise Weights:** `${blueprint.baselineState.pairwiseWeights}`")
                appendLine("- **AI Skip Weights:** `${blueprint.baselineState.aiSkipWeights}`")
                appendLine("- **Taste DNA Weights:** `${blueprint.baselineState.tasteDnaWeights}`")
                appendLine()
                appendLine("### 5. Target State")
                appendLine("- **Target Metric:** `${blueprint.targetState.targetMetric}`")
                appendLine("- **Target Value:** `${blueprint.targetState.targetValue}`")
                appendLine("- **Desired Outcome:** ${blueprint.targetState.desiredBehavioralOutcome}")
                appendLine()
                appendLine("### 6. Strategy")
                appendLine("- **Selected Strategy:** ${blueprint.strategySelection.selectedStrategy}")
                appendLine("- **Rationale:** ${blueprint.strategySelection.rationale}")
                appendLine()
                appendLine("### 7. Proposed Modifications")
                if (blueprint.proposedModifications.isEmpty()) {
                    appendLine("_None proposed_")
                } else {
                    blueprint.proposedModifications.forEach { m ->
                        appendLine("- **[${m.modificationType.name}]** `${m.component}.${m.parameter}`: `${m.currentValue}` -> `${m.proposedValue}` (`${m.delta}`) — ${m.reason}")
                    }
                }
                appendLine()
                appendLine("### 8. Taste DNA Modifications")
                blueprint.tasteDnaModifications.forEach { td ->
                    appendLine("- **[${td.modificationType.name}]** `${td.dimension}`: `${td.previousValue}` -> `${td.proposedValue}` (`${td.delta}`) | Auto: `${td.isAutomatic}`")
                }
                appendLine()
                appendLine("### 9. Recommendation Engine Modifications")
                blueprint.recommendationEngineModifications.forEach { rm ->
                    appendLine("- **[${rm.modificationType.name}]** `${rm.parameterOrWeightName}`: `${rm.previousValue}` -> `${rm.proposedValue}` (`${rm.delta}`)")
                }
                appendLine()
                appendLine("### 10. Execution Plan")
                appendLine("- **Execution Order:** ${blueprint.executionPlan.executionOrderDescription}")
                appendLine("- **Persistence:** `${blueprint.executionPlan.persistenceRequirements}`")
                appendLine("- **Reversible:** `${blueprint.executionPlan.isReversible}`")
                blueprint.executionPlan.intendedActions.forEach { act ->
                    appendLine("  1. **Step ${act.stepOrder}:** ${act.action} (`${act.targetComponent}`)")
                }
                appendLine()
                appendLine("### 11. Experiment Design")
                appendLine("- **Control:** ${blueprint.experimentDesign.controlGroupConfig}")
                appendLine("- **Experimental:** ${blueprint.experimentDesign.experimentalGroupConfig}")
                appendLine("- **Sample Requirements:** `${blueprint.experimentDesign.sampleRequirements}`")
                appendLine("- **Rollback Condition:** ${blueprint.experimentDesign.rollbackCondition}")
                appendLine()
                appendLine("### 12. Expected Outcome")
                appendLine("- **Expected Improvement:** ${blueprint.expectedOutcome.expectedImprovement}")
                appendLine("- **Timeline:** `${blueprint.expectedOutcome.timeline}`")
                appendLine("- **Risk Level:** `${blueprint.expectedOutcome.riskLevel}`")
                appendLine("- **Risk Mitigation:** ${blueprint.expectedOutcome.riskMitigation}")
                appendLine("- **Next Recommended Step:** ${blueprint.nextExperimentRecommendation}")
                appendLine("- **Production Validation Requirements:**")
                blueprint.productionValidationRequirements.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("### 13. Actual Outcome")
                appendLine("- **Measured Score:** `${blueprint.actualOutcome.measuredScore}`")
                appendLine("- **Outcome Classification:** `${blueprint.actualOutcome.outcomeClassification}`")
                appendLine()
                appendLine("### 14. Learning")
                blueprint.learning.keyInsights.forEach { appendLine("- $it") }
                appendLine()
                appendLine("### 15. Version History")
                blueprint.versionHistory.forEach { v ->
                    appendLine("- **v${v.version}** by `${v.authorOrEngine}`: ${v.notes}")
                }
            }
        }
    }

    /**
     * Exports report and blueprint into HTML format.
     */
    fun exportToHtml(
        report: ClosedLoopReport, 
        blueprint: StrategyBlueprint? = null,
        manifest: BlueprintImplementationManifest? = null
    ): String {
        val md = exportToMarkdown(report, blueprint, manifest)
        // Convert Markdown text lines to simple HTML representation
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head><meta charset=\"utf-8\"><title>Closed Loop Intelligence Report</title>")
            appendLine("<style>")
            appendLine("body { font-family: -apple-system, Roboto, sans-serif; margin: 24px; color: #1c1b1f; background-color: #fcf8f6; }")
            appendLine("h1, h2, h3 { color: #6750a4; }")
            appendLine("table { border-collapse: collapse; width: 100%; margin-bottom: 16px; }")
            appendLine("th, td { border: 1px solid #e7e0ec; padding: 8px 12px; text-align: left; }")
            appendLine("th { background-color: #f3edf7; }")
            appendLine(".alert { background-color: #ffdad6; color: #410002; padding: 12px; border-radius: 8px; margin: 16px 0; }")
            appendLine(".success { background-color: #e8def8; color: #1d192b; padding: 12px; border-radius: 8px; margin: 16px 0; }")
            appendLine("</style></head><body>")
            appendLine("<h1>Aura Closed Loop Intelligence Report</h1>")
            appendLine("<p><b>Report ID:</b> ${report.id}<br><b>Date:</b> ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))}</p>")

            if (manifest != null) {
                appendLine("<div class=\"success\"><strong>Implementation Status: ${manifest.manifestStatus.name}</strong><br>")
                appendLine("Approval State: ${manifest.approvalState.name}<br>")
                appendLine("Validation Result: ${manifest.validationStatus.name}<br>")
                appendLine("Closed Loop Outcome: ${manifest.closedLoopOutcome?.name ?: "PENDING"}<br>")
                appendLine("Causality: ${manifest.causalValidationSummary}</div>")
                
                appendLine("<h3>Long-term Monitoring</h3><ul>")
                manifest.monitoringRequirements.forEach { appendLine("<li>$it</li>") }
                appendLine("</ul>")
            }

            if (report.productionSampleCount == 0) {
                appendLine("<div class=\"alert\"><strong>Production Evidence Samples: 0</strong><br>Production Improvement Established: NO<br><em>Conclusion: No production improvement has been established.</em></div>")
            } else {
                appendLine("<div class=\"success\"><strong>Production Evidence Samples: ${report.productionSampleCount}</strong><br>Production Improvement Established: ${if (report.productionImprovementEstablished) "YES" else "NO"}<br><em>Summary: ${report.summaryMessage}</em></div>")
            }

            appendLine("<h2>Metrics & Outcome</h2>")
            appendLine("<ul>")
            appendLine("<li><b>Baseline Score:</b> ${"%.2f".format(report.baselineScore)}</li>")
            appendLine("<li><b>Measured Score:</b> ${"%.2f".format(report.measuredScore)}</li>")
            appendLine("<li><b>Target Score:</b> ${"%.2f".format(report.targetScore)} (${report.targetValidity.name})</li>")
            appendLine("<li><b>Outcome Classification:</b> ${report.outcomeClassification.name}</li>")
            appendLine("<li><b>Production Confidence:</b> ${"%.1f".format(report.productionConfidence * 100)}%</li>")
            appendLine("</ul>")

            appendLine("<h2>Evidence Breakdown</h2>")
            appendLine("<table><tr><th>Tier</th><th>Sample Count</th><th>Quality</th><th>Prod Eligible</th></tr>")
            appendLine("<tr><td>Production</td><td>${report.productionSampleCount}</td><td>${"%.1f".format(report.productionEvidenceQuality * 100)}%</td><td>${if (report.productionSampleCount > 0) "YES" else "NO"}</td></tr>")
            appendLine("<tr><td>Experimental</td><td>${report.experimentalSampleCount}</td><td>${"%.1f".format(report.experimentalEvidenceQuality * 100)}%</td><td>NO</td></tr>")
            appendLine("<tr><td>Simulation</td><td>${report.simulationSampleCount}</td><td>${"%.1f".format(report.simulationEvidenceQuality * 100)}%</td><td>NO</td></tr>")
            appendLine("</table>")

            appendLine("<h2>Interpretation</h2>")
            appendLine("<h3>What Is Known</h3><ul>")
            report.knownFacts.forEach { appendLine("<li>$it</li>") }
            appendLine("</ul><h3>What Is Inferred</h3><ul>")
            report.inferences.forEach { appendLine("<li>$it</li>") }
            appendLine("</ul><h3>What Is Unknown</h3><ul>")
            report.unknowns.forEach { appendLine("<li>$it</li>") }
            appendLine("</ul>")

            if (blueprint != null) {
                appendLine("<h2>Strategy Blueprint: ${blueprint.title}</h2>")
                appendLine("<div class=\"alert\"><strong>Recommendation Notice:</strong> ${blueprint.recommendationNotice}</div>")
                appendLine("<p><b>Blueprint ID:</b> ${blueprint.identity.blueprintId}<br>")
                appendLine("<b>Validation State:</b> ${blueprint.validationState.name}<br>")
                appendLine("<b>Risk Assessment:</b> ${blueprint.riskAssessment}</p>")
                appendLine("<h3>Proposed Modifications</h3><ul>")
                if (blueprint.proposedModifications.isEmpty()) {
                    appendLine("<li><em>None proposed</em></li>")
                } else {
                    blueprint.proposedModifications.forEach { m ->
                        appendLine("<li><b>[${m.modificationType.name}]</b> ${m.component}.${m.parameter}: ${m.currentValue} &rarr; ${m.proposedValue} (${m.delta}) &mdash; ${m.reason}</li>")
                    }
                }
                appendLine("</ul>")
            }

            appendLine("</body></html>")
        }
    }

    /**
     * Renders report and optional blueprint into a multi-page PDF document saved to [outputFile].
     */
    fun generatePdfReport(
        context: Context,
        report: ClosedLoopReport,
        blueprint: StrategyBlueprint? = null,
        outputFile: File,
        reportType: ModularReportType = ModularReportType.COMBINED_PACKAGE
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 dimensions in points
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        val titlePaint = Paint().apply {
            color = Color.rgb(103, 80, 164) // Aura Purple
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.rgb(28, 27, 31)
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val subHeaderPaint = Paint().apply {
            color = Color.rgb(73, 69, 79)
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }

        var y = 50f
        val xMargin = 50f
        val lineSpacing = 16f

        // Helper to check for page break
        fun checkPageBreak(needed: Float) {
            if (y + needed > 780f) {
                pdfDocument.finishPage(page)
                val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, pdfDocument.pages.size + 1).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        // 1. Report Header
        canvas.drawText("AURA INTELLIGENCE SYSTEM", xMargin, y, titlePaint)
        y += 24f
        canvas.drawText(reportType.displayName.uppercase(), xMargin, y, headerPaint)
        y += 24f
        canvas.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}", xMargin, y, paint)
        canvas.drawText("Report ID: ${report.id.take(12)}...", 350f, y, paint)
        y += 30f

        // 2. Metadata / Context Summary (Always include)
        canvas.drawRect(xMargin, y, 545f, y + 45f, Paint().apply { color = Color.rgb(243, 237, 247) })
        canvas.drawText("CONTEXT METADATA", xMargin + 10f, y + 18f, subHeaderPaint)
        canvas.drawText("Intelligence Module: AuraCore v1.0", xMargin + 10f, y + 36f, paint)
        canvas.drawText("Validation State: ${blueprint?.validationState?.name ?: "EVALUATION_PENDING"}", 300f, y + 36f, paint)
        y += 60f

        // 3. Evidence Integrity Banner (Critical Requirement)
        if (reportType == ModularReportType.EVIDENCE_INTEGRITY || reportType == ModularReportType.COMBINED_PACKAGE || reportType == ModularReportType.CLOSED_LOOP_INTELLIGENCE) {
            checkPageBreak(80f)
            val bannerColor = if (report.productionSampleCount == 0) Color.rgb(255, 218, 214) else Color.rgb(232, 222, 248)
            canvas.drawRect(xMargin, y, 545f, y + 65f, Paint().apply { color = bannerColor })
            
            val bannerTextPaint = Paint().apply {
                color = if (report.productionSampleCount == 0) Color.rgb(104, 0, 8) else Color.rgb(29, 25, 43)
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }

            canvas.drawText("PRODUCTION EVIDENCE SAMPLES: ${report.productionSampleCount}", xMargin + 10f, y + 18f, bannerTextPaint)
            canvas.drawText("PRODUCTION IMPROVEMENT ESTABLISHED: ${if (report.productionImprovementEstablished) "YES" else "NO"}", xMargin + 10f, y + 34f, bannerTextPaint)
            canvas.drawText("PRODUCTION REGRESSION ESTABLISHED: ${if (report.productionRegressionEstablished) "YES" else "NO"}", xMargin + 10f, y + 50f, bannerTextPaint)
            y += 85f
        }

        // 4. Metrics & Scores
        if (reportType in listOf(ModularReportType.ENGAGEMENT_INTELLIGENCE, ModularReportType.CLOSED_LOOP_INTELLIGENCE, ModularReportType.COMBINED_PACKAGE)) {
            checkPageBreak(100f)
            canvas.drawText("METRICS & PERFORMANCE OUTCOME", xMargin, y, headerPaint)
            y += 20f
            canvas.drawText("Baseline Score: ${"%.2f".format(report.baselineScore)}", xMargin, y, paint)
            canvas.drawText("Measured Score: ${"%.2f".format(report.measuredScore)}", xMargin + 180f, y, paint)
            canvas.drawText("Target: ${"%.2f".format(report.targetScore)} (${report.targetValidity.name})", xMargin + 350f, y, paint)
            y += lineSpacing
            canvas.drawText("Outcome Classification: ${report.outcomeClassification.name}", xMargin, y, paint)
            y += lineSpacing
            canvas.drawText("Production Confidence: ${"%.1f".format(report.productionConfidence * 100)}%", xMargin, y, paint)
            canvas.drawText("Overall Confidence: ${"%.1f".format(report.overallConfidence * 100)}%", xMargin + 180f, y, paint)
            y += 30f
        }

        // 5. Tiered Evidence Breakdown
        if (reportType in listOf(ModularReportType.EVIDENCE_INTEGRITY, ModularReportType.AGENT_INTELLIGENCE, ModularReportType.COMBINED_PACKAGE)) {
            checkPageBreak(100f)
            canvas.drawText("TIERED EVIDENCE BREAKDOWN", xMargin, y, headerPaint)
            y += 20f
            canvas.drawText("• PRODUCTION: ${report.productionSampleCount} samples (Quality: ${"%.1f".format(report.productionEvidenceQuality * 100)}%)", xMargin, y, paint)
            y += lineSpacing
            canvas.drawText("• EXPERIMENTAL: ${report.experimentalSampleCount} samples (Quality: ${"%.1f".format(report.experimentalEvidenceQuality * 100)}%)", xMargin, y, paint)
            y += lineSpacing
            canvas.drawText("• SIMULATION: ${report.simulationSampleCount} samples (Quality: ${"%.1f".format(report.simulationEvidenceQuality * 100)}%)", xMargin, y, paint)
            y += 30f
        }

        // 6. Agent Findings / Interpretation
        if (reportType in listOf(ModularReportType.AGENT_INTELLIGENCE, ModularReportType.PROTOTYPE_INTELLIGENCE, ModularReportType.COMBINED_PACKAGE)) {
            checkPageBreak(120f)
            canvas.drawText("INTELLIGENCE AGENT FINDINGS", xMargin, y, headerPaint)
            y += 20f
            
            canvas.drawText("KNOWN FACTS:", xMargin, y, subHeaderPaint)
            y += lineSpacing
            report.knownFacts.take(3).forEach { fact ->
                canvas.drawText(" - ${fact.take(85)}", xMargin, y, paint)
                y += lineSpacing
            }
            
            y += 4f
            canvas.drawText("INFERRED PATTERNS:", xMargin, y, subHeaderPaint)
            y += lineSpacing
            report.inferences.take(2).forEach { inf ->
                canvas.drawText(" - ${inf.take(85)}", xMargin, y, paint)
                y += lineSpacing
            }
            y += 20f
        }

        // 7. Strategy Blueprint Section
        if (blueprint != null && reportType in listOf(ModularReportType.STRATEGY_BLUEPRINT, ModularReportType.COMBINED_PACKAGE)) {
            checkPageBreak(200f)
            canvas.drawText("STRATEGY BLUEPRINT: ${blueprint.title.uppercase()}", xMargin, y, headerPaint)
            y += 20f
            canvas.drawText("Notice: ${blueprint.recommendationNotice}", xMargin, y, Paint().apply { color = if (blueprint.recommendationNotice.startsWith("NO")) Color.RED else Color.rgb(103, 80, 164); textSize = 11f; isFakeBoldText = true })
            y += lineSpacing
            canvas.drawText("Validation State: ${blueprint.validationState.name}", xMargin, y, paint)
            y += lineSpacing
            canvas.drawText("Next Step: ${blueprint.nextExperimentRecommendation.take(85)}", xMargin, y, paint)
            y += lineSpacing
            canvas.drawText("Problem Diagnosis: ${blueprint.description.take(85)}", xMargin, y, paint)
            y += lineSpacing
            canvas.drawText("Risk Assessment: ${blueprint.riskAssessment.take(85)}", xMargin, y, paint)
            y += 20f

            if (blueprint.proposedModifications.isNotEmpty()) {
                canvas.drawText("PROPOSED MODIFICATIONS:", xMargin, y, subHeaderPaint)
                y += lineSpacing
                blueprint.proposedModifications.take(4).forEach { mod ->
                    canvas.drawText(" • [${mod.modificationType.name}] ${mod.component}.${mod.parameter}: ${mod.currentValue} -> ${mod.proposedValue}", xMargin, y, paint)
                    y += lineSpacing
                }
                y += 10f
            }
            
            if (blueprint.tasteDnaModifications.isNotEmpty()) {
                canvas.drawText("TASTE DNA TUNING:", xMargin, y, subHeaderPaint)
                y += lineSpacing
                blueprint.tasteDnaModifications.take(2).forEach { mod ->
                    canvas.drawText(" • ${mod.dimension}: ${mod.previousValue} -> ${mod.proposedValue} (${mod.modificationType.name})", xMargin, y, paint)
                    y += lineSpacing
                }
                y += 10f
            }
        }

        // 7.5 Builder Instructions Implementation Section
        if (blueprint != null && reportType in listOf(ModularReportType.BUILDER_INSTRUCTIONS, ModularReportType.COMBINED_PACKAGE)) {
            checkPageBreak(180f)
            canvas.drawText("BUILDER INSTRUCTIONS: IMPLEMENTATION SEQUENCE", xMargin, y, headerPaint)
            y += 20f
            
            canvas.drawText("OBJECTIVE: ${blueprint.diagnosis.problemStatement.take(80)}", xMargin, y, subHeaderPaint)
            y += lineSpacing
            canvas.drawText("EVIDENCE TIER: ${blueprint.validationState.name}", xMargin, y, paint)
            y += lineSpacing
            
            val builderSet = blueprint.builderInstructions
            if (builderSet != null && builderSet.prompts.isNotEmpty()) {
                builderSet.prompts.forEach { prompt ->
                    checkPageBreak(60f)
                    canvas.drawText("STEP ${prompt.stepNumber}: ${prompt.title.uppercase()}", xMargin, y, subHeaderPaint)
                    y += lineSpacing
                    
                    // Multi-line prompt text drawing
                    val lines = prompt.promptText.split("\n").filter { it.isNotBlank() }.take(4)
                    lines.forEach { line ->
                        val cleanLine = if (line.length > 90) line.take(87) + "..." else line
                        canvas.drawText("  $cleanLine", xMargin, y, paint)
                        y += lineSpacing
                    }
                    y += 8f
                }
            } else {
                canvas.drawText("No builder instructions generated. Evidence may be insufficient.", xMargin, y, paint)
                y += lineSpacing
            }
            y += 20f
        }

        // 8. Unknowns & Limitations
        checkPageBreak(80f)
        canvas.drawText("LIMITATIONS & UNKNOWNS", xMargin, y, headerPaint)
        y += 20f
        report.unknowns.take(3).forEach { unk ->
            canvas.drawText(" ? ${unk.take(85)}", xMargin, y, paint)
            y += lineSpacing
        }

        pdfDocument.finishPage(page)
        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return outputFile
    }
}

/**
 * Data container for Moshi JSON serialization of export output.
 */
data class ExportContainer(
    val report: ClosedLoopReport? = null,
    val blueprint: StrategyBlueprint? = null,
    val manifest: BlueprintImplementationManifest? = null,
    val master: MasterIntelligenceReport? = null,
    val isReconstruction: Boolean = false,
    val entities: Map<String, Int> = emptyMap(),
    val exportedAt: String
)

/**
 * Tactical package containing all decision context for one improvement.
 */
@JsonClass(generateAdapter = true)
data class ImprovementPackageContainer(
    val improvement: SuggestedImprovement,
    val finding: Finding? = null,
    val artifact: com.example.data.blueprint.BlueprintArtifact? = null,
    val explanation: IntelligenceExplanation? = null,
    val exportedAt: Long,
    val schemaVersion: String = "1.0.0",
    val packageType: String = "SUGGESTED_IMPROVEMENT_PACKAGE"
)

package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImprovementDetailScreen(
    improvementId: String,
    repository: IntelligenceRepository,
    onBack: () -> Unit
) {
    val viewModel: IntelligenceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceViewModel(repository) as T
            }
        }
    )

    LaunchedEffect(improvementId) {
        viewModel.loadImprovement(improvementId)
    }

    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val explanation by viewModel.explanation.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showExportMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        AuraTopBar(
            title = "Improvement Review",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuraPurple)
                }
            },
            actions = {
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(Icons.Default.IosShare, contentDescription = "Export Options", tint = AuraPurple)
                }
                
                DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Export technical Blueprint") },
                        onClick = { 
                            viewModel.exportBlueprint(context, improvementId)
                            showExportMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Improvement Package") },
                        onClick = { 
                            viewModel.exportImprovementPackage(context, improvementId)
                            showExportMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Reconstruction Package") },
                        onClick = { 
                            viewModel.exportReconstructionPackage(context, improvementId)
                            showExportMenu = false 
                        }
                    )
                }
            },
            showLogo = false
        )

        when (val s = state) {
            is ImprovementDetailState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AuraPurple)
                }
            }
            is ImprovementDetailState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Color.Red)
                }
            }
            is ImprovementDetailState.Success -> {
                val imp = s.improvement
                val artifact = s.artifact
                val validation = s.validation

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ImprovementHeader(imp)

                    // VISUAL PROGRESSION
                    LifecycleIndicator(imp.status, modifier = Modifier.padding(vertical = 12.dp))

                    // READINESS STATUS
                    ImplementationReadinessBanner(imp.status, validation)

                    // THE RECOMMENDATION
                    SectionCard("The Recommendation") {
                        Text(
                            text = imp.summary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AuraOnSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "WHY AURA RECOMMENDS THIS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = AuraPurple.copy(alpha = 0.6f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = imp.rationale,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraOnSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }

                    // 1. WHAT SHOULD CHANGE?
                    SectionCard("Proposed Changes") {
                        imp.proposedChanges.modifications.forEach { mod ->
                            ProposedChangeItem(mod)
                        }
                    }

                    // 2. EVIDENCE
                    SectionCard("Evidence & Confidence") {
                        val report = imp.technicalDetails?.closedLoopReport
                        if (report != null) {
                            EvidenceSummaryView(
                                summary = EvidenceSummary(
                                    sampleCount = report.productionSampleCount,
                                    baseline = report.baselineScore,
                                    currentResult = report.measuredScore,
                                    change = report.measuredScore - report.baselineScore,
                                    evidenceAge = "Current",
                                    confidence = imp.confidence,
                                    hasRegression = report.productionRegressionEstablished
                                ),
                                onWhyConfidenceClick = { viewModel.askAuraToExplain(imp) }
                            )
                        } else {
                            Text("Evidence details not available.", color = AuraOnSurfaceVariant)
                        }
                    }

                    // 3. IMPACT & RISK
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionCard("Expected Impact", modifier = Modifier.weight(1f)) {
                            Text(imp.expectedImpact, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        SectionCard("Risk", modifier = Modifier.weight(1f)) {
                            RiskSection(imp.risk)
                        }
                    }

                    // 4. WHAT WILL NOT CHANGE?
                    SectionCard("What will not change?") {
                        Text(imp.whatWillNotChange, fontSize = 14.sp, color = AuraOnSurfaceVariant)
                    }

                    // TECHNICAL & WORKFLOW SECTIONS
                    if (artifact != null) {
                        if (imp.status in listOf(IntelligenceLifecycleState.APPROVED, IntelligenceLifecycleState.IMPLEMENTATION_PLANNED, IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS, IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE)) {
                            ImplementationWorkflowSection(
                                imp = imp,
                                artifact = artifact,
                                runs = s.runs,
                                results = s.verificationResults,
                                onStartImplementation = { viewModel.startImplementation(it) },
                                onCompleteImplementation = { runId, notes, files -> viewModel.completeImplementation(runId, notes, files) },
                                onStartVerification = { runId -> viewModel.startVerification(imp.id, runId) },
                                onRecordVerification = { runId, b, t, r, d, s, ac, td -> 
                                    viewModel.recordVerificationResult(imp.id, runId, b, t, r, d, s, ac, td) 
                                }
                            )
                        }

                        if (imp.status in listOf(IntelligenceLifecycleState.VERIFICATION_IN_PROGRESS, IntelligenceLifecycleState.VERIFICATION_PASSED, IntelligenceLifecycleState.MONITORING, IntelligenceLifecycleState.VALIDATED)) {
                            MonitoringValidationSection(
                                imp = imp,
                                sessions = s.monitoringSessions,
                                validationResults = s.validationResults,
                                onUpdateMonitoring = { sessionId, samples, value -> 
                                    viewModel.updateMonitoring(sessionId, samples, value)
                                }
                            )
                        }

                        if (imp.status == IntelligenceLifecycleState.REGRESSION_DETECTED || s.regressionAlerts.isNotEmpty()) {
                            RegressionResponseSection(
                                imp = imp,
                                alerts = s.regressionAlerts,
                                rollbackRuns = s.rollbackRuns,
                                onInvestigate = { viewModel.investigateRegression(it) },
                                onMarkFalsePositive = { id, reason -> viewModel.markRegressionFalsePositive(id, reason) },
                                onApproveRollback = { viewModel.approveRollback(it) },
                                onExecuteRollback = { viewModel.executeRollback(it) },
                                onCreateCorrective = { viewModel.createCorrectiveImprovement(it) },
                                onWhyClick = { viewModel.askAuraToExplain(imp) }
                            )
                        }

                        EvidenceIntegritySection(
                            imp = imp,
                            auditHistory = s.auditHistory,
                            snapshot = s.evidenceSnapshot,
                            onRunAudit = { viewModel.runIntegrityAudit(imp.id, "Improvement") }
                        )

                        if (s.implementationPackage != null) {
                            ImplementationPackageView(s.implementationPackage)
                        }
                    }

                    // APPROVAL CONTEXT
                    if (imp.status == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT || imp.status == IntelligenceLifecycleState.NEEDS_REVIEW) {
                        SectionCard("Why should I approve this?") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("This optimization is supported by ${imp.evidenceCount} production samples with ${imp.confidence.name.lowercase()} confidence. It aims to yield a ${imp.expectedImpact.lowercase()} while maintaining baseline stability.", fontSize = 14.sp, color = AuraOnSurface)
                                OutlinedButton(onClick = { viewModel.askAuraToExplain(imp) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("View Detailed Explanation")
                                }
                            }
                        }

                        SectionCard("What happens if you approve?") {
                            val steps = listOf(
                                "Aura locks the approved Blueprint version.",
                                "A formal implementation package is created.",
                                "Android Studio receives technical instructions.",
                                "The implementation is tracked in a persistent Run.",
                                "Aura automatically verifies the result.",
                                "The improvement enters production monitoring."
                            )
                            steps.forEach { Text("• $it", fontSize = 12.sp, color = AuraOnSurfaceVariant) }
                        }
                    } else if (imp.status != IntelligenceLifecycleState.REJECTED) {
                        val approvalEvent = s.history.find { it.toState == IntelligenceLifecycleState.APPROVED }
                        if (approvalEvent != null) {
                            SectionCard("Why was this approved?") {
                                Text("Approved on ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(approvalEvent.timestamp))} based on ${imp.evidenceCount} production samples.", fontSize = 13.sp, color = AuraOnSurfaceVariant)
                                if (approvalEvent.reason != null) {
                                    Text("Reason: ${approvalEvent.reason}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AuraOnSurface)
                                }
                            }
                        }
                    }

                    // 6. RISK
                    SectionCard("Risk Assessment") {
                        RiskSection(imp.risk)
                    }

                    // 7. VERIFICATION & ROLLBACK
                    SectionCard("Assurance Plan") {
                        Text("Verification", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AuraOnSurfaceVariant)
                        imp.verificationPlan.criteria.forEach { Text("• $it", fontSize = 13.sp, color = AuraOnSurface) }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("Rollback Strategy", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AuraOnSurfaceVariant)
                        Text(imp.rollbackPlan.procedure, fontSize = 13.sp, color = AuraOnSurface)
                    }

                    // 8. DECISION HISTORY
                    DecisionHistorySection(s.history)

                    // 9. TECHNICAL DETAILS (SECONDARY LAYER)
                    TechnicalDetailsExpandable(imp, artifact)

                    Spacer(modifier = Modifier.height(80.dp))
                }

                if (imp.status == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT || imp.status == IntelligenceLifecycleState.NEEDS_REVIEW) {
                    ReviewActions(
                        onApprove = { viewModel.approveImprovement(imp.id) },
                        onReject = { viewModel.rejectImprovement(imp.id, "Rejected by user") },
                        onExplain = { viewModel.askAuraToExplain(imp) }
                    )
                } else if (imp.status == IntelligenceLifecycleState.APPROVED) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { viewModel.planImplementation(imp.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraPurple),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text("Initialize Implementation Plan", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (imp.status == IntelligenceLifecycleState.IMPLEMENTATION_FAILED || imp.status == IntelligenceLifecycleState.VERIFICATION_FAILED) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { viewModel.retryImplementation(imp.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraPurple),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Text("Retry Implementation", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (imp.status == IntelligenceLifecycleState.DEVIATION_DETECTED) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Scope Deviation Detected", color = Color.Red, fontWeight = FontWeight.Bold)
                            Text("The implementation modified files outside the approved blueprint scope. Review the changes before proceeding.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.resolveDeviation(imp.id, true) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
                                ) {
                                    Text("Approve New Scope")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.resolveDeviation(imp.id, false) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reject & Rework")
                                }
                            }
                        }
                    }
                } else if (imp.status == IntelligenceLifecycleState.IMPLEMENTATION_PLANNED) {
                    val latestRun = s.runs.firstOrNull { it.status == IntelligenceActionStatus.PENDING }
                    if (latestRun != null) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = { viewModel.startImplementation(latestRun.id) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AuraPurple),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Text("Begin Technical Implementation", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    if (explanation != null) {
        ExplanationDialog(
            explanation = explanation!!,
            onDismiss = { viewModel.clearExplanation() }
        )
    }
}

@Composable
private fun ImprovementHeader(imp: SuggestedImprovement) {
    Column {
        Text(
            text = "RECOMMENDATION · ${imp.id}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AuraPurple.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )
        Text(
            text = imp.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = AuraOnSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(imp.status.name, color = if (imp.status == IntelligenceLifecycleState.NEEDS_REVIEW) AuraPurple else Color(0xFF81C784))
            PriorityBadge(imp.priority)
        }
    }
}

@Composable
private fun ProposedChangeItem(mod: ProposedModification) {
    Surface(
        color = AuraSurfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${mod.component} · ${mod.parameter}", 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Bold, 
                color = AuraOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CURRENT", style = MaterialTheme.typography.labelSmall, color = AuraOnSurfaceVariant.copy(alpha = 0.6f))
                    Text(mod.currentValue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward, 
                    contentDescription = null, 
                    tint = AuraPurple.copy(alpha = 0.5f), 
                    modifier = Modifier.size(16.dp).padding(horizontal = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("PROPOSED", style = MaterialTheme.typography.labelSmall, color = AuraPurple.copy(alpha = 0.6f))
                    Text(mod.proposedValue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = AuraPurple)
                }
            }
            if (mod.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(mod.reason, style = MaterialTheme.typography.bodySmall, color = AuraOnSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
    }
}

@Composable
private fun RiskSection(risk: String) {
    val color = when (risk.uppercase()) {
        "HIGH", "CRITICAL" -> Color(0xFFE57373)
        "MEDIUM", "MODERATE" -> Color(0xFFFFB74D)
        else -> Color(0xFF81C784)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Text(risk, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
    }
}

@Composable
private fun DecisionHistorySection(history: List<LifecycleEvent>) {
    Column {
        Text("Decision History", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AuraOnSurface)
        Spacer(modifier = Modifier.height(12.dp))
        history.forEach { event ->
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(AuraPurple, RoundedCornerShape(4.dp)))
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(AuraBorder))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(event.toState.name.replace("_", " "), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(Date(event.timestamp)), fontSize = 11.sp, color = AuraOnSurfaceVariant)
                    if (event.reason != null) {
                        Text(event.reason, fontSize = 12.sp, color = AuraOnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewActions(onApprove: () -> Unit, onReject: () -> Unit, onExplain: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onApprove,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AuraPurple),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Approve Optimization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onExplain, 
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
                ) {
                    Text("Ask Aura", color = AuraOnSurfaceVariant)
                }
                OutlinedButton(
                    onClick = onReject, 
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
                ) {
                    Text("Reject", color = Color(0xFFE57373))
                }
            }
        }
    }
}

@Composable
private fun ImplementationReadinessBanner(status: IntelligenceLifecycleState, validation: ScopeValidationResult) {
    if (status != IntelligenceLifecycleState.APPROVED) return

    val (color, text, icon) = when (validation) {
        ScopeValidationResult.Valid -> Triple(Color(0xFF81C784), "Ready for Implementation", Icons.Default.CheckCircle)
        is ScopeValidationResult.Mismatch -> Triple(Color(0xFFE57373), "Implementation Scope Mismatch", Icons.Default.Error)
        is ScopeValidationResult.Incomplete -> Triple(Color(0xFFFFB74D), "Implementation Plan Incomplete", Icons.Default.Info)
    }

    Surface(
        color = color.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                if (validation is ScopeValidationResult.Mismatch) {
                    Text(validation.reason, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f))
                } else if (validation is ScopeValidationResult.Incomplete) {
                    Text("Missing technical steps in implementation manifest.", style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun ImplementationWorkflowSection(
    imp: SuggestedImprovement,
    artifact: com.example.data.blueprint.BlueprintArtifact,
    runs: List<ImplementationRun>,
    results: List<VerificationResult>,
    onStartImplementation: (String) -> Unit,
    onCompleteImplementation: (String, String?, List<String>) -> Unit,
    onStartVerification: (String) -> Unit,
    onRecordVerification: (String, Boolean, Boolean, Boolean, Boolean, Boolean, Map<String, String>, String?) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard("Implementation & Verification") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Implementation Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AuraOnSurfaceVariant)
                    StatusBadge(imp.status.name)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show", color = AuraPurple)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // OBJECTIVE
                Text("Technical Objective".uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = AuraPurple.copy(alpha = 0.5f), letterSpacing = 0.5.sp)
                Text(imp.summary, style = MaterialTheme.typography.bodyLarge, color = AuraOnSurface, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(16.dp))

                // SCOPE
                ImplementationScopeView(artifact)

                Spacer(modifier = Modifier.height(16.dp))

                // ACCEPTANCE CRITERIA
                AcceptanceCriteriaView(artifact, results.firstOrNull())

                Spacer(modifier = Modifier.height(24.dp))

                // RUNS
                ImplementationRunsView(
                    runs = runs,
                    verificationResults = results,
                    artifact = artifact,
                    currentStatus = imp.status,
                    onStartImplementation = onStartImplementation,
                    onCompleteImplementation = onCompleteImplementation,
                    onStartVerification = onStartVerification,
                    onRecordVerification = onRecordVerification
                )
            }
        }
    }
}

@Composable
private fun ImplementationScopeView(artifact: com.example.data.blueprint.BlueprintArtifact) {
    var technicalExpanded by remember { mutableStateOf(false) }

    Column {
        Text("Technical Scope".uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = AuraPurple.copy(alpha = 0.5f), letterSpacing = 0.5.sp)
        
        val manifest = artifact.implementationManifest
        if (manifest != null) {
            val files = manifest.proposedModifications.flatMap { it.filesToModify }.distinct()
            Text("${files.size} code components will be refined.", style = MaterialTheme.typography.bodyMedium, color = AuraOnSurface)
            
            TextButton(
                onClick = { technicalExpanded = !technicalExpanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (technicalExpanded) "Hide Component List" else "View Affected Components", color = AuraPurple, style = MaterialTheme.typography.labelMedium)
            }

            if (technicalExpanded) {
                Surface(
                    color = AuraSurfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScopeItem("Files", files)
                        ScopeItem("Classes", manifest.proposedModifications.map { it.className }.distinct())
                        ScopeItem("Functions", manifest.proposedModifications.map { it.methodOrProperty }.distinct())
                        ScopeItem("Protected", manifest.proposedModifications.flatMap { it.filesNotToModify }.distinct())
                    }
                }
            }
        } else {
            Text("No implementation manifest available.", fontSize = 14.sp, color = AuraOnSurfaceVariant)
        }
    }
}

@Composable
private fun ScopeItem(label: String, items: List<String>) {
    if (items.isEmpty()) return
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AuraOnSurfaceVariant)
        items.forEach { Text("• $it", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AuraOnSurface) }
    }
}

@Composable
private fun AcceptanceCriteriaView(artifact: com.example.data.blueprint.BlueprintArtifact, lastResult: VerificationResult?) {
    Column {
        Text("Validation Criteria".uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = AuraPurple.copy(alpha = 0.5f), letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        artifact.strategyBlueprint.targetState.successCriteria.forEach { criterion ->
            val status = lastResult?.acceptanceCriteriaResults?.get(criterion) ?: "Pending"
            CriterionItem(criterion, status)
        }
    }
}

@Composable
private fun CriterionItem(text: String, status: String) {
    val color = when (status) {
        "Passed" -> Color(0xFF81C784)
        "Failed" -> Color(0xFFE57373)
        "Not Applicable" -> AuraOnSurfaceVariant
        else -> Color(0xFFFFB74D) // Pending
    }
    
    Row(
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, color = AuraOnSurface, modifier = Modifier.weight(1f))
        Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ImplementationRunsView(
    runs: List<ImplementationRun>,
    verificationResults: List<VerificationResult>,
    artifact: com.example.data.blueprint.BlueprintArtifact,
    currentStatus: IntelligenceLifecycleState,
    onStartImplementation: (String) -> Unit,
    onCompleteImplementation: (String, String?, List<String>) -> Unit,
    onStartVerification: (String) -> Unit,
    onRecordVerification: (String, Boolean, Boolean, Boolean, Boolean, Boolean, Map<String, String>, String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Activity Sessions".uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = AuraPurple.copy(alpha = 0.5f), letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.weight(1f))
            if (currentStatus == IntelligenceLifecycleState.IMPLEMENTATION_PLANNED) {
                val latestRun = runs.firstOrNull { it.status == IntelligenceActionStatus.PENDING }
                if (latestRun != null) {
                    Button(
                        onClick = { onStartImplementation(latestRun.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = AuraPurple),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Start Execution", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (runs.isEmpty()) {
            Text("No implementation runs recorded.", fontSize = 13.sp, color = AuraOnSurfaceVariant)
        } else {
            runs.forEach { run ->
                RunCard(
                    run = run,
                    artifact = artifact,
                    verificationResults = verificationResults.filter { it.runId == run.id },
                    currentStatus = currentStatus,
                    onComplete = { notes, files -> onCompleteImplementation(run.id, notes, files) },
                    onStartVerify = { onStartVerification(run.id) },
                    onRecordVerify = { b, t, r, d, s, ac, td -> onRecordVerification(run.id, b, t, r, d, s, ac, td) }
                )
            }
        }
    }
}

@Composable
private fun RunCard(
    run: ImplementationRun,
    artifact: com.example.data.blueprint.BlueprintArtifact,
    verificationResults: List<VerificationResult>,
    currentStatus: IntelligenceLifecycleState,
    onComplete: (String?, List<String>) -> Unit,
    onStartVerify: () -> Unit,
    onRecordVerify: (Boolean, Boolean, Boolean, Boolean, Boolean, Map<String, String>, String?) -> Unit
) {
    Surface(
        color = AuraSurfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(run.id, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AuraPurple)
                Spacer(modifier = Modifier.weight(1f))
                StatusBadgeAction(run.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Started: ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(run.startTime))}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
            if (run.endTime != null) {
                Text("Completed: ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(run.endTime))}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
            }

            if (run.status == IntelligenceActionStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onComplete("Implementation finished by user", emptyList()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
                ) {
                    Text("Mark Implementation Complete")
                }
            } else if (run.status == IntelligenceActionStatus.COMPLETED && currentStatus == IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStartVerify,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
                ) {
                    Text("Start Verification")
                }
            } else if (currentStatus == IntelligenceLifecycleState.VERIFICATION_IN_PROGRESS && run.status == IntelligenceActionStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { 
                        // Simulate verification for prototype
                        val results = artifact.strategyBlueprint.targetState.successCriteria.associateWith { "Passed" }
                        onRecordVerify(true, true, true, true, true, results, "Build successful, all tests passed.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784))
                ) {
                    Text("Run Automated Verification")
                }
            }

            if (verificationResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Technical Verification Logs".uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = AuraPurple.copy(alpha = 0.4f), letterSpacing = 0.5.sp)
                verificationResults.forEach { result ->
                    VerificationResultItem(result)
                }
            }
        }
    }
}

@Composable
private fun VerificationResultItem(result: VerificationResult) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (result.overallPassed) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (result.overallPassed) Color(0xFF81C784) else Color(0xFFE57373),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (result.overallPassed) "Passed" else "Failed", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide Details" else "View Details", fontSize = 11.sp, color = AuraPurple)
            }
        }
        
        if (expanded) {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                VerificationRow("Build", result.buildPassed)
                VerificationRow("Tests", result.testsPassed)
                VerificationRow("Regression", result.regressionPassed)
                VerificationRow("DB Integrity", result.dbIntegrityPassed)
                VerificationRow("Scope Compliance", result.scopeCompliant)
                
                if (result.technicalDetails != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Technical Diagnostics", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AuraOnSurfaceVariant)
                    Text(result.technicalDetails, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AuraOnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VerificationRow(label: String, passed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = AuraOnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(if (passed) "Passed" else "Failed", fontSize = 11.sp, color = if (passed) Color(0xFF81C784) else Color(0xFFE57373))
    }
}

@Composable
private fun StatusBadgeAction(status: IntelligenceActionStatus) {
    val color = when (status) {
        IntelligenceActionStatus.COMPLETED -> Color(0xFF81C784)
        IntelligenceActionStatus.FAILED -> Color(0xFFE57373)
        IntelligenceActionStatus.IN_PROGRESS -> AuraPurple
        else -> AuraOnSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun MonitoringValidationSection(
    imp: SuggestedImprovement,
    sessions: List<MonitoringSession>,
    validationResults: List<ValidationResult>,
    onUpdateMonitoring: (String, Int, Double) -> Unit
) {
    if (sessions.isEmpty() && validationResults.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard("Monitoring & Validation") {
            if (imp.status == IntelligenceLifecycleState.MONITORING) {
                val activeSession = sessions.firstOrNull { it.status == MonitoringStatus.ACTIVE }
                if (activeSession != null) {
                    ActiveMonitoringView(activeSession, onUpdateMonitoring)
                }
            }

            validationResults.forEach { result ->
                ValidationResultCard(result)
            }
        }
    }
}

@Composable
private fun ActiveMonitoringView(session: MonitoringSession, onUpdate: (String, Int, Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MONITORING IN PROGRESS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AuraPurple)
            Spacer(modifier = Modifier.weight(1f))
            CircularProgressIndicator(
                progress = { session.confidence.toFloat() },
                modifier = Modifier.size(16.dp),
                color = AuraPurple,
                strokeWidth = 2.dp,
            )
        }

        val targetMetric = session.baselineMetrics.keys.firstOrNull() ?: "Metric"
        val baseline = session.baselineMetrics[targetMetric] ?: 0.0
        val current = session.currentMetrics[targetMetric] ?: 0.0
        val change = current - baseline

        MonitoringMetricRow("Baseline", baseline)
        MonitoringMetricRow("Current Result", current)
        MonitoringMetricRow("Change", change, isDelta = true)

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { (session.currentSampleCount.toFloat() / session.requiredSampleCount.toFloat()).coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = AuraPurple,
            trackColor = AuraBorder.copy(alpha = 0.3f)
        )
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("${session.currentSampleCount} samples", fontSize = 11.sp, color = AuraOnSurfaceVariant)
            Text("Target: ${session.requiredSampleCount}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
        }

        if (session.regressionDetected) {
            Surface(
                color = Color(0xFFE57373).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE57373))
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Potential Regression Detected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                }
            }
        }

        Button(
            onClick = { 
                // Simulate receiving more production evidence for the prototype
                onUpdate(session.id, 50, current + (Math.random() * 2.0 - 0.5))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
        ) {
            Text("Simulate Production Evidence")
        }
    }
}

@Composable
private fun MonitoringMetricRow(label: String, value: Double, isDelta: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = AuraOnSurfaceVariant, modifier = Modifier.weight(1f))
        val color = if (isDelta) {
            if (value >= 0) Color(0xFF81C784) else Color(0xFFE57373)
        } else AuraOnSurface
        
        val prefix = if (isDelta && value > 0) "+" else ""
        Text("$prefix${"%.2f".format(value)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ValidationResultCard(result: ValidationResult) {
    val (color, title, icon) = when (result.outcome) {
        IntelligenceLifecycleState.VALIDATED -> Triple(Color(0xFF81C784), "Improvement Validated", Icons.Default.CheckCircle)
        IntelligenceLifecycleState.REGRESSION_DETECTED -> Triple(Color(0xFFE57373), "Regression Detected", Icons.Default.Error)
        else -> Triple(Color(0xFFFFB74D), "Validation Inconclusive", Icons.Default.Info)
    }

    Surface(
        color = color.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, color = color)
                Spacer(modifier = Modifier.weight(1f))
                Text(SimpleDateFormat("MMM d", Locale.US).format(Date(result.timestamp)), fontSize = 11.sp, color = AuraOnSurfaceVariant)
            }

            Text(result.evidenceSummary, fontSize = 13.sp, color = AuraOnSurface)

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.1f)))

            Row(modifier = Modifier.fillMaxWidth()) {
                ValidationMetric("Baseline", result.baselineValue, Modifier.weight(1f))
                ValidationMetric("Final", result.finalValue, Modifier.weight(1f))
                ValidationMetric("Change", result.change, Modifier.weight(1f), isDelta = true)
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Samples: ${result.sampleCount}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
                Text("Confidence: ${"%.0f%%".format(result.confidence * 100)}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ValidationMetric(label: String, value: Double, modifier: Modifier, isDelta: Boolean = false) {
    Column(modifier = modifier) {
        Text(label, fontSize = 10.sp, color = AuraOnSurfaceVariant)
        val color = if (isDelta) {
            if (value >= 0) Color(0xFF81C784) else Color(0xFFE57373)
        } else AuraOnSurface
        val prefix = if (isDelta && value > 0) "+" else ""
        Text("$prefix${"%.2f".format(value)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun RegressionResponseSection(
    imp: SuggestedImprovement,
    alerts: List<RegressionAlert>,
    rollbackRuns: List<RollbackRun>,
    onInvestigate: (String) -> Unit,
    onMarkFalsePositive: (String, String) -> Unit,
    onApproveRollback: (String) -> Unit,
    onExecuteRollback: (String) -> Unit,
    onCreateCorrective: (String) -> Unit,
    onWhyClick: () -> Unit
) {
    if (alerts.isEmpty() && rollbackRuns.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard("Regression Response & Recovery") {
            alerts.forEach { alert ->
                RegressionAlertCard(
                    alert = alert,
                    currentStatus = imp.status,
                    onInvestigate = { onInvestigate(alert.id) },
                    onMarkFalsePositive = { onMarkFalsePositive(alert.id, "False positive confirmed by user.") },
                    onApproveRollback = { onApproveRollback(alert.id) },
                    onCreateCorrective = { onCreateCorrective(alert.id) },
                    onWhyClick = onWhyClick
                )
            }

            rollbackRuns.forEach { run ->
                RollbackRunCard(run, onExecuteRollback)
            }
        }
    }
}

@Composable
private fun RegressionAlertCard(
    alert: RegressionAlert,
    currentStatus: IntelligenceLifecycleState,
    onInvestigate: () -> Unit,
    onMarkFalsePositive: () -> Unit,
    onApproveRollback: () -> Unit,
    onCreateCorrective: () -> Unit,
    onWhyClick: () -> Unit
) {
    Surface(
        color = Color(0xFFE57373).copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.3f)),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFE57373))
                Spacer(modifier = Modifier.width(8.dp))
                Text("REGRESSION DETECTED", fontWeight = FontWeight.Bold, color = Color(0xFFE57373), fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                SeverityBadge(alert.severity)
            }

            Text(
                "Metric ${alert.affectedMetric} dropped from ${"%.2f".format(alert.baselineValue)} to ${"%.2f".format(alert.currentResult)} (${"%.2f".format(alert.change)})",
                fontSize = 13.sp,
                color = AuraOnSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aura Recommendation: ${alert.recommendation}", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = AuraOnSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onWhyClick) {
                    Text("Why?", fontSize = 11.sp, color = AuraPurple)
                }
            }

            if (alert.status == RegressionAlertStatus.ACTIVE) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onInvestigate, modifier = Modifier.weight(1f)) {
                        Text("Investigate", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onApproveRollback,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373))
                    ) {
                        Text("Rollback", fontSize = 12.sp)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCreateCorrective, modifier = Modifier.weight(1f)) {
                        Text("Corrective Action", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onMarkFalsePositive, modifier = Modifier.weight(1f)) {
                        Text("False Positive", fontSize = 11.sp)
                    }
                }
            } else {
                Text("Status: ${alert.status.name}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AuraPurple)
            }
        }
    }
}

@Composable
private fun RollbackRunCard(run: RollbackRun, onExecute: (String) -> Unit) {
    Surface(
        color = AuraSurfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder.copy(alpha = 0.5f)),
        modifier = Modifier.padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ROLLBACK RUN", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AuraPurple)
                Spacer(modifier = Modifier.weight(1f))
                StatusBadgeAction(run.status)
            }
            
            Text("Reversing implementation ${run.originalRunId}", fontSize = 11.sp, color = AuraOnSurfaceVariant)

            if (run.status == IntelligenceActionStatus.PENDING) {
                Button(
                    onClick = { onExecute(run.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
                ) {
                    Text("Execute Rollback")
                }
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: RegressionSeverity) {
    val color = when (severity) {
        RegressionSeverity.CRITICAL -> Color(0xFFD32F2F)
        RegressionSeverity.HIGH -> Color(0xFFE64A19)
        RegressionSeverity.MEDIUM -> Color(0xFFFBC02D)
        else -> Color(0xFF7B1FA2)
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = severity.name,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun EvidenceIntegritySection(
    imp: SuggestedImprovement,
    auditHistory: List<IntegrityAuditResult>,
    snapshot: EvidenceSnapshot?,
    onRunAudit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val lastAudit = auditHistory.firstOrNull()

    SectionCard("Evidence & Integrity") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IntegrityStatusBadge(lastAudit?.status ?: IntegrityStatus.PASS)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onRunAudit) {
                    Text("Run Audit", color = AuraPurple, fontSize = 12.sp)
                }
            }

            if (lastAudit != null) {
                Text("Last Audit: ${SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(lastAudit.timestamp))}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
                if (lastAudit.status != IntegrityStatus.PASS) {
                    lastAudit.issues.forEach { issue ->
                        Text("• ${issue.message}", color = if (issue.severity == IntegritySeverity.CRITICAL) Color(0xFFE57373) else AuraOnSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (expanded) "Hide Lineage" else "View Evidence Lineage", color = AuraPurple, fontSize = 13.sp)
            }

            if (expanded) {
                LineageView(imp, snapshot)
            }
        }
    }
}

@Composable
private fun IntegrityStatusBadge(status: IntegrityStatus) {
    val (color, text) = when (status) {
        IntegrityStatus.PASS -> Color(0xFF81C784) to "Integrity Verified"
        IntegrityStatus.WARNING -> Color(0xFFFFB74D) to "Integrity Warning"
        IntegrityStatus.FAIL -> Color(0xFFE57373) to "Integrity Failure"
        IntegrityStatus.REVIEW_REQUIRED -> AuraPurple to "Review Required"
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun LineageView(imp: SuggestedImprovement, snapshot: EvidenceSnapshot?) {
    Surface(
        color = AuraSurfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TraceStep("Source", imp.source)
            TraceStep("Finding", imp.findingId)
            TraceStep("Proposal", "v${imp.version} (${SimpleDateFormat("MMM d", Locale.US).format(Date(imp.createdAt))})")
            if (snapshot != null) {
                TraceStep("Approval Snapshot", "Captured ${SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(snapshot.timestamp))}")
                Text("Snapshot Baseline: ${snapshot.report.baselineScore}", fontSize = 10.sp, color = AuraOnSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun TraceStep(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(AuraPurple, RoundedCornerShape(3.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Text("$label: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AuraOnSurface)
    }
}

@Composable
private fun ImplementationPackageView(pkg: ImplementationPackage) {
    var technicalExpanded by remember { mutableStateOf(false) }

    SectionCard("Implementation Contract") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("TECHNICAL OBJECTIVE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AuraOnSurfaceVariant)
            Text(pkg.technicalObjective, fontSize = 14.sp)

            Text("APPROVED SCOPE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AuraOnSurfaceVariant)
            pkg.approvedScope.forEach { Text("• $it", fontSize = 13.sp) }

            Text("OUT OF SCOPE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE57373))
            pkg.outOfScope.forEach { Text("• $it", fontSize = 12.sp, color = AuraOnSurfaceVariant) }

            TextButton(onClick = { technicalExpanded = !technicalExpanded }, contentPadding = PaddingValues(0.dp)) {
                Text(if (technicalExpanded) "Hide Technical Details" else "View Technical Details", color = AuraPurple)
            }

            if (technicalExpanded) {
                Surface(
                    color = AuraSurfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Files: ${pkg.filesAffected.joinToString(", ")}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("Classes: ${pkg.classesAffected.joinToString(", ")}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        if (pkg.dataModelChanges != null) {
                            Text("Data Model: ${pkg.dataModelChanges}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                
                if (pkg.androidStudioPrompt != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ANDROID STUDIO PROMPT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AuraOnSurfaceVariant)
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = pkg.androidStudioPrompt,
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFF81C784),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalDetailsExpandable(imp: SuggestedImprovement, artifact: com.example.data.blueprint.BlueprintArtifact?) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(if (expanded) "Hide Internal Context" else "View Technical Details", color = AuraOnSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
        }

        if (expanded) {
            Surface(
                color = Color(0xFFF9FAFB),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TechnicalIdRow("Improvement ID", imp.id)
                    TechnicalIdRow("Finding ID", imp.findingId)
                    TechnicalIdRow("Blueprint ID", artifact?.blueprintId ?: "N/A")
                    TechnicalIdRow("Blueprint Version", artifact?.blueprintVersion ?: "N/A")
                    
                    if (artifact != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("TECHNICAL PAYLOAD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = AuraOnSurfaceVariant.copy(alpha = 0.6f))
                        Surface(
                            color = Color(0xFFF3F4F6),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Technical Blueprint Artifact v${artifact.blueprintVersion} is locked. All sections (1-15) are available via Intelligence Export.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = AuraOnSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalIdRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = AuraOnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = AuraPurple.copy(alpha = 0.8f))
    }
}

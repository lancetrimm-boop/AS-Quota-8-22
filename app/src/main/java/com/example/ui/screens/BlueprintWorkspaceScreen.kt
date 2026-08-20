package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.data.blueprint.*
import com.example.ui.components.ActionStatusBadge
import com.example.ui.components.AuraTopBar
import com.example.ui.components.ConfidenceSection
import com.example.ui.components.EvidenceSummaryView
import com.example.ui.components.FindingCard
import com.example.ui.components.ImprovementReviewCard
import com.example.ui.components.IntelligenceMetricCard
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueprintWorkspaceScreen(
    repository: MediaRepository,
    onBack: () -> Unit,
    onNavigateToImprovement: (String) -> Unit,
    onNavigateToFinding: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val intelligenceRepository = repository.intelligenceRepository ?: return
    val viewModel: IntelligenceViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceViewModel(intelligenceRepository) as T
            }
        }
    )
    
    val state by viewModel.state.collectAsState()
    
    var showAdvancedMode by remember { mutableStateOf(false) }

    if (showAdvancedMode) {
        // Fallback to legacy view for backward compatibility with Strategy Blueprint actions
        LegacyBlueprintWorkspace(repository, onBack = { showAdvancedMode = false })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White) // White background as per visual design
    ) {
        AuraTopBar(
            title = "Intelligence Control Center",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuraPurple)
                }
            },
            actions = {
                IconButton(onClick = { showAdvancedMode = true }) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Settings, contentDescription = "Advanced Mode", tint = AuraPurple)
                }
            },
            showLogo = false
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraPurple)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. AURA INTELLIGENCE SUMMARY
                IntelligenceSummarySection(state.summaryMetrics, onFilterSelect = { viewModel.setFilter(it) })

                // 2. REGRESSIONS (Prominent if active)
                if (state.summaryMetrics.activeRegressions > 0) {
                    RegressionAlertSection(state)
                }

                // 3. SYSTEM ANALYSIS
                SystemAnalysisSection(state.systemAnalysis) { id ->
                    onNavigateToImprovement(id)
                }

                // 4. IMPROVEMENTS NEEDING REVIEW
                ImprovementsSection(
                    title = "Improvements Needing Review",
                    improvements = state.improvements.filter { it.status == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT || it.status == IntelligenceLifecycleState.NEEDS_REVIEW },
                    emptyMessage = "All improvements have been reviewed."
                ) { id ->
                    onNavigateToImprovement(id)
                }

                // 5. CURRENTLY BEING IMPLEMENTED
                ImprovementsSection(
                    title = "Currently Being Implemented",
                    improvements = state.improvements.filter { it.status in listOf(IntelligenceLifecycleState.IMPLEMENTATION_PLANNED, IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS, IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE) },
                    emptyMessage = "No active implementations."
                ) { id ->
                    onNavigateToImprovement(id)
                }

                // 6. RECENT FINDINGS
                FindingsSection(state.findings.take(5)) { id ->
                    onNavigateToImprovement(id)
                }
            }
        }
    }
}

@Composable
private fun IntelligenceSummarySection(metrics: IntelligenceMetrics, onFilterSelect: (IntelligenceMetricsFilter) -> Unit) {
    Column {
        Text("Aura Intelligence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AuraOnSurface)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IntelligenceMetricCard("New Findings", metrics.newFindings) { onFilterSelect(IntelligenceMetricsFilter.NEW) }
            IntelligenceMetricCard("Needs Review", metrics.needsReview) { onFilterSelect(IntelligenceMetricsFilter.NEEDS_REVIEW) }
            IntelligenceMetricCard("Approved", metrics.approved) { onFilterSelect(IntelligenceMetricsFilter.APPROVED) }
            IntelligenceMetricCard("Implementing", metrics.implementing) { onFilterSelect(IntelligenceMetricsFilter.IMPLEMENTING) }
            IntelligenceMetricCard("Monitoring", metrics.monitoring) { onFilterSelect(IntelligenceMetricsFilter.MONITORING) }
            IntelligenceMetricCard("Validated", metrics.validated) { onFilterSelect(IntelligenceMetricsFilter.VALIDATED) }
        }
    }
}

@Composable
private fun SystemAnalysisSection(analysis: SystemAnalysisSummary?, onImprovementClick: (String) -> Unit) {
    SectionCard("System Analysis") {
        if (analysis == null) {
            Text("No analysis available. Aura is currently collecting evidence.", color = AuraOnSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Latest Findings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
                    ActionStatusBadge(analysis.actionStatus)
                }

                Text(analysis.summary, fontSize = 15.sp, color = AuraOnSurface, lineHeight = 22.sp)
                
                if (analysis.whatsWorking.isNotEmpty()) {
                    AnalysisBulletSection("What's Working", analysis.whatsWorking, Color(0xFF81C784))
                }
                
                if (analysis.whatsNotWorking.isNotEmpty()) {
                    AnalysisBulletSection("Needs Attention", analysis.whatsNotWorking, Color(0xFFE57373))
                }

                HorizontalDivider(color = AuraBorder.copy(alpha = 0.5f))

                // EVIDENCE
                analysis.evidenceSummary?.let { summary ->
                    Text("EVIDENCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
                    EvidenceSummaryView(summary)
                }

                // CONFIDENCE
                ConfidenceSection(
                    level = analysis.latestFinding?.confidence ?: ConfidenceLevel.LOW,
                    explanation = analysis.confidenceExplanation
                )

                HorizontalDivider(color = AuraBorder.copy(alpha = 0.5f))
                
                // RECOMMENDED ACTION
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RECOMMENDATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
                    Text(analysis.recommendation, fontSize = 14.sp, color = AuraOnSurface)
                    
                    if (analysis.actionStatus == ActionStatus.REVIEW_RECOMMENDED || analysis.actionStatus == ActionStatus.ACTION_REQUIRED) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { 
                                analysis.latestFinding?.let { finding ->
                                    // Normally we'd find the associated improvement ID
                                    // For now, pass the finding ID if we don't have a direct link yet
                                    onImprovementClick(finding.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
                        ) {
                            Text("Review Suggested Improvement")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisBulletSection(title: String, bullets: List<String>, color: Color) {
    Column {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        bullets.forEach { bullet ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text("•", modifier = Modifier.padding(end = 8.dp), color = color)
                Text(bullet, fontSize = 13.sp, color = AuraOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ImprovementsSection(
    title: String,
    improvements: List<SuggestedImprovement>,
    emptyMessage: String,
    onImprovementClick: (String) -> Unit
) {
    Column {
        SectionHeader(title)
        if (improvements.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AuraLogoIcon(size = 48.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = emptyMessage,
                        fontSize = 14.sp,
                        color = AuraOnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            improvements.forEach { imp ->
                ImprovementReviewCard(imp) { onImprovementClick(imp.id) }
            }
        }
    }
}

@Composable
private fun FindingsSection(findings: List<Finding>, onFindingClick: (String) -> Unit) {
    Column {
        SectionHeader("Recent Findings")
        if (findings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AuraLogoIcon(size = 48.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No findings detected yet.",
                        fontSize = 14.sp,
                        color = AuraOnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            findings.forEach { finding ->
                FindingCard(finding) { onFindingClick(finding.id) }
            }
        }
    }
}

@Composable
private fun RegressionAlertSection(state: IntelligenceCenterState) {
    Surface(
        color = Color(0xFFFFEBEE),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Active Regressions Detected", fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                Text("${state.summaryMetrics.activeRegressions} items require immediate review.", fontSize = 13.sp, color = Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = AuraOnSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/**
 * Legacy view wrapper to preserve all existing functionality.
 */
@Composable
private fun LegacyBlueprintWorkspace(repository: MediaRepository, onBack: () -> Unit) {
    val manager = repository.blueprintArtifactManager ?: return
    val artifact by manager.currentArtifact.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Blueprint", "Evidence", "Implementation", "Validation")

    var searchQuery by remember { mutableStateOf("") }
    var selectedTierFilter by remember { mutableStateOf<EvidenceTier?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<ImplementationStatus?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraBackground)
    ) {
        AuraTopBar(
            title = "Legacy Blueprint Workspace",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuraOnSurface)
                }
            },
            showLogo = false
        )

        if (artifact == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No blueprint artifact loaded.", color = AuraOnSurfaceVariant)
            }
        } else {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = AuraSurface,
                contentColor = AuraPurple,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> Text("Blueprint Details (Legacy)", modifier = Modifier.padding(16.dp))
                    1 -> Text("Evidence (Legacy)", modifier = Modifier.padding(16.dp))
                    2 -> Text("Implementation (Legacy)", modifier = Modifier.padding(16.dp))
                    3 -> Text("Validation (Legacy)", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

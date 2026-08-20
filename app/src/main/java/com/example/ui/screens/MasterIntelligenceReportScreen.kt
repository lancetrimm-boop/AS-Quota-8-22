package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.data.*
import com.example.ui.components.*
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSlate
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.AuraSurface
import com.example.ui.theme.AuraSurfaceVariant
import com.example.ui.theme.DiscoveryViolet
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterIntelligenceReportScreen(
    repository: IntelligenceRepository,
    onNavigateToWorkspace: () -> Unit,
    onNavigateToImprovement: (String) -> Unit,
    onNavigateToFinding: (String) -> Unit
) {
    val viewModel: IntelligenceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceViewModel(repository) as T
            }
        }
    )

    val state by viewModel.state.collectAsState()
    val report = state.masterReport
    val savedReports by repository.getAllSavedReports().collectAsState(initial = emptyList())
    
    var showHistoricalList by remember { mutableStateOf(false) }
    var viewingSnapshot by remember { mutableStateOf<MasterIntelligenceReport?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val activeReport = viewingSnapshot ?: report

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraCrispWhite)
    ) {
        AuraTopBar(
            title = if (activeReport?.isSnapshot == true) "Snapshot Analysis" else "System Analysis",
            showLogo = true,
            actions = {
                if (activeReport != null) {
                    IconButton(onClick = { viewModel.exportMasterReport(context, activeReport) }) {
                        Icon(Icons.Default.IosShare, contentDescription = "Export Report", tint = DiscoveryViolet)
                    }
                    IconButton(onClick = { viewModel.saveReportSnapshot(activeReport) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Snapshot", tint = DiscoveryViolet)
                    }
                }
                IconButton(onClick = { showHistoricalList = true }) {
                    Icon(Icons.Default.History, contentDescription = "Historical Reports", tint = DiscoveryViolet)
                }
            }
        )

        if (viewingSnapshot != null) {
            Surface(
                color = DiscoveryViolet.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = DiscoveryViolet, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Viewing Historical Snapshot (${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(viewingSnapshot!!.timestamp))})", style = MaterialTheme.typography.labelMedium, color = DiscoveryViolet)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewingSnapshot = null }) {
                        Text("Exit Snapshot", color = DiscoveryViolet, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (state.isLoading || activeReport == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiscoveryViolet)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // REPORT STATUS
                ReportStatusHeader(activeReport)

                // 1. EXECUTIVE SUMMARY
                ExecutiveSummarySection(activeReport.executiveSummary, onNavigateToWorkspace)

                // 2. SINCE YOUR LAST REVIEW
                ReviewUpdateSection(activeReport.sinceLastReview, onNavigateToImprovement, onNavigateToFinding)

                // 3. SYSTEM ANALYSIS
                SystemAnalysisEmbed(activeReport.systemAnalysis)

                // 4-9. DOMAIN INTELLIGENCE
                DomainIntelligenceSection("Product Intelligence", activeReport.productIntelligence)
                DomainIntelligenceSection("Engagement", activeReport.engagement)
                PersonalizationSummary(activeReport.personalization)
                if (activeReport.discovery != null) {
                    DomainIntelligenceSection("Discovery", activeReport.discovery)
                }
                DomainIntelligenceSection("Retention", activeReport.retention)
                DomainIntelligenceSection("Monetization", activeReport.monetization)
                TechnicalHealthSummary(activeReport.technicalHealth)

                // 10. SUGGESTED IMPROVEMENTS
                PipelineSummarySection(activeReport.improvementPipeline, onNavigateToImprovement)

                // 11. IMPLEMENTATION STATUS
                ImplementationStatusSection(activeReport.implementationOverview, onNavigateToImprovement)

                // 12. RISKS AND REGRESSIONS
                RisksAndRegressionsSection(activeReport.risksAndRegressions, onNavigateToImprovement)

                // 13. RECENTLY VALIDATED
                RecentlyValidatedSection(activeReport.recentlyValidated, onNavigateToImprovement)

                // 15. OPEN QUESTIONS
                if (activeReport.openQuestions.isNotEmpty()) {
                    SectionCard("Open Intelligence Questions") {
                        activeReport.openQuestions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }

                // 16. AREAS TO WATCH
                if (activeReport.recommendedAreasToWatch.isNotEmpty()) {
                    SectionCard("Recommended Areas to Watch") {
                        activeReport.recommendedAreasToWatch.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showHistoricalList) {
        ModalBottomSheet(onDismissRequest = { showHistoricalList = false }, containerColor = AuraCrispWhite) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Saved Reports & Snapshots", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuraMidnight)
                Spacer(modifier = Modifier.height(16.dp))
                if (savedReports.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AuraLogoIcon(size = 48.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No saved snapshots available.",
                                color = AuraMutedSlate
                            )
                        }
                    }
                } else {
                    savedReports.forEach { saved ->
                        Surface(
                            onClick = { 
                                viewingSnapshot = saved
                                showHistoricalList = false 
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(Date(saved.timestamp)), fontWeight = FontWeight.Bold, color = AuraMidnight)
                                    Text("Reporting Period: ${saved.reportingPeriodDays} days", style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuraSubtleBorder)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun ReportStatusHeader(report: MasterIntelligenceReport) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "MASTER SYSTEM ANALYSIS", 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Bold, 
                color = AuraMutedSlate,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "Intelligence Briefing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = AuraMidnight
            )
            Text(
                text = "Analysis current as of ${SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(report.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = AuraMutedSlate
            )
        }
        Surface(
            color = DiscoveryViolet.copy(alpha = 0.08f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DiscoveryViolet.copy(alpha = 0.15f))
        ) {
            Text(
                text = "v2.0 · PRODUCTION",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DiscoveryViolet
            )
        }
    }
}

@Composable
private fun ExecutiveSummarySection(summary: ExecutiveSummary, onNavigateToWorkspace: () -> Unit) {
    SectionCard("Executive Summary") {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusMiniCard(
                    label = "System Health", 
                    value = summary.systemHealth, 
                    color = if (summary.systemHealth == "HEALTHY") Color(0xFF81C784) else Color(0xFFE57373),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = summary.plainEnglishSummary,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium,
                color = AuraOnSurface
            )

            SummaryMetricsGrid(summary.metrics)
        }
    }
}

@Composable
private fun StatusMiniCard(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.04f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = AuraOnSurfaceVariant, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun SummaryMetricsGrid(metrics: IntelligenceMetrics) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricMiniCard("New", metrics.newFindings.toString(), Modifier.weight(1f))
        MetricMiniCard("Review", metrics.needsReview.toString(), Modifier.weight(1f))
        MetricMiniCard("Active", metrics.implementing.toString(), Modifier.weight(1f))
        MetricMiniCard("Regressions", metrics.activeRegressions.toString(), Modifier.weight(1f), isAlert = metrics.activeRegressions > 0)
    }
}

@Composable
private fun MetricMiniCard(label: String, value: String, modifier: Modifier, isAlert: Boolean = false) {
    Surface(
        color = if (isAlert) Color(0xFFFFDAD6) else AuraSubtleSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isAlert) Color(0xFFBA1A1A) else AuraSubtleBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isAlert) Color(0xFFBA1A1A) else DiscoveryViolet)
            Text(label, fontSize = 10.sp, color = AuraMutedSlate)
        }
    }
}

@Composable
private fun ReviewUpdateSection(
    update: ReviewUpdate,
    onNavigateToImprovement: (String) -> Unit,
    onNavigateToFinding: (String) -> Unit
) {
    SectionCard("Since Your Last Review") {
        if (update.items.isEmpty()) {
            Text("No significant new activity since your last review.", fontSize = 14.sp, color = AuraOnSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                update.items.take(3).forEach { item ->
                    IntelligenceChangeRow(item, onClick = {
                        if (item.type == "Finding") onNavigateToFinding(item.targetId)
                        else onNavigateToImprovement(item.targetId)
                    })
                }
                if (update.items.size > 3) {
                    TextButton(onClick = { /* Show all */ }) {
                        Text("View all ${update.items.size} updates", color = AuraPurple)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntelligenceChangeRow(item: IntelligenceChange, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Box(modifier = Modifier.size(8.dp).background(DiscoveryViolet, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AuraMidnight)
                Text("${item.type} · ${SimpleDateFormat("MMM d", Locale.US).format(Date(item.timestamp))}", fontSize = 11.sp, color = AuraMutedSlate)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuraSubtleBorder, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SystemAnalysisEmbed(analysis: SystemAnalysisSummary) {
    SectionCard("Primary Learning") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(analysis.summary, style = MaterialTheme.typography.bodyLarge, color = AuraMidnight, fontWeight = FontWeight.Medium, lineHeight = 24.sp)
            
            if (analysis.whatsWorking.isNotEmpty()) {
                Column {
                    Text("WORKING WELL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AuraSuccess, letterSpacing = 0.5.sp)
                    analysis.whatsWorking.take(2).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = AuraSlate) }
                }
            }

            if (analysis.whatsNotWorking.isNotEmpty()) {
                Column {
                    Text("NEEDS ATTENTION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFE57373), letterSpacing = 0.5.sp)
                    analysis.whatsNotWorking.take(2).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = AuraSlate) }
                }
            }
        }
    }
}

@Composable
private fun DomainIntelligenceSection(title: String, domain: DomainIntelligence) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(title) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(domain.status, color = if (domain.status == "HEALTHY") AuraSuccess else AuraPurple)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = AuraOnSurfaceVariant)
                }
            }
            if (expanded) {
                if (domain.whatsWorking.isNotEmpty()) {
                    Text("HIGHLIGHTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AuraMutedSlate, letterSpacing = 0.5.sp)
                    domain.whatsWorking.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = AuraMidnight) }
                }
                if (domain.recommendedActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("RECOMMENDATIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = DiscoveryViolet, letterSpacing = 0.5.sp)
                    domain.recommendedActions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = DiscoveryViolet) }
                }
            }
        }
    }
}

@Composable
private fun PersonalizationSummary(p: PersonalizationIntelligence) {
    SectionCard("Personalization Engine") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(p.performanceSummary, style = MaterialTheme.typography.bodyLarge, color = AuraMidnight, fontWeight = FontWeight.Medium)
            
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricColumn("BASELINE", "%.1f".format(p.baselineScore), Modifier.weight(1f))
                MetricColumn("CURRENT", "%.1f".format(p.currentScore), Modifier.weight(1f))
                MetricColumn("SAMPLES", p.sampleCount.toString(), Modifier.weight(1f))
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = DiscoveryViolet.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confidence: ${"%.0f%%".format(p.confidence * 100)}", style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate)
                Spacer(modifier = Modifier.weight(1f))
                StatusBadge(p.regressionStatus, color = if (p.regressionStatus == "STABLE") AuraSuccess else Color(0xFFE57373))
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant, letterSpacing = 0.5.sp)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = AuraPurple)
    }
}

@Composable
private fun TechnicalHealthSummary(t: TechnicalHealthIntelligence) {
    SectionCard("Technical Integrity") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(t.overallStatus, color = if (t.overallStatus == "HEALTHY") AuraSuccess else Color(0xFFFFB74D))
                Spacer(modifier = Modifier.weight(1f))
                Text("Startup: ${t.startupTimeMs}ms", style = MaterialTheme.typography.labelSmall, color = AuraOnSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricColumnSmall("CRASH RATE", "${t.crashRate}%")
                MetricColumnSmall("DB HEALTH", t.dbHealth)
                MetricColumnSmall("REGRESSIONS", t.performanceRegressions.toString())
            }
        }
    }
}

@Composable
private fun MetricColumnSmall(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AuraOnSurface)
    }
}

@Composable
private fun PipelineSummarySection(improvements: List<SuggestedImprovement>, onNavigate: (String) -> Unit) {
    SectionCard("Optimization Pipeline") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val pending = improvements.filter { it.status == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT || it.status == IntelligenceLifecycleState.NEEDS_REVIEW }
            Text("${pending.size} Recommendations awaiting authorization", style = MaterialTheme.typography.bodyMedium, color = AuraMutedSlate)
            pending.take(3).forEach { imp ->
                Surface(
                    onClick = { onNavigate(imp.id) },
                    color = DiscoveryViolet.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DiscoveryViolet.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(imp.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AuraMidnight)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = AuraSubtleBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImplementationStatusSection(runs: List<ImplementationRun>, onNavigate: (String) -> Unit) {
    SectionCard("Active Executions") {
        if (runs.isEmpty()) {
            Text("No active implementation work.", style = MaterialTheme.typography.bodyMedium, color = AuraMutedSlate)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                runs.take(3).forEach { run ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(run.improvementId) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(DiscoveryViolet, RoundedCornerShape(3.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(run.id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = DiscoveryViolet, modifier = Modifier.weight(1f))
                        StatusBadge(run.status.name)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp).padding(start = 8.dp), tint = AuraSubtleBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun RisksAndRegressionsSection(alerts: List<RegressionAlert>, onNavigate: (String) -> Unit) {
    if (alerts.isEmpty()) return
    SectionCard("Active Issues", containerColor = Color(0xFFFFFBFA)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            alerts.forEach { alert ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onNavigate(alert.improvementId) }) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFBA1A1A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(alert.affectedMetric, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFFBA1A1A), modifier = Modifier.weight(1f))
                    StatusBadge("CRITICAL", color = Color(0xFFBA1A1A))
                }
            }
        }
    }
}

@Composable
private fun RecentlyValidatedSection(results: List<ValidationResult>, onNavigate: (String) -> Unit) {
    SectionCard("Validated Successes") {
        if (results.isEmpty()) {
            Text("No improvements recently validated.", style = MaterialTheme.typography.bodyMedium, color = AuraOnSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.forEach { res ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onNavigate(res.improvementId) }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(res.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Confirmed Impact: +${"%.1f".format(res.change)}%", style = MaterialTheme.typography.bodySmall, color = AuraOnSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = AuraBorder)
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.ui.theme.DiscoveryViolet
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IntelligenceOverviewScreen(
    repository: IntelligenceRepository,
    onNavigateToImprovement: (String) -> Unit,
    onNavigateToFinding: (String) -> Unit,
    onViewMasterReport: () -> Unit
) {
    val viewModel: IntelligenceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceViewModel(repository) as T
            }
        }
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val report = state.masterReport

    if (state.isLoading || report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DiscoveryViolet)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraCrispWhite)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // TOP EXECUTIVE SUMMARY
            SummaryMessageHeader(report.executiveSummary)

            // SYSTEM HEALTH AT A GLANCE
            SystemStatusHeader(report.executiveSummary)

            // SINCE YOUR LAST REVIEW
            SinceLastReviewSection(report.sinceLastReview, onNavigateToImprovement, onNavigateToFinding)

            // WHAT AURA RECENTLY LEARNED
            RecentLearningSection(report.systemAnalysis)

            // YOUR NEXT DECISION
            NextDecisionSection(state.decisionCenter, onNavigateToImprovement)

            // WHAT'S WORKING
            WhatsWorkingSection()

            // RECENT ACTIVITY
            RecentActivitySection(report.sinceLastReview.items)

            // MASTER INTELLIGENCE (SECONDARY LAYER)
            MasterIntelligenceAccess(onViewMasterReport = onViewMasterReport)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SummaryMessageHeader(summary: ExecutiveSummary) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "Intelligence",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = AuraMidnight,
            letterSpacing = (-1).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = summary.plainEnglishSummary,
            style = MaterialTheme.typography.titleMedium,
            color = AuraSlate,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SystemStatusHeader(summary: ExecutiveSummary) {
    Column {
        Text("HEALTH AT A GLANCE", fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = AuraMutedSlate, letterSpacing = 1.2.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusMiniCard("Personalization", "Strong", AuraSuccess, Modifier.weight(1f))
            StatusMiniCard("Technical", "Healthy", AuraSuccess, Modifier.weight(1f))
            val attentionCount = summary.metrics.needsReview + summary.metrics.activeRegressions
            StatusMiniCard("Attention", attentionCount.toString(), if (summary.metrics.activeRegressions > 0) Color(0xFFE57373) else DiscoveryViolet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusMiniCard(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = AuraMutedSlate, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun SinceLastReviewSection(update: ReviewUpdate, onNavigateToImp: (String) -> Unit, onNavigateToFind: (String) -> Unit) {
    SectionCard("Since Your Last Review") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReviewMetric("${update.newFindings}", "Findings")
            ReviewMetric("${update.newImprovements}", "Improvements")
            ReviewMetric("${update.newRegressions}", "Regressions", isAlert = update.newRegressions > 0)
        }
        
        if (update.items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AuraBorder.copy(alpha = 0.5f)))
            Spacer(modifier = Modifier.height(12.dp))
            
            update.items.take(2).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            if (item.type == "Finding") onNavigateToFind(item.targetId) 
                            else onNavigateToImp(item.targetId)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(DiscoveryViolet, RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(item.title, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f), color = AuraMidnight)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuraSubtleBorder, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ReviewMetric(value: String, label: String, isAlert: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (isAlert) Color(0xFFE57373) else DiscoveryViolet)
        Text(label, fontSize = 11.sp, color = AuraMutedSlate)
    }
}

@Composable
private fun RecentLearningSection(analysis: SystemAnalysisSummary) {
    SectionCard("Recent Intelligence") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(analysis.summary, style = MaterialTheme.typography.bodyLarge, color = AuraSlate, fontWeight = FontWeight.Medium)
            analysis.evidenceSummary?.let { evidence ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = DiscoveryViolet.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${evidence.sampleCount} production samples validated.", style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate)
                }
            }
        }
    }
}

@Composable
private fun WhatsWorkingSection() {
    SectionCard("Operational Status") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkingItem("Personalization", "Baseline exceeded (+14%)")
            WorkingItem("Stability", "100% crash-free sessions")
            WorkingItem("Engagement", "Session depth improving")
        }
    }
}

@Composable
private fun WorkingItem(title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(32.dp).background(AuraSuccess.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AuraMidnight)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate)
        }
    }
}

@Composable
private fun NextDecisionSection(dc: DecisionCenterState?, onNavigate: (String) -> Unit) {
    val next = dc?.attentionItems?.firstOrNull() ?: return
    
    SectionCard("Primary Action Required", containerColor = DiscoveryViolet.copy(alpha = 0.02f)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge("Decision Required", color = DiscoveryViolet)
                Spacer(modifier = Modifier.weight(1f))
                PriorityBadge(next.priority)
            }
            Text(next.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AuraMidnight)
            Text(next.description, style = MaterialTheme.typography.bodyMedium, color = AuraSlate, lineHeight = 20.sp)
            
            Button(
                onClick = { onNavigate(next.targetId) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Review and Authorize", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecentActivitySection(items: List<IntelligenceChange>) {
    SectionCard("Activity Stream") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.take(4).forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(6.dp).offset(y = 6.dp).background(DiscoveryViolet, RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AuraMidnight)
                        Text(
                            text = "${item.type} · ${SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(item.timestamp))}", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = AuraMutedSlate.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterIntelligenceAccess(onViewMasterReport: () -> Unit) {
    OutlinedButton(
        onClick = onViewMasterReport,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuraMutedSlate)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp), tint = DiscoveryViolet.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Access Detailed System Analysis", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

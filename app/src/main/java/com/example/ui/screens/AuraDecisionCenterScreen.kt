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
import androidx.compose.material.icons.outlined.Shield
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
import com.example.ui.theme.AuraMagenta
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
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraDecisionCenterScreen(
    repository: IntelligenceRepository,
    onNavigateToImprovement: (String) -> Unit,
    onNavigateToFinding: (String) -> Unit,
    onNavigateToMonitoring: (String) -> Unit,
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

    val state by viewModel.state.collectAsStateWithLifecycle()
    val attentionItems by viewModel.attentionInbox.collectAsStateWithLifecycle()
    val decisionCenter = state.decisionCenter

    // MARK ITEMS AS SEEN
    LaunchedEffect(attentionItems) {
        attentionItems.forEach { item ->
            if (item.requiresAction && item.status == AttentionStatus.NEW) {
                viewModel.markAsSeen(item.id)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraCrispWhite)
    ) {
        if (state.isLoading || decisionCenter == null) {
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
                // CORE USER QUESTION
                DecisionHeader(attentionItems)

                // 1. YOUR ATTENTION
                val actionableItems = attentionItems.filter { it.requiresAction && it.status != AttentionStatus.RESOLVED }
                if (actionableItems.isNotEmpty()) {
                    AttentionList(actionableItems, onNavigateToImprovement, onNavigateToFinding)
                }

                // 2. CRITICAL ISSUES
                // (Handled by Attention Inbox now)
                
                // 3. AWAITING YOUR DECISION
                // (Handled by Attention Inbox now)

                // 4. IN PROGRESS
                if (decisionCenter.inProgress.isNotEmpty()) {
                    InProgressSection(decisionCenter.inProgress, onNavigateToImprovement)
                }

                // 5. RECENTLY COMPLETED
                if (decisionCenter.recentlyCompleted.isNotEmpty()) {
                    RecentlyCompletedSection(decisionCenter.recentlyCompleted, onNavigateToImprovement)
                }

                // 6. WHAT CHANGED
                if (decisionCenter.whatChanged.isNotEmpty()) {
                    WhatChangedSection(decisionCenter.whatChanged, onNavigateToImprovement, onNavigateToFinding)
                }

                // EMPTY STATE
                if (actionableItems.isEmpty()) {
                    NoActionRequiredView(decisionCenter.inProgress.size, decisionCenter.recentlyCompleted.size, attentionItems)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun DecisionHeader(attentionItems: List<AttentionItem>) {
    val itemsCount = attentionItems.count { it.requiresAction && it.status != AttentionStatus.RESOLVED }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = if (itemsCount > 0) "$itemsCount Actionable Briefings" else "Intelligence Briefing",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (attentionItems.any { it.priority == DecisionPriority.CRITICAL }) Color(0xFFD32F2F) else AuraMidnight,
            letterSpacing = (-1).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (itemsCount > 0) "Review automated findings and authorize pending system optimizations." else "Aura is currently monitoring system integrity and learning from production telemetry.",
            style = MaterialTheme.typography.bodyLarge,
            color = AuraSlate,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun AttentionList(items: List<AttentionItem>, onNavigateImp: (String) -> Unit, onNavigateFind: (String) -> Unit) {
    SectionCard("Items Requiring Your Decision") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { item ->
                DecisionItemCard(item, onNavigateImp, onNavigateFind)
            }
        }
    }
}

@Composable
private fun DecisionItemCard(item: AttentionItem, onNavigateImp: (String) -> Unit, onNavigateFind: (String) -> Unit) {
    Surface(
        onClick = { 
            if (item.sourceType == "Finding") onNavigateFind(item.sourceId)
            else onNavigateImp(item.sourceId)
        },
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title, 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, 
                    color = AuraMidnight,
                    modifier = Modifier.weight(1f)
                )
                PriorityBadge(item.priority)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.summary, 
                style = MaterialTheme.typography.bodyMedium,
                color = AuraSlate
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.whyItMatters, 
                style = MaterialTheme.typography.labelSmall,
                color = AuraMutedSlate,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = { 
                    if (item.sourceType == "Finding") onNavigateFind(item.sourceId)
                    else onNavigateImp(item.sourceId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Review Decision", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InProgressSection(items: List<InWorkImprovement>, onNavigate: (String) -> Unit) {
    SectionCard("Active Workflows") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onNavigate(item.id) }) {
                    Box(
                        modifier = Modifier.size(36.dp).background(DiscoveryViolet.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DiscoveryViolet,
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AuraMidnight)
                        Text(item.progressDescription, style = MaterialTheme.typography.labelSmall, color = DiscoveryViolet, fontWeight = FontWeight.Bold)
                        if (item.currentEvidence != null) {
                            Text(item.currentEvidence, style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AuraSubtleBorder, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RecentlyCompletedSection(items: List<IntelligenceChange>, onNavigate: (String) -> Unit) {
    SectionCard("Recently Closed") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.take(3).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onNavigate(item.targetId) }) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    StatusBadge(item.state.name, color = AuraSuccess)
                }
            }
        }
    }
}

@Composable
private fun WhatChangedSection(items: List<IntelligenceChange>, onNavigateImp: (String) -> Unit, onNavigateFind: (String) -> Unit) {
    SectionCard("Intelligence Briefing") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.take(4).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { 
                    if (item.type == "Finding") onNavigateFind(item.targetId) else onNavigateImp(item.targetId)
                }) {
                    Box(modifier = Modifier.size(6.dp).background(DiscoveryViolet, RoundedCornerShape(3.dp)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AuraMidnight)
                        Text(
                            text = "${item.type} · ${SimpleDateFormat("MMM d", Locale.US).format(Date(item.timestamp))}", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = AuraMutedSlate.copy(alpha = 0.6f)
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AuraSubtleBorder, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun NoActionRequiredView(inProgressCount: Int, recentlyCompletedCount: Int, attentionItems: List<AttentionItem>) {
    Surface(
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AuraLogoIcon(size = 64.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Aura is Protecting Your System", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, 
                color = AuraMidnight
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No urgent decisions require your attention right now. All automated policies are performing within limits.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = AuraSlate,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AuraSubtleBorder.copy(alpha = 0.4f)))
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatusMiniMetric(inProgressCount.toString(), "Monitoring")
                StatusMiniMetric(recentlyCompletedCount.toString(), "Validated")
                StatusMiniMetric(
                    attentionItems.count { it.attentionType == AttentionType.REGRESSION_DETECTED && it.status != AttentionStatus.RESOLVED }.toString(), 
                    "Alerts"
                )
            }
        }
    }
}

@Composable
private fun StatusMiniMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DiscoveryViolet)
        Text(label, fontSize = 10.sp, color = AuraMutedSlate)
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.SectionCard
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.DiscoveryViolet

@Composable
fun IntelligenceExecutionDashboard(
    repository: IntelligenceRepository,
    onNavigateToImprovement: (String) -> Unit
) {
    val improvements by repository.getAllImprovements().collectAsState(initial = emptyList())
    
    val activeImplementations = improvements.filter { it.status in listOf(
        IntelligenceLifecycleState.IMPLEMENTATION_PLANNED,
        IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS,
        IntelligenceLifecycleState.IMPLEMENTATION_COMPLETE,
        IntelligenceLifecycleState.DEVIATION_DETECTED,
        IntelligenceLifecycleState.IMPLEMENTATION_FAILED
    ) }
    
    val monitoring = improvements.filter { it.status == IntelligenceLifecycleState.MONITORING }
    val validated = improvements.filter { it.status == IntelligenceLifecycleState.VALIDATED }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AuraCrispWhite).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Operational Intelligence", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuraMidnight)
            Text("Tracking the execution of approved optimizations.", style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate)
        }

        if (activeImplementations.isNotEmpty()) {
            item {
                SectionCard("Active Executions") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        activeImplementations.forEach { imp ->
                            ExecutionRow(imp, onNavigateToImprovement)
                        }
                    }
                }
            }
        }

        if (monitoring.isNotEmpty()) {
            item {
                SectionCard("Active Monitoring") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        monitoring.forEach { imp ->
                            ExecutionRow(imp, onNavigateToImprovement)
                        }
                    }
                }
            }
        }

        if (validated.isNotEmpty()) {
            item {
                SectionCard("Recently Validated") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        validated.take(5).forEach { imp ->
                            ExecutionRow(imp, onNavigateToImprovement)
                        }
                    }
                }
            }
        }
        
        if (activeImplementations.isEmpty() && monitoring.isEmpty() && validated.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.example.ui.components.AuraLogoIcon(size = 64.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No active optimizations in progress.",
                            color = AuraMutedSlate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecutionRow(
    improvement: SuggestedImprovement,
    onClick: (String) -> Unit
) {
    Surface(
        onClick = { onClick(improvement.id) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        color = AuraSubtleSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when (improvement.status) {
                IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS -> Icons.Default.Sync to DiscoveryViolet
                IntelligenceLifecycleState.IMPLEMENTATION_FAILED -> Icons.Default.Error to Color.Red
                IntelligenceLifecycleState.DEVIATION_DETECTED -> Icons.Default.Warning to Color.Red
                IntelligenceLifecycleState.MONITORING -> Icons.Default.Visibility to DiscoveryViolet
                IntelligenceLifecycleState.VALIDATED -> Icons.Default.CheckCircle to Color(0xFF4ADE80)
                else -> Icons.Default.Schedule to AuraMutedSlate
            }
            
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(improvement.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AuraMidnight)
                Text(improvement.status.name.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = AuraMutedSlate)
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuraSubtleBorder)
        }
    }
}

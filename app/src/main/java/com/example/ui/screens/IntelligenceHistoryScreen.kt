package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.components.*
import com.example.data.*
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.AuraSurface
import com.example.ui.theme.AuraSurfaceVariant
import com.example.ui.theme.DiscoveryViolet
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IntelligenceHistoryScreen(
    repository: IntelligenceRepository
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
    val events by viewModel.events.collectAsStateWithLifecycle()
    val items = state.masterReport?.sinceLastReview?.items ?: emptyList()
    var showEventAudit by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AuraCrispWhite),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intelligence Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = AuraMidnight)
                TextButton(onClick = { showEventAudit = !showEventAudit }) {
                    Text(if (showEventAudit) "Hide Audit" else "Technical Audit", fontSize = 11.sp, color = DiscoveryViolet)
                }
            }
        }

        if (showEventAudit) {
            items(events) { event ->
                EventAuditRow(event)
            }
            item {
                Box(modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(AuraBorder.copy(alpha = 0.5f)))
            }
        }

        if (items.isEmpty() && !showEventAudit) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AuraLogoIcon(size = 64.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No historical intelligence records available.",
                            color = AuraMutedSlate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(items) { item ->
                HistoryItemRow(item)
            }
        }
    }
}

@Composable
private fun EventAuditRow(event: com.example.data.db.IntelligenceEventEntity) {
    Surface(
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(event.type, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f), color = AuraMidnight)
                StatusBadge(event.status)
            }
            Text("Source: ${event.sourceId}", fontSize = 10.sp, color = AuraMutedSlate, fontFamily = FontFamily.Monospace)
            Text(SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(event.timestamp)), fontSize = 9.sp, color = AuraMutedSlate)
            if (event.failureReason != null) {
                Text("Error: ${event.failureReason}", color = Color(0xFFE57373), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun HistoryItemRow(item: IntelligenceChange) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(modifier = Modifier.size(8.dp).offset(y = 6.dp).background(DiscoveryViolet, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(AuraSubtleBorder))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = SimpleDateFormat("MMM d · HH:mm", Locale.US).format(Date(item.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = AuraMutedSlate,
                fontWeight = FontWeight.Bold
            )
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AuraMidnight)
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate)
            
            Spacer(modifier = Modifier.height(8.dp))
            StatusBadge(item.type, color = if (item.type == "Finding") Color(0xFF81D4FA) else DiscoveryViolet)
        }
    }
}

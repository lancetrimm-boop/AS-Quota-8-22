package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
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
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IntelligenceInboxScreen(
    repository: IntelligenceRepository,
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

    val items by viewModel.attentionInbox.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf<AttentionType?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // FILTER BAR
        ScrollableTabRow(
            selectedTabIndex = if (selectedFilter == null) 0 else selectedFilter!!.ordinal + 1,
            containerColor = Color.White,
            contentColor = AuraPurple,
            edgePadding = 16.dp,
            divider = {}
        ) {
            Tab(selected = selectedFilter == null, onClick = { selectedFilter = null }) {
                Text("All Updates", modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp), fontSize = 13.sp, fontWeight = if (selectedFilter == null) FontWeight.Bold else FontWeight.Medium)
            }
            AttentionType.entries.forEach { type ->
                Tab(selected = selectedFilter == type, onClick = { selectedFilter = type }) {
                    Text(
                        text = type.name.replace("_", " ").lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp), 
                        fontSize = 13.sp,
                        fontWeight = if (selectedFilter == type) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        val filteredItems = if (selectedFilter == null) items else items.filter { it.attentionType == selectedFilter }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AuraLogoIcon(size = 64.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Your intelligence inbox is empty.", color = AuraOnSurfaceVariant)
                        }
                    }
                }
            } else {
                items(filteredItems) { item ->
                    AttentionItemCard(
                        item = item,
                        onClick = {
                            if (item.sourceType == "Finding") onNavigateToFinding(item.sourceId)
                            else onNavigateToImprovement(item.sourceId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttentionItemCard(item: AttentionItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttentionIcon(item.attentionType)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AuraOnSurface)
                    Text(
                        text = SimpleDateFormat("MMM d · HH:mm", Locale.US).format(Date(item.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = AuraOnSurfaceVariant
                    )
                }
                PriorityBadge(item.priority)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.summary, 
                style = MaterialTheme.typography.bodyMedium,
                color = AuraOnSurface,
                lineHeight = 20.sp
            )
            
            if (item.requiresAction && item.status != AttentionStatus.RESOLVED) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = AuraPurple.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AuraPurple, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decision Required", fontSize = 12.sp, color = AuraPurple, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AuraPurple, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionIcon(type: AttentionType) {
    val (icon, color) = when (type) {
        AttentionType.DECISION_REQUIRED -> Icons.Default.Gavel to AuraPurple
        AttentionType.REGRESSION_DETECTED -> Icons.Default.Error to Color(0xFFE57373)
        AttentionType.EXECUTION_FAILURE -> Icons.Default.Warning to Color(0xFFFFB74D)
        AttentionType.VALIDATION_COMPLETE -> Icons.Default.CheckCircle to Color(0xFF81C784)
        else -> Icons.Default.Info to Color(0xFF81D4FA)
    }
    Box(
        modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

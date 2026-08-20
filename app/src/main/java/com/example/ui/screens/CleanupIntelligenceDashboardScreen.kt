package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.cleanup.CleanupCategory
import com.example.data.cleanup.CleanupRecommendation
import com.example.data.MediaItem
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupIntelligenceDashboardScreen(
    viewModel: CleanupIntelligenceViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cleanup Intelligence", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAnalysis() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        containerColor = AuraBackground
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraPurple)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Section 1: Summary Card
                item {
                    SummaryCard(uiState)
                }

                // Section 2: Category Breakdown
                item {
                    CategoryBreakdownSection(uiState)
                }

                // Section 3: Sorting Controls
                item {
                    SortControls(uiState.currentSort, onSortChange = { viewModel.updateSort(it) })
                }

                // Section 4: Lowest Keep Score Media
                item {
                    Text("Lowest Keep Score Media", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AuraOnSurface)
                }
                
                if (uiState.lowestScoreItems.isEmpty()) {
                    item {
                        Text("No cleanup recommendations found.", color = AuraOnSurfaceVariant)
                    }
                } else {
                    items(uiState.lowestScoreItems) { rec ->
                        RecommendationItemCard(rec)
                    }
                }

                // Section 5: Highest Keep Score Media
                item {
                    Text("Highest Keep Score Media (Protected)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AuraOnSurface)
                }
                
                items(uiState.highestScoreItems) { item ->
                    HighValueItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(uiState: CleanupIntelligenceUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Smart Cleanup Summary", fontWeight = FontWeight.Bold, color = AuraPurple)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryMetric("Recommendations", uiState.totalRecommendations.toString())
                SummaryMetric("Potential Recovery", formatSize(uiState.potentialStorageRecovery))
                SummaryMetric("Avg Confidence", "${(uiState.averageConfidence * 100).toInt()}%")
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = AuraOnSurfaceVariant, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = AuraOnSurface)
    }
}

@Composable
private fun CategoryBreakdownSection(uiState: CleanupIntelligenceUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Category Breakdown", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AuraOnSurface)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategorySmallCard("Forgotten", uiState.forgottenCount, Modifier.weight(1f))
            CategorySmallCard("Never Connected", uiState.neverConnectedCount, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategorySmallCard("Space Hogs", uiState.spaceHogCount, Modifier.weight(1f))
            CategorySmallCard("Redundant", uiState.redundantCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategorySmallCard(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = AuraSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = AuraOnSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(count.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AuraPurple)
        }
    }
}

@Composable
private fun SortControls(currentSort: CleanupSort, onSortChange: (CleanupSort) -> Unit) {
    Column {
        Text("Validation Sort", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CleanupSortChip("Lowest Score", currentSort == CleanupSort.LOWEST_KEEP_SCORE) { onSortChange(CleanupSort.LOWEST_KEEP_SCORE) }
            CleanupSortChip("Confidence", currentSort == CleanupSort.HIGHEST_CONFIDENCE) { onSortChange(CleanupSort.HIGHEST_CONFIDENCE) }
            CleanupSortChip("Impact", currentSort == CleanupSort.LARGEST_STORAGE_IMPACT) { onSortChange(CleanupSort.LARGEST_STORAGE_IMPACT) }
        }
    }
}

@Composable
private fun CleanupSortChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier.height(32.dp),
        color = if (isSelected) AuraPurple.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            brush = if (isSelected) DiscoveryGradient else androidx.compose.ui.graphics.SolidColor(AuraMutedSlate.copy(alpha = 0.5f))
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) AuraPurple else AuraMutedSlate
            )
        }
    }
}

@Composable
private fun RecommendationItemCard(rec: CleanupRecommendation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rec.mediaId, fontSize = 10.sp, color = AuraOnSurfaceVariant)
                    Text(rec.category.name, fontWeight = FontWeight.Bold, color = AuraPurple)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("KEEP SCORE", fontSize = 10.sp, color = AuraOnSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text("${(rec.keepScore * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (rec.keepScore < 0.3f) Color.Red else AuraOnSurface)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text("WHY:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
            Text(rec.explanation, fontSize = 13.sp, color = AuraOnSurface)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Confidence: ${(rec.confidenceScore * 100).toInt()}%", fontSize = 11.sp, color = AuraOnSurfaceVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Impact: ${formatSize(rec.storageSize)}", fontSize = 11.sp, color = AuraOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HighValueItemCard(item: MediaItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AuraSurface.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Green.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    text = if (item.isFavorite) "Favorite protected" else "High engagement",
                    fontSize = 10.sp,
                    color = AuraOnSurfaceVariant
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

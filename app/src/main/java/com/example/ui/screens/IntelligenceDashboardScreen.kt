package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.intelligence.CalibrationStatus
import com.example.ui.components.TasteRadarChart

/**
 * Main dashboard for user-facing local intelligence insights (Phase 4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceDashboardScreen(
    viewModel: IntelligenceDashboardViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val report = state.report

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Intelligence Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Info, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (report != null) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Maturity & Learning Stats
                MaturityCard(report.maturity)

                // 2. Taste DNA Radar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TasteRadarChart(
                            dimensions = report.tasteProfile.dimensions,
                            modifier = Modifier.size(280.dp)
                        )
                        
                        if (report.maturity.calibrationStatus == CalibrationStatus.INITIALIZING) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text(
                                    "Continue comparing items in 'Compare' to refine your Taste DNA profile.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 3. Top Traits
                if (report.tasteProfile.topTraits.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Core Style Identifiers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        FlowRow(
                            mainAxisSpacing = 8.dp,
                            crossAxisSpacing = 8.dp
                        ) {
                            report.tasteProfile.topTraits.forEach { trait ->
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(trait) }
                                )
                            }
                        }
                    }
                }

                // 4. Interaction Quality
                EngagementGrid(report.engagement)
            }
        }
    }
}

@Composable
private fun MaturityCard(maturity: com.example.data.intelligence.AuraMaturitySnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Personalization Confidence (Signal Quality)
            Text(
                "Personalization Confidence",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { maturity.personalizationConfidence.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Text(
                "How well Aura understands your aesthetic preferences.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Data Coverage (Signal Quantity)
            Text(
                "Library Learning Coverage",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { maturity.dataCoverage.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
            )
            Text(
                "Proportion of your library that Aura has evaluated.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("STATUS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    Text(maturity.calibrationStatus.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LEARNING DATA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    Text("${maturity.totalInteractionsAnalyzed} signals across ${maturity.itemsInLearningPool} items", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun EngagementGrid(engagement: com.example.data.intelligence.EngagementSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Engagement Insight", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox("Comp. Rate", "${(engagement.completionRate * 100).toInt()}%", Modifier.weight(1f))
            MetricBox("Fav. Density", "${(engagement.favoriteDensity * 100).toInt()}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox("Skip Velocity", "${engagement.averageSkipVelocity}/min", Modifier.weight(1f))
            MetricBox("Peak Hour", "${engagement.mostActiveHour}:00", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    // Basic implementation of a FlowRow as a simple placeholder for layout
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        var layoutHeight = 0
        var layoutWidth = 0
        
        // Simple layout logic for mock-up
        layoutWidth = constraints.maxWidth
        layoutHeight = (placeables.size / 2 + 1) * 48 // Simplified height
        
        layout(layoutWidth, layoutHeight) {
            var x = 0
            var y = 0
            placeables.forEach { placeable ->
                if (x + placeable.width > constraints.maxWidth) {
                    x = 0
                    y += placeable.height + crossAxisSpacing.roundToPx()
                }
                placeable.placeRelative(x, y)
                x += placeable.width + mainAxisSpacing.roundToPx()
            }
        }
    }
}

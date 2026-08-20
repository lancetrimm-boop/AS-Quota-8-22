package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingDetailScreen(
    findingId: String,
    repository: IntelligenceRepository,
    onBack: () -> Unit,
    onViewImprovement: (String) -> Unit
) {
    val viewModel: IntelligenceViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceViewModel(repository) as T
            }
        }
    )

    LaunchedEffect(findingId) {
        viewModel.markAsReviewed(findingId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val explanation by viewModel.explanation.collectAsStateWithLifecycle()
    val finding = state.findings.find { it.id == findingId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        AuraTopBar(
            title = "Finding Detail",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuraPurple)
                }
            },
            showLogo = false
        )

        if (finding == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Finding not found.", color = AuraOnSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Column {
                    Text(
                        text = "INTELLIGENCE FINDING · ${finding.id}", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold, 
                        color = AuraPurple.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = finding.title, 
                        style = MaterialTheme.typography.headlineMedium, 
                        fontWeight = FontWeight.ExtraBold,
                        color = AuraOnSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FindingClassificationBadge(finding.classification)
                }

                SectionCard("What Aura Observed") {
                    Text(
                        text = finding.summary, 
                        style = MaterialTheme.typography.bodyLarge, 
                        color = AuraOnSurface,
                        lineHeight = 24.sp
                    )
                }

                SectionCard("Evidence & Performance") {
                    EvidenceSummaryView(
                        summary = EvidenceSummary(
                            sampleCount = finding.technicalDetails.evidence.productionCount,
                            baseline = finding.technicalDetails.baselineState.engagementScore,
                            currentResult = finding.technicalDetails.actualOutcome.measuredScore ?: 0.0,
                            change = (finding.technicalDetails.actualOutcome.measuredScore ?: 0.0) - finding.technicalDetails.baselineState.engagementScore,
                            evidenceAge = "Updated recently",
                            confidence = finding.confidence,
                            hasRegression = finding.classification == FindingClassification.REGRESSION
                        ),
                        onWhyConfidenceClick = { viewModel.askAuraToExplainFinding(finding.id) }
                    )
                }

                SectionCard("Recommendation") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = if (finding.classification == FindingClassification.IMPROVEMENT_OPPORTUNITY) 
                                "Aura has identified a low-risk opportunity to optimize performance further based on current production data." 
                                else "Aura is continuing to monitor the system. No immediate intervention is required at this stage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuraOnSurfaceVariant
                        )
                        
                        // Action
                        if (finding.lifecycleState == IntelligenceLifecycleState.SUGGESTED_IMPROVEMENT) {
                            val improvement = state.improvements.find { it.findingId == finding.id }
                            if (improvement != null) {
                                Button(
                                    onClick = { onViewImprovement(improvement.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(12.dp)
                                ) {
                                    Text("Review Suggested Improvement", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.askAuraToExplainFinding(finding.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ask Aura to Explain")
                        }
                    }
                }
            }
        }
    }

    if (explanation != null) {
        ExplanationDialog(
            explanation = explanation!!,
            onDismiss = { viewModel.clearExplanation() }
        )
    }
}

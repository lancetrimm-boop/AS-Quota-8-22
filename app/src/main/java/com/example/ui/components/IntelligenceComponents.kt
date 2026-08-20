package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.data.intelligence.*
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
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryMagenta
import com.example.ui.theme.DiscoveryViolet
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IntelligenceMetricCard(
    label: String,
    value: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(120.dp)
            .height(80.dp)
            .padding(4.dp),
        color = if (isSelected) DiscoveryViolet else AuraSubtleSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) DiscoveryViolet else AuraSubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) AuraCrispWhite else AuraMidnight
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) AuraCrispWhite.copy(alpha = 0.8f) else AuraMutedSlate,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun FindingCard(
    finding: Finding,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AuraCrispWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FindingClassificationBadge(finding.classification)
                Text(
                    text = SimpleDateFormat("MMM d", Locale.US).format(Date(finding.dateDiscovered)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AuraMutedSlate
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = finding.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = finding.summary,
                fontSize = 14.sp,
                color = AuraSlate,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = DiscoveryViolet
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${finding.technicalDetails.evidence.productionCount} samples",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiscoveryViolet
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AuraSubtleBorder, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ImprovementReviewCard(
    improvement: SuggestedImprovement,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = AuraCrispWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(improvement.status.name, color = if (improvement.status == IntelligenceLifecycleState.NEEDS_REVIEW) DiscoveryViolet else AuraSuccess)
                PriorityBadge(improvement.priority)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = improvement.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AuraMidnight
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("EXPECTED IMPACT", improvement.expectedImpact, Modifier.weight(1f))
                MetricItem("RISK", improvement.risk, Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                Text("Review Recommendation", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AuraMutedSlate, letterSpacing = 0.5.sp)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AuraMidnight)
    }
}

@Composable
fun ActionStatusBadge(status: ActionStatus) {
    val result: Pair<Color, String> = when (status) {
        ActionStatus.ACTION_REQUIRED -> Color(0xFFE57373) to "Action Required"
        ActionStatus.REVIEW_RECOMMENDED -> DiscoveryViolet to "Review Recommended"
        ActionStatus.CONTINUE_MONITORING -> Color(0xFF81D4FA) to "Monitoring"
        ActionStatus.MORE_EVIDENCE_NEEDED -> Color(0xFFFFB74D) to "More Evidence Needed"
        ActionStatus.NO_ACTION_REQUIRED -> AuraSuccess to "No Action Required"
    }
    val (color, text) = result
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun EvidenceSummaryView(summary: EvidenceSummary, onWhyConfidenceClick: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            EvidenceMetric("Samples", summary.sampleCount.toString())
            EvidenceMetric("Result", "%.2f".format(summary.currentResult))
            EvidenceMetric("Change", (if (summary.change >= 0) "+" else "") + "%.2f".format(summary.change))
        }
        
        ConfidenceSection(
            level = summary.confidence,
            explanation = "Evidence collected from production telemetry.",
            onWhyClick = onWhyConfidenceClick
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (summary.hasRegression) Icons.Default.Warning else Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (summary.hasRegression) Color(0xFFE57373) else AuraSuccess
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (summary.hasRegression) "Regression detected" else "No regression detected",
                fontSize = 13.sp,
                color = AuraMidnight
            )
        }

        var showTechnical by remember { mutableStateOf(false) }
        
        TextButton(
            onClick = { showTechnical = !showTechnical },
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showTechnical) "Hide Technical Evidence" else "View Technical Evidence",
                    fontSize = 12.sp,
                    color = DiscoveryViolet
                )
                Icon(
                    if (showTechnical) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = DiscoveryViolet
                )
            }
        }

        AnimatedVisibility(
            visible = showTechnical,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = AuraSubtleSurface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("TECHNICAL DATA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AuraMutedSlate)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Baseline: ${summary.baseline}\nCurrent: ${summary.currentResult}\nConfidence Score: ${summary.confidence.name}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AuraMidnight
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceMetric(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = AuraMutedSlate)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AuraMidnight)
    }
}

@Composable
fun ConfidenceSection(level: ConfidenceLevel, explanation: String, onWhyClick: (() -> Unit)? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Confidence: ", fontSize = 13.sp, color = AuraMutedSlate)
            Text(
                text = level.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = when (level) {
                    ConfidenceLevel.HIGH -> AuraSuccess
                    ConfidenceLevel.MEDIUM -> Color(0xFFFFB74D)
                    ConfidenceLevel.LOW -> Color(0xFFE57373)
                }
            )
            if (onWhyClick != null) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onWhyClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(24.dp)) {
                    Text("Why?", fontSize = 11.sp, color = DiscoveryViolet)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(explanation, fontSize = 13.sp, color = AuraMutedSlate, lineHeight = 18.sp)
    }
}

@Composable
fun FindingClassificationBadge(classification: FindingClassification) {
    val (color, text) = when (classification) {
        FindingClassification.ACTION_REQUIRED -> Color(0xFFE57373) to "Action Required"
        FindingClassification.IMPROVEMENT_OPPORTUNITY -> DiscoveryViolet to "Opportunity"
        FindingClassification.INFORMATIONAL -> Color(0xFF81D4FA) to "Info"
        FindingClassification.NO_ACTION_REQUIRED -> AuraSuccess to "No Action"
        FindingClassification.INSUFFICIENT_EVIDENCE -> Color(0xFFFFB74D) to "Low Evidence"
        FindingClassification.REGRESSION -> Color(0xFFBA1A1A) to "Regression"
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun PriorityBadge(priority: DecisionPriority) {
    PriorityBadge(priority.name)
}

@Composable
fun PriorityBadge(priority: String) {
    val (color, label) = when (priority.uppercase()) {
        "CRITICAL" -> Color(0xFFBA1A1A) to "Critical"
        "HIGH" -> Color(0xFFE64A19) to "High"
        "MEDIUM", "NORMAL" -> Color(0xFFFBC02D) to "Medium"
        else -> Color(0xFF81C784) to "Low"
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun TechnicalRow(label: String, value: String) {
    Row {
        Text("$label: ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
        Text(value, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = AuraOnSurface)
    }
}

@Composable
fun ExplanationDialog(
    explanation: IntelligenceExplanation,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = "Aura Briefing".uppercase(), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = DiscoveryViolet.copy(alpha = 0.5f), 
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = explanation.summary, 
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = AuraMidnight
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // REASONING
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    explanation.reasoning.forEach { line ->
                        Text(
                            text = line, 
                            style = MaterialTheme.typography.bodyLarge, 
                            color = AuraMidnight,
                            lineHeight = 24.sp
                        )
                    }
                }

                // CONFIDENCE & EVIDENCE STRENGTH
                if ((explanation.confidence != null) || (explanation.evidenceStrength != null)) {
                    Surface(
                        color = DiscoveryViolet.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DiscoveryViolet.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            explanation.evidenceStrength?.let { strength ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("DATA STRENGTH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AuraMutedSlate)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(strength.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = if (strength == "High") AuraSuccess else DiscoveryViolet)
                                }
                            }
                            
                            explanation.confidence?.let { conf ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = DiscoveryViolet, modifier = Modifier.size(16.dp).offset(y = 2.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("CONFIDENCE: ${conf.name}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = DiscoveryViolet)
                                        Text(
                                            text = when(conf) {
                                                ConfidenceLevel.HIGH -> "Based on consistent production evidence with significant sample size."
                                                ConfidenceLevel.MEDIUM -> "Supported by production patterns, but additional samples would increase certainty."
                                                else -> "Based on limited observations; results may vary with larger data sets."
                                            },
                                            style = MaterialTheme.typography.bodySmall, 
                                            color = AuraMutedSlate,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // WHAT WILL CHANGE
                if (explanation.whatWillChange.isNotEmpty()) {
                    SectionDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WHAT WILL CHANGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = DiscoveryViolet, letterSpacing = 0.5.sp)
                        explanation.whatWillChange.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = AuraMidnight) }
                    }
                }

                // WHAT WILL NOT CHANGE
                if (explanation.whatWillNotChange.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WHAT WILL NOT CHANGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = AuraMutedSlate, letterSpacing = 0.5.sp)
                        explanation.whatWillNotChange.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate) }
                    }
                }

                // LIMITATIONS
                if (explanation.limitations.isNotEmpty()) {
                    SectionDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("UNCERTAINTIES & LIMITATIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE57373), letterSpacing = 0.5.sp)
                        explanation.limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = AuraMutedSlate) }
                    }
                }

                // TECHNICAL TRACEABILITY
                var showTechnical by remember { mutableStateOf(false) }
                Column {
                    TextButton(onClick = { showTechnical = !showTechnical }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showTechnical) "Hide Technical Traceability" else "View Technical Traceability", style = MaterialTheme.typography.labelSmall, color = DiscoveryViolet.copy(alpha = 0.6f))
                    }
                    
                    if (showTechnical) {
                        Surface(
                            color = AuraSubtleSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                TechnicalRow("Explanation ID", explanation.explanationId)
                                TechnicalRow("Source Type", explanation.sourceType)
                                TechnicalRow("Source ID", explanation.sourceId)
                                TechnicalRow("Timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(explanation.generatedAt)))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Understand", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = AuraCrispWhite,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun SectionDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AuraBorder.copy(alpha = 0.4f)))
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AuraPurple.copy(alpha = 0.7f),
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun StatusBadge(state: String, color: Color = DiscoveryViolet) {
    Surface(
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Text(
            text = state.replace("_", " ").uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = AuraMutedSlate,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun LifecycleIndicator(
    currentStatus: IntelligenceLifecycleState,
    modifier: Modifier = Modifier
) {
    val stages = listOf(
        IntelligenceLifecycleState.NEEDS_REVIEW to "Review",
        IntelligenceLifecycleState.APPROVED to "Approved",
        IntelligenceLifecycleState.IMPLEMENTATION_IN_PROGRESS to "Implementation",
        IntelligenceLifecycleState.VERIFICATION_IN_PROGRESS to "Verification",
        IntelligenceLifecycleState.MONITORING to "Monitoring",
        IntelligenceLifecycleState.VALIDATED to "Validated"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, stage ->
            val isActive = currentStatus.ordinal >= stage.first.ordinal
            val isCurrent = currentStatus == stage.first
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 10.dp else 8.dp)
                        .background(
                            if (isActive) AuraPurple else AuraBorder.copy(alpha = 0.5f), 
                            RoundedCornerShape(5.dp)
                        )
                        .then(
                            if (isCurrent) Modifier.border(2.dp, AuraPurple.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                            else Modifier
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stage.second, 
                    fontSize = 8.sp, 
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) AuraPurple else AuraOnSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            
            if (index < stages.size - 1) {
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .weight(0.5f)
                        .offset(y = (-8).dp)
                        .background(if (currentStatus.ordinal > stage.first.ordinal) AuraPurple else AuraBorder.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
fun AuraMaturityCard(maturity: AuraMaturitySnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraSubtleSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Personalization Confidence (Signal Quality)
            Text(
                "Personalization Confidence",
                fontWeight = FontWeight.SemiBold,
                color = AuraMidnight
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { maturity.personalizationConfidence.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = DiscoveryViolet,
                trackColor = AuraSubtleBorder
            )
            Text(
                "How well Aura understands your aesthetic preferences.",
                fontSize = 10.sp,
                color = AuraMutedSlate,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Data Coverage (Signal Quantity)
            Text(
                "Library Learning Coverage",
                fontWeight = FontWeight.SemiBold,
                color = AuraMidnight
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { maturity.dataCoverage.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = DiscoveryMagenta,
                trackColor = AuraSubtleBorder
            )
            Text(
                "Proportion of your library that Aura has evaluated.",
                fontSize = 10.sp,
                color = AuraMutedSlate,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("STATUS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AuraMutedSlate)
                    Text(maturity.calibrationStatus.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraMidnight)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("LEARNING DATA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AuraMutedSlate)
                    Text("${maturity.totalInteractionsAnalyzed} signals across ${maturity.itemsInLearningPool} items", fontSize = 11.sp, color = AuraMidnight)
                }
            }
        }
    }
}

@Composable
fun AuraEngagementGrid(engagement: EngagementSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Engagement Insight", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AuraMidnight)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox("Comp. Rate", "${(engagement.completionRate * 100).toInt()}%", Modifier.weight(1f))
            MetricBox("Fav. Density", "${(engagement.favoriteDensity * 100).toInt()}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox("Skip Velocity", "%.1f/min".format(engagement.averageSkipVelocity), Modifier.weight(1f))
            MetricBox("Peak Hour", "${engagement.mostActiveHour}:00", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = AuraSubtleSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = AuraMutedSlate)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AuraMidnight)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuraFlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(mainAxisSpacing),
        verticalArrangement = Arrangement.spacedBy(crossAxisSpacing)
    ) {
        content()
    }
}


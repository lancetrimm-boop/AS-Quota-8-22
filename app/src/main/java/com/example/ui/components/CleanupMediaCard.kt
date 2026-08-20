package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MediaItem
import com.example.data.cleanup.CleanupRecommendation
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSlate
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.AuraSubtleSurface
import com.example.ui.theme.AuraSurface
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet

@Composable
fun CleanupMediaCard(
    item: MediaItem,
    recommendation: CleanupRecommendation,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onKeep: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelection() },
        colors = CardDefaults.cardColors(containerColor = AuraCrispWhite),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) DiscoveryViolet else AuraSubtleBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Thumbnail with Selection Overlay
                Box(modifier = Modifier.size(100.dp)) {
                    AuraMediaThumbnail(
                        itemId = item.id,
                        mediaType = item.mediaType,
                        imageUrl = item.imageUrl,
                        uriPath = item.uriPath,
                        title = item.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Selection indicator
                    IconButton(
                        onClick = onToggleSelection,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Select",
                            tint = if (isSelected) DiscoveryViolet else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = recommendation.category.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = DiscoveryViolet,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        
                        KeepScoreBadge(recommendation.keepScore)
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = AuraMidnight,
                        maxLines = 1
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "WHY AURA FOUND THIS:",
                        style = labelExtraSmall(),
                        color = AuraMutedSlate,
                        fontWeight = FontWeight.Black
                    )
                    
                    Text(
                        text = recommendation.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = AuraSlate
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Confidence: ${(recommendation.confidenceScore * 100).toInt()}%",
                        style = labelExtraSmall(),
                        color = AuraMutedSlate.copy(alpha = 0.6f)
                    )
                    
                    Text(
                        text = "${formatSize(recommendation.storageSize)} • ${item.mediaType}",
                        fontSize = 11.sp,
                        color = AuraMutedSlate.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onKeep,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp), // Pill shape
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                ) {
                    Text("KEEP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraMidnight)
                }
                
                Button(
                    onClick = onToggleSelection,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp), // Pill shape
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) DiscoveryViolet else DiscoveryViolet.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = if (isSelected) "SELECTED" else "DELETE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else DiscoveryViolet
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepScoreBadge(score: Float) {
    Surface(
        color = AuraSubtleSurface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "KEEP SCORE",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMutedSlate
            )
            Text(
                text = "${(score * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    score < 0.2f -> Color.Red
                    score < 0.4f -> Color(0xFFFF9800)
                    else -> AuraMidnight
                }
            )
        }
    }
}

@Composable
private fun labelExtraSmall() = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

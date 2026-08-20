package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.intelligence.TasteClusterEvidence
import com.example.ui.theme.*

/**
 * A visual evidence card displaying a Taste DNA cluster backed by a real local media item.
 */
@Composable
fun TasteClusterCard(
    evidence: TasteClusterEvidence,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = AuraSubtleSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Media Preview Focal Point (Reusing proven Library mechanism)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f),
                contentAlignment = Alignment.Center
            ) {
                AuraMediaThumbnail(
                    itemId = evidence.representativeMediaId ?: "",
                    mediaType = if (evidence.isVideo) "VIDEO" else "PHOTO",
                    imageUrl = evidence.representativeMediaThumbnailUrl ?: "",
                    uriPath = evidence.representativeMediaThumbnailUrl ?: "",
                    title = evidence.title,
                    modifier = Modifier.fillMaxSize(),
                    locationTag = "taste_cluster"
                )

                if (evidence.isVideo) {
                    // Subtle video/play indicator (Restoring original overlay design)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cluster Metadata
            Text(
                text = evidence.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = evidence.strengthLabel.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = DiscoveryViolet,
                    letterSpacing = 0.5.sp
                )
                
                // Visual Strength Indicator (Dots)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val filledDots = when (evidence.strengthLabel) {
                        "Very Strong" -> 4
                        "Strong" -> 3
                        "Moderate" -> 2
                        "Emerging" -> 1
                        else -> 0
                    }
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (i < filledDots) DiscoveryViolet else AuraSubtleBorder)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Semantic Explanation
            Text(
                text = evidence.description,
                fontSize = 11.sp,
                color = AuraSlate,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

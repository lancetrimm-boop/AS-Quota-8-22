package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet
import kotlin.math.cos
import kotlin.math.sin

/**
 * A radial radar chart to visualize the 24 dimensions of Aura's Taste DNA.
 */
@Composable
fun TasteRadarChart(
    dimensions: Map<String, Double>,
    modifier: Modifier = Modifier,
    radarColor: Color = DiscoveryViolet
) {
    if (dimensions.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Insufficient data to render profile.",
                fontSize = 12.sp,
                color = AuraMutedSlate
            )
        }
        return
    }

    val labels = dimensions.keys.toList()
    val values = dimensions.values.toList()
    val numPoints = labels.size

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .padding(32.dp), // Extra padding for labels
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2.2f

                // 1. Draw Grid (Concentric circles/polygons)
                val gridLevels = 5
                for (level in 1..gridLevels) {
                    val currentRadius = radius * (level.toFloat() / gridLevels)
                    val gridPath = Path()
                    for (i in 0 until numPoints) {
                        val angle = (Math.PI * 2 * i / numPoints) - (Math.PI / 2)
                        val x = center.x + cos(angle).toFloat() * currentRadius
                        val y = center.y + sin(angle).toFloat() * currentRadius
                        if (i == 0) gridPath.moveTo(x, y) else gridPath.lineTo(x, y)
                    }
                    gridPath.close()
                    drawPath(gridPath, AuraSubtleBorder, style = Stroke(width = 1.dp.toPx()))
                }

                // 2. Draw Spoke Lines
                for (i in 0 until numPoints) {
                    val angle = (Math.PI * 2 * i / numPoints) - (Math.PI / 2)
                    val x = center.x + cos(angle).toFloat() * radius
                    val y = center.y + sin(angle).toFloat() * radius
                    drawLine(AuraSubtleBorder.copy(alpha = 0.5f), center, Offset(x, y), strokeWidth = 1.dp.toPx())
                }

                // 3. Draw Data Polygon
                val dataPath = Path()
                for (i in 0 until numPoints) {
                    val angle = (Math.PI * 2 * i / numPoints) - (Math.PI / 2)
                    val value = values[i].toFloat().coerceIn(0f, 1f)
                    val currentRadius = radius * value
                    val x = center.x + cos(angle).toFloat() * currentRadius
                    val y = center.y + sin(angle).toFloat() * currentRadius
                    if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                }
                dataPath.close()
                
                val brush = Brush.radialGradient(
                    colors = listOf(
                        radarColor.copy(alpha = 0.6f),
                        com.example.ui.theme.DiscoveryMagenta.copy(alpha = 0.4f),
                        com.example.ui.theme.DiscoveryHotPink.copy(alpha = 0.2f)
                    ),
                    center = center,
                    radius = radius
                )

                drawPath(dataPath, brush)
                drawPath(dataPath, radarColor, style = Stroke(width = 2.dp.toPx()))
            }

            // 4. Draw Labels and Values (Overlay)
            // To prevent clutter with 24 dimensions, we only label major axes or those with significant drift
            labels.forEachIndexed { i, label ->
                val value = values[i]
                val isSignificant = Math.abs(value - 0.5) > 0.15
                // Label every 4th dimension always, or if it's significant
                if (i % 4 == 0 || isSignificant) {
                    val angle = (Math.PI * 2 * i / numPoints) - (Math.PI / 2)
                    val labelRadius = 1.25f // Distance from center
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val minDim = minOf(w, h)
                                val x = (w / 2) + cos(angle).toFloat() * (minDim / 2.2f) * labelRadius
                                val y = (h / 2) + sin(angle).toFloat() * (minDim / 2.2f) * labelRadius
                                translationX = x - (w / 2)
                                translationY = y - (h / 2)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = if (isSignificant) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSignificant) AuraMidnight else AuraMutedSlate,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp
                            )
                            Text(
                                text = "${(value * 100).toInt()}%",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = radarColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Taste DNA Spectrum",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AuraMidnight
        )
    }
}

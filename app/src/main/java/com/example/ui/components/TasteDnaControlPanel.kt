package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TasteDNA
import com.example.ui.theme.AuraBackground
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
import com.example.ui.theme.DiscoveryViolet

@Composable
fun TasteDnaControlPanel(
    tasteDNA: TasteDNA,
    preferenceProfile: TasteDNA.PreferenceProfile,
    onTasteDnaUpdate: (TasteDNA) -> Unit,
    onPreferenceProfileUpdate: (TasteDNA.PreferenceProfile) -> Unit,
    modifier: Modifier = Modifier,
    isEmbedded: Boolean = false,
    aiDescription: String? = null,
    showWeightsAtTop: Boolean = false,
    collapsibleSliders: Boolean = false,
    initialSlidersExpanded: Boolean = false,
    isDiscoverContext: Boolean = false
) {
    var slidersExpanded by rememberSaveable { mutableStateOf(initialSlidersExpanded) }

    val containerModifier = if (isEmbedded) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AuraSubtleSurface)
            .padding(16.dp)
    }

    Column(modifier = containerModifier) {
        if (isDiscoverContext) {
            // --- AUTHORITATIVE DISCOVER HIERARCHY ---
            // 1. AI Taste Learning
            AiLearningToggleSection(tasteDNA, isEmbedded, onTasteDnaUpdate)
            
            // 2. Aura Style Analysis (placed below AI learning)
            AiDescriptionSection(aiDescription)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 3. Discovery Intelligence (4 tuners)
            IntelligenceWeightsSection(preferenceProfile, onPreferenceProfileUpdate)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 4. Collapsible taste-slider menu (24 sliders)
            CollapsibleTasteSliders(
                tasteDNA = tasteDNA,
                expanded = slidersExpanded,
                onToggle = { slidersExpanded = !slidersExpanded },
                onTasteDnaUpdate = onTasteDnaUpdate
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            RadarSection(tasteDNA)
        } else if (showWeightsAtTop) {
            IntelligenceWeightsSection(preferenceProfile, onPreferenceProfileUpdate)
            Spacer(modifier = Modifier.height(24.dp))
            AiLearningToggleSection(tasteDNA, isEmbedded, onTasteDnaUpdate)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (collapsibleSliders) {
                CollapsibleTasteSliders(
                    tasteDNA = tasteDNA,
                    expanded = slidersExpanded,
                    onToggle = { slidersExpanded = !slidersExpanded },
                    onTasteDnaUpdate = onTasteDnaUpdate
                )
            } else {
                TasteSliders(tasteDNA, onTasteDnaUpdate)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            RadarSection(tasteDNA)
            AiDescriptionSection(aiDescription)
        } else {
            RadarSection(tasteDNA)
            Spacer(modifier = Modifier.height(24.dp))
            AiLearningToggleSection(tasteDNA, isEmbedded, onTasteDnaUpdate)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (collapsibleSliders) {
                CollapsibleTasteSliders(
                    tasteDNA = tasteDNA,
                    expanded = slidersExpanded,
                    onToggle = { slidersExpanded = !slidersExpanded },
                    onTasteDnaUpdate = onTasteDnaUpdate
                )
            } else {
                TasteSliders(tasteDNA, onTasteDnaUpdate)
            }
            
            AiDescriptionSection(aiDescription)
            Spacer(modifier = Modifier.height(28.dp))
            IntelligenceWeightsSection(preferenceProfile, onPreferenceProfileUpdate)
        }
    }
}

@Composable
private fun RadarSection(tasteDNA: TasteDNA) {
    // --- 1. VISUAL FOCAL POINT (Radar Spectrum) ---
    val dimensionsMap = remember(tasteDNA) {
        mapOf(
            "Vibrancy" to tasteDNA.effectiveVibrancy,
            "Contrast" to tasteDNA.effectiveContrast,
            "Sharpness" to tasteDNA.effectiveSharpness,
            "Symmetry" to tasteDNA.effectiveSymmetry,
            "Complexity" to tasteDNA.effectiveComplexity,
            "Naturalism" to tasteDNA.effectiveNaturalism,
            "Novelty" to tasteDNA.effectiveNovelty,
            "Lighting" to tasteDNA.effectiveLighting,
            "Color Temp" to tasteDNA.effectiveColorTemp,
            "Texture" to tasteDNA.effectiveTexture,
            "Motion" to tasteDNA.effectiveMotion,
            "Dynamic Range" to tasteDNA.effectiveDynamicRange,
            "Framing" to tasteDNA.effectiveFraming,
            "Depth" to tasteDNA.effectiveDepth,
            "Warmth" to tasteDNA.effectiveWarmth,
            "Saturation" to tasteDNA.effectiveSaturation,
            "Elegance" to tasteDNA.effectiveElegance,
            "Minimalism" to tasteDNA.effectiveMinimalism,
            "Grain" to tasteDNA.effectiveGrain,
            "Focus" to tasteDNA.effectiveFocus,
            "Density" to tasteDNA.effectiveDensity,
            "Rhythm" to tasteDNA.effectiveRhythm,
            "Mood" to tasteDNA.effectiveMood,
            "Harmony" to tasteDNA.effectiveHarmony
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        TasteRadarChart(
            dimensions = dimensionsMap,
            modifier = Modifier.size(240.dp)
        )
    }
}

@Composable
private fun AiLearningToggleSection(
    tasteDNA: TasteDNA,
    isEmbedded: Boolean,
    onTasteDnaUpdate: (TasteDNA) -> Unit
) {
    Surface(
        color = if (isEmbedded) AuraSubtleSurface.copy(alpha = 0.5f) else AuraSubtleSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Taste Learning",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraMidnight
                )
                Text(
                    text = "Allow Aura to adapt your profile based on your interactions.",
                    fontSize = 12.sp,
                    color = AuraSlate
                )
            }
            Switch(
                checked = tasteDNA.isFineTuningEnabled,
                onCheckedChange = { onTasteDnaUpdate(tasteDNA.copy(isFineTuningEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AuraCrispWhite,
                    checkedTrackColor = DiscoveryViolet,
                    uncheckedThumbColor = AuraMutedSlate,
                    uncheckedTrackColor = AuraSubtleBorder
                ),
                modifier = Modifier.scale(0.85f)
            )
        }
    }
}

@Composable
internal fun TasteSliders(
    tasteDNA: TasteDNA,
    onTasteDnaUpdate: (TasteDNA) -> Unit
) {
    Column {
        ControlSectionHeader("Aesthetic Character")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConsumerSlider("Vibrancy", tasteDNA.effectiveVibrancy, "Subtle", "Vibrant", 
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.vibrancy != tasteDNA.learnedVibrancy
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newVibrancy = it)) }
            
            ConsumerSlider("Saturation", tasteDNA.effectiveSaturation, "Muted", "Vivid",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.saturation != tasteDNA.learnedSaturation
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newSaturation = it)) }

            ConsumerSlider("Contrast", tasteDNA.effectiveContrast, "Soft", "Hard",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.contrast != tasteDNA.learnedContrast
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newContrast = it)) }

            ConsumerSlider("Sharpness", tasteDNA.effectiveSharpness, "Smooth", "Sharp",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.sharpness != tasteDNA.learnedSharpness
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newSharpness = it)) }

            ConsumerSlider("Lighting", tasteDNA.effectiveLighting, "Shadow", "Light",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.lighting != tasteDNA.learnedLighting
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newLighting = it)) }

            ConsumerSlider("Color Temperature", tasteDNA.effectiveColorTemp, "Cool", "Warm",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.colorTemperature != tasteDNA.learnedColorTemp
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newColorTemp = it)) }

            ConsumerSlider("Texture", tasteDNA.effectiveTexture, "Polished", "Textured",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.texture != tasteDNA.learnedTexture
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newTexture = it)) }

            ConsumerSlider("Grain", tasteDNA.effectiveGrain, "Clean", "Gritty",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.grain != tasteDNA.learnedGrain
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newGrain = it)) }
        }

        Spacer(modifier = Modifier.height(28.dp))

        ControlSectionHeader("Structure & Flow")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConsumerSlider("Symmetry", tasteDNA.effectiveSymmetry, "Asymmetric", "Symmetric",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.symmetry != tasteDNA.learnedSymmetry
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newSymmetry = it)) }

            ConsumerSlider("Complexity", tasteDNA.effectiveComplexity, "Minimal", "Intricate",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.complexity != tasteDNA.learnedComplexity
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newComplexity = it)) }

            ConsumerSlider("Depth", tasteDNA.effectiveDepth, "Flat", "Deep",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.depth != tasteDNA.learnedDepth
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newDepth = it)) }

            ConsumerSlider("Framing", tasteDNA.effectiveFraming, "Wide", "Tight",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.framing != tasteDNA.learnedFraming
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newFraming = it)) }

            ConsumerSlider("Motion", tasteDNA.effectiveMotion, "Still", "Dynamic",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.motion != tasteDNA.learnedMotion
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newMotion = it)) }

            ConsumerSlider("Dynamic Range", tasteDNA.effectiveDynamicRange, "Narrow", "Rich",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.dynamicRange != tasteDNA.learnedDynamicRange
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newDynamicRange = it)) }

            ConsumerSlider("Focus", tasteDNA.effectiveFocus, "Soft", "Sharp",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.focus != tasteDNA.learnedFocus
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newFocus = it)) }

            ConsumerSlider("Density", tasteDNA.effectiveDensity, "Sparse", "Dense",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.density != tasteDNA.learnedDensity
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newDensity = it)) }
        }

        Spacer(modifier = Modifier.height(28.dp))

        ControlSectionHeader("Style & Sentiment")
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ConsumerSlider("Naturalism", tasteDNA.effectiveNaturalism, "Stylized", "Organic",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.naturalism != tasteDNA.learnedNaturalism
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newNaturalism = it)) }

            ConsumerSlider("Novelty", tasteDNA.effectiveNovelty, "Familiar", "Unique",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.novelty != tasteDNA.learnedNovelty
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newNovelty = it)) }

            ConsumerSlider("Elegance", tasteDNA.effectiveElegance, "Raw", "Refined",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.elegance != tasteDNA.learnedElegance
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newElegance = it)) }

            ConsumerSlider("Minimalism", tasteDNA.effectiveMinimalism, "Maximalist", "Minimalist",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.minimalism != tasteDNA.learnedMinimalism
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newMinimalism = it)) }

            ConsumerSlider("Warmth", tasteDNA.effectiveWarmth, "Cool", "Warm",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.warmth != tasteDNA.learnedWarmth
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newWarmth = it)) }

            ConsumerSlider("Rhythm", tasteDNA.effectiveRhythm, "Steady", "Syncopated",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.rhythm != tasteDNA.learnedRhythm
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newRhythm = it)) }

            ConsumerSlider("Mood", tasteDNA.effectiveMood, "Relaxed", "Intense",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.mood != tasteDNA.learnedMood
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newMood = it)) }

            ConsumerSlider("Harmony", tasteDNA.effectiveHarmony, "Dissonant", "Harmonious",
                isAdjusted = tasteDNA.isFineTuningEnabled && tasteDNA.harmony != tasteDNA.learnedHarmony
            ) { onTasteDnaUpdate(tasteDNA.updateBaseline(newHarmony = it)) }
        }
    }
}

@Composable
internal fun CollapsibleTasteSliders(
    tasteDNA: TasteDNA,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTasteDnaUpdate: (TasteDNA) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tune your Tastes",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = AuraMidnight
            )
        }
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                TasteSliders(tasteDNA, onTasteDnaUpdate)
            }
        }
    }
}

@Composable
internal fun AiDescriptionSection(aiDescription: String?) {
    if (!aiDescription.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(32.dp))
        Surface(
            color = DiscoveryViolet.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, AuraSubtleBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = DiscoveryViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AURA STYLE ANALYSIS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = DiscoveryViolet,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = aiDescription,
                    fontSize = 13.sp,
                    color = AuraMidnight,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
internal fun IntelligenceWeightsSection(
    preferenceProfile: TasteDNA.PreferenceProfile,
    onPreferenceProfileUpdate: (TasteDNA.PreferenceProfile) -> Unit,
    showTitle: Boolean = true
) {
    if (showTitle) {
        ControlSectionHeader("Discovery Intelligence")
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConsumerSlider(
            label = "Content Similarity",
            value = preferenceProfile.contentSimilarityWeight,
            leftLabel = "Discovery",
            rightLabel = "Consistency"
        ) { onPreferenceProfileUpdate(preferenceProfile.copy(contentSimilarityWeight = it).normalize()) }

        ConsumerSlider(
            label = "Collaborative Signals",
            value = preferenceProfile.collaborativeWeight,
            leftLabel = "Unique",
            rightLabel = "Global"
        ) { onPreferenceProfileUpdate(preferenceProfile.copy(collaborativeWeight = it).normalize()) }

        ConsumerSlider(
            label = "Diversity Bonus",
            value = preferenceProfile.diversityWeight,
            leftLabel = "Focused",
            rightLabel = "Diverse"
        ) { onPreferenceProfileUpdate(preferenceProfile.copy(diversityWeight = it).normalize()) }

        ConsumerSlider(
            label = "Novelty Weight",
            value = preferenceProfile.noveltyWeight,
            leftLabel = "Familiar",
            rightLabel = "New"
        ) { onPreferenceProfileUpdate(preferenceProfile.copy(noveltyWeight = it).normalize()) }
    }
}

@Composable
private fun ControlSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = DiscoveryViolet,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun ConsumerSlider(
    label: String,
    value: Double,
    leftLabel: String? = null,
    rightLabel: String? = null,
    isAdjusted: Boolean = false,
    onValueChange: (Double) -> Unit
) {
    val displayValue = value.takeIf { !it.isNaN() } ?: 0.50
    val percentage = (displayValue * 100).toInt()
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AuraMidnight)
                if (isAdjusted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = DiscoveryViolet.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "ADAPTED", 
                            fontSize = 8.sp, 
                            fontWeight = FontWeight.Black, 
                            color = DiscoveryViolet, 
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "$percentage%", 
                fontSize = 13.sp, 
                fontWeight = FontWeight.Bold,
                color = if (isAdjusted) DiscoveryViolet else AuraMutedSlate
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(AuraSubtleBorder)
            )

            Slider(
                value = displayValue.toFloat(),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = if (isAdjusted) DiscoveryViolet else AuraCrispWhite,
                    activeTrackColor = DiscoveryViolet,
                    inactiveTrackColor = AuraSubtleBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (leftLabel != null && rightLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = leftLabel, fontSize = 9.sp, color = AuraMutedSlate, fontWeight = FontWeight.Medium)
                Text(text = rightLabel, fontSize = 9.sp, color = AuraMutedSlate, fontWeight = FontWeight.Medium)
            }
        }
    }
}

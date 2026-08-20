package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IntelligenceStats
import com.example.data.TasteDNA
import com.example.ui.theme.AuraBackground
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
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet

@Composable
fun AuraEngagementTunerCard(
    tasteDNA: TasteDNA,
    preferenceProfile: TasteDNA.PreferenceProfile,
    onTasteDnaUpdate: (TasteDNA) -> Unit,
    onPreferenceProfileUpdate: (TasteDNA.PreferenceProfile) -> Unit,
    modifier: Modifier = Modifier,
    aiDescription: String? = null,
    showWeightsAtTop: Boolean = false,
    collapsibleSliders: Boolean = false,
    initialSlidersExpanded: Boolean = false,
    isDiscoverContext: Boolean = false
) {
    val learningStatus = when {
        !tasteDNA.isFineTuningEnabled -> "Learning Paused"
        preferenceProfile.interactionsCount < 5 -> "Preferences Calibrating"
        else -> "Learning Active"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = AuraSubtleSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI LEARNING STATUS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = AuraMutedSlate,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = learningStatus,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tasteDNA.isFineTuningEnabled) DiscoveryViolet else AuraMidnight
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sub-component for actual controls (Refactored to be embeddable)
            TasteDnaControlPanel(
                tasteDNA = tasteDNA,
                preferenceProfile = preferenceProfile,
                onTasteDnaUpdate = onTasteDnaUpdate,
                onPreferenceProfileUpdate = onPreferenceProfileUpdate,
                modifier = Modifier.padding(0.dp),
                isEmbedded = true,
                aiDescription = aiDescription,
                showWeightsAtTop = showWeightsAtTop,
                collapsibleSliders = collapsibleSliders,
                initialSlidersExpanded = initialSlidersExpanded,
                isDiscoverContext = isDiscoverContext
            )
        }
    }
}

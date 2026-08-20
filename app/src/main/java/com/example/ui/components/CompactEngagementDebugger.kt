package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MediaRepository
import com.example.data.TasteDNA
import com.example.ui.theme.*

@Composable
fun CompactEngagementDebugger(
    repository: MediaRepository,
    modifier: Modifier = Modifier
) {
    val tasteDNA by repository.tasteDNA.collectAsStateWithLifecycle()
    val profile by repository.preferenceProfile.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(AuraSurface)
            .padding(16.dp)
    ) {
        // Compact Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = AuraPurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AURA ENGAGEMENT TUNER",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuraOnSurface
                )
            }
            
            Surface(
                color = AuraPurple.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "LIVE STATE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = AuraPurple,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs for different control groups
        var selectedTab by remember { mutableIntStateOf(0) }
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = AuraPurple,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AuraPurple
                )
            }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Engine Signals", modifier = Modifier.padding(vertical = 8.dp), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            when (selectedTab) {
                0 -> EngineSignalInfo(repository)
            }
        }
    }
}

// Removed duplicate TasteDnaControls and PreferenceProfileControls - migrated to TasteDnaControlPanel.kt

@Composable
private fun EngineSignalInfo(repository: MediaRepository) {
    val stats by repository.intelligenceStats.collectAsStateWithLifecycle()
    val diag by repository.pairwiseDiagnostics.collectAsStateWithLifecycle()
    val dna by repository.tasteDNA.collectAsStateWithLifecycle()
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignalRow("Fine-Tuning Active", if (dna.isFineTuningEnabled) "YES" else "NO (Locked to Baseline)")
        SignalRow("Personalization Accuracy", "${stats.personalizationScore}%")
        SignalRow("Pairwise Confidence", "${(diag.comparedCandidateCount.toFloat() / diag.top100CandidatePoolSize.coerceAtLeast(1) * 100).toInt()}%")
        SignalRow("Pool Freshness", "${(System.currentTimeMillis() - diag.poolRefreshTimestamp) / 1000}s ago")
        SignalRow("Last Pair Selection", diag.lastSelectionReason)
    }
}

@Composable
private fun SignalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = AuraOnSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
    }
}

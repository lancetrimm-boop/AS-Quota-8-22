package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.*
import com.example.data.intelligence.TasteClusterEvidence
import com.example.ui.components.*
import com.example.ui.theme.*
import android.widget.Toast
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.example.util.MediaThumbnailFetcher

data class TasteSpectrum(
    val labelLeft: String,
    val labelRight: String,
    val value: Double
)

private fun getTasteSpectrums(dna: TasteDNA): List<TasteSpectrum> {
    return listOf(
        TasteSpectrum("Cinematic", "Casual", (dna.effectiveFraming + dna.effectiveDepth + dna.effectiveElegance) / 3.0),
        TasteSpectrum("Calm", "Energetic", (dna.effectiveMood + dna.effectiveMotion + dna.effectiveRhythm) / 3.0),
        TasteSpectrum("Minimal", "Detailed", (dna.effectiveComplexity + dna.effectiveDensity + (1.0 - dna.effectiveMinimalism)) / 3.0),
        TasteSpectrum("Dark", "Bright", (dna.effectiveLighting + dna.effectiveDynamicRange + dna.effectiveVibrancy) / 3.0),
        TasteSpectrum("Realistic", "Stylized", ((1.0 - dna.effectiveNaturalism) + dna.effectiveElegance + dna.effectiveSaturation) / 3.0),
        TasteSpectrum("Familiar", "Experimental", (dna.effectiveNovelty + dna.effectiveExploration) / 2.0),
        TasteSpectrum("Atmospheric", "Clean", (dna.effectiveTexture + dna.effectiveGrain + dna.effectiveMood) / 3.0),
        TasteSpectrum("Still", "Dynamic", (dna.effectiveMotion + dna.effectiveRhythm) / 2.0)
    )
}

@Composable
private fun TasteSpectrumIndicator(spectrum: TasteSpectrum) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = spectrum.labelLeft.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = AuraMutedSlate.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
            Text(
                text = spectrum.labelRight.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = AuraMutedSlate.copy(alpha = 0.8f),
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp) // Breathe room for dot
                .height(2.dp)
                .clip(CircleShape)
                .background(AuraSubtleBorder)
        ) {
            // Value Indicator - subtle dot
            Box(
                modifier = Modifier
                    .fillMaxWidth(spectrum.value.toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .align(Alignment.CenterEnd)
                        .offset(x = 3.dp) // Center the dot on the end of the line
                        .clip(CircleShape)
                        .background(DiscoveryViolet)
                )
            }
        }
    }
}

@Composable
private fun VisualTasteSummary(
    tasteDNA: TasteDNA,
    aiDescription: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = AuraSubtleSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "AESTHETIC SPECTRUM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = DiscoveryViolet,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 8 Spectrums
                val spectrums = getTasteSpectrums(tasteDNA)
                spectrums.forEach { spectrum ->
                    TasteSpectrumIndicator(spectrum)
                }
                
                if (!aiDescription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = DiscoveryViolet,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AURA INTERPRETATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = DiscoveryViolet,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = aiDescription,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = AuraMidnight,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = AuraMutedSlate,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp, top = 12.dp)
    )
}

@Composable
private fun PrivacyConsentCard(
    consentState: com.example.data.contribution.ConsentState,
    onConsentChange: (com.example.data.contribution.ConsentState) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = AuraSubtleSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Global Recommendation Intelligence",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AuraMidnight
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (consentState) {
                            com.example.data.contribution.ConsentState.GRANTED -> "Status: Opted In (Active)"
                            com.example.data.contribution.ConsentState.REVOKED -> "Status: Opted Out (Queue Purged)"
                            else -> "Status: Disabled by Default"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (consentState == com.example.data.contribution.ConsentState.GRANTED) DiscoveryViolet else AuraMutedSlate
                    )
                }

                Switch(
                    checked = consentState == com.example.data.contribution.ConsentState.GRANTED,
                    onCheckedChange = { checked ->
                        val newState = if (checked) {
                            com.example.data.contribution.ConsentState.GRANTED
                        } else {
                            com.example.data.contribution.ConsentState.REVOKED
                        }
                        onConsentChange(newState)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AuraCrispWhite,
                        checkedTrackColor = DiscoveryViolet,
                        uncheckedThumbColor = AuraMutedSlate,
                        uncheckedTrackColor = AuraSubtleBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Participation is entirely optional. When enabled, Aura contributes anonymized mathematical preference signals to improve global models. Your personal intelligence stays 100% private and on-device.",
                fontSize = 11.sp,
                color = AuraMutedSlate,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun VisualTasteLearningCard() {
    Surface(
        color = DiscoveryViolet.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Aura is learning your visual taste.",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp),
            color = DiscoveryViolet,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AuraVersionInfo() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AURA MEDIA PLAYER",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = AuraMutedSlate,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Version 1.0.0 (consumerDebug)",
            fontSize = 10.sp,
            color = AuraMutedSlate.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun ProfileScreen(
    repository: com.example.data.MediaRepository,
    onNavigateToFavorites: () -> Unit,
    onNavigateToCleanup: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onLaunchAuraMoments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasteDNA by repository.tasteDNA.collectAsStateWithLifecycle()
    val preferenceProfile by repository.preferenceProfile.collectAsStateWithLifecycle()
    val discoveryPolicy by repository.discoveryPolicy.collectAsStateWithLifecycle()
    val consentState by repository.consentState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val dashboardViewModel: IntelligenceDashboardViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return IntelligenceDashboardViewModel(repository.intelligenceRepository!!) as T
            }
        }
    )
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val report = dashboardState.report

    val diagnosticsViewModel: PlaybackDiagnosticsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PlaybackDiagnosticsViewModel(repository.playbackErrorLogRepository!!) as T
            }
        }
    )
    val errorLogs by diagnosticsViewModel.errorLogs.collectAsStateWithLifecycle()
    
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var selectedCluster by remember { mutableStateOf<TasteClusterEvidence?>(null) }
    var slidersExpanded by remember { mutableStateOf(false) }
    var advancedDiscoveryExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraCrispWhite)
        ) {
            AuraSectionHeader(
                title = "Profile",
                subtitle = "Your intelligence profile and Taste DNA"
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isLandscape = this.maxWidth > this.maxHeight
                
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Left Column: Collections & Your Visual Taste
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 1. COLLECTIONS
                            ProfileSectionTitle("Collections")
                            Text(
                                text = "What you have saved and organized",
                                fontSize = 13.sp,
                                color = AuraMutedSlate,
                                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp)),
                                color = AuraSubtleSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                            ) {
                                Column {
                                    SettingsClickRow(
                                        icon = Icons.Default.Favorite,
                                        title = "Favorites",
                                        subtitle = "Your explicit saved media",
                                        onClick = { onNavigateToFavorites() }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))
                                    SettingsClickRow(
                                        icon = Icons.Default.AutoAwesomeMotion,
                                        title = "Aura Moments",
                                        subtitle = "Intelligent curated slideshows",
                                        onClick = { onLaunchAuraMoments() }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))
                                    SettingsClickRow(
                                        icon = Icons.Default.Delete,
                                        title = "Smart Cleanup",
                                        subtitle = "Intelligent storage management",
                                        onClick = { onNavigateToCleanup() }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            // 2. SIGNATURE STYLES
                            ProfileSectionTitle("Signature Styles")
                            Text(
                                text = "Your intelligence profile and visual style clusters",
                                fontSize = 13.sp,
                                color = AuraMutedSlate,
                                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                            )
                            
                            VisualTasteSummary(
                                tasteDNA = tasteDNA,
                                aiDescription = report?.tasteProfile?.description
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                            
                            // Tune My Taste (Detailed 24-dimension editor)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp)),
                                color = AuraSubtleSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                            ) {
                                Column {
                                    SettingsClickRow(
                                        icon = Icons.Default.Tune,
                                        title = "Tune My Taste",
                                        subtitle = if (slidersExpanded) "Hide detailed editor" else "Edit detailed Taste DNA dimensions",
                                        onClick = { slidersExpanded = !slidersExpanded }
                                    )
                                    
                                    AnimatedVisibility(
                                        visible = slidersExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                            TasteSliders(
                                                tasteDNA = tasteDNA,
                                                onTasteDnaUpdate = { repository.updateTasteDNA(it, isUserGenerated = true, evidenceCategory = "Profile Manual Tuning") }
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(48.dp))
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        // Right Column: Discovery & Administrative
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 3. DISCOVERY
                            ProfileSectionTitle("Discovery")
                            Text(
                                text = "How AURA applies your visual taste",
                                fontSize = 13.sp,
                                color = AuraMutedSlate,
                                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                            )
                            DiscoveryPolicyControl(
                                policy = discoveryPolicy,
                                onPolicyChange = { repository.updateDiscoveryPolicy(it) }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp)),
                                color = AuraSubtleSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                            ) {
                                Column {
                                    Column(modifier = Modifier.padding(24.dp)) {
                                        Text(
                                            text = "Discovery Intelligence",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AuraMidnight
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "AURA automatically balances your preferences, engagement, and exploration.",
                                            fontSize = 12.sp,
                                            color = AuraMutedSlate,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))
                                    
                                    SettingsClickRow(
                                        icon = Icons.Default.SettingsSuggest,
                                        title = "Advanced Controls",
                                        subtitle = if (advancedDiscoveryExpanded) "Hide discovery weights" else "Fine-tune recommendation signals",
                                        onClick = { advancedDiscoveryExpanded = !advancedDiscoveryExpanded }
                                    )
                                    
                                    AnimatedVisibility(
                                        visible = advancedDiscoveryExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                            IntelligenceWeightsSection(
                                                preferenceProfile = preferenceProfile,
                                                onPreferenceProfileUpdate = { repository.updatePreferenceProfile(it) },
                                                showTitle = false
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(48.dp))

                            // 4. ADMINISTRATIVE & PRIVACY
                            ProfileSectionTitle("Administrative & Privacy")
                            Text(
                                text = "Manage app settings and privacy",
                                fontSize = 13.sp,
                                color = AuraMutedSlate,
                                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                            )
                            PrivacyConsentCard(consentState) { repository.updateConsentState(it) }

                            Spacer(modifier = Modifier.height(24.dp))

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp)),
                                color = AuraSubtleSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                            ) {
                                Column {
                                    SettingsClickRow(
                                        icon = Icons.Outlined.Feedback,
                                        title = "Customer Feedback",
                                        subtitle = "Share your thoughts about Aura experience",
                                        onClick = { showFeedbackDialog = true }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))
                                    SettingsClickRow(
                                        icon = Icons.Default.PrivacyTip,
                                        title = "Privacy Policy",
                                        subtitle = "How Aura protects your data",
                                        onClick = { onNavigateToPrivacyPolicy() }
                                    )
                                }
                            }
                            
                            AuraVersionInfo()
                            
                            Spacer(modifier = Modifier.height(24.dp))

                            // 5. PLAYBACK DIAGNOSTICS
                            ProfileSectionTitle("Playback Diagnostics")
                            Text(
                                text = "Review playback failures and technical diagnostics stored on this device.",
                                fontSize = 13.sp,
                                color = AuraMutedSlate,
                                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp)),
                                color = AuraSubtleSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                            ) {
                                val totalErrors = errorLogs.sumOf { it.occurrenceCount }
                                Column {
                                    SettingsClickRow(
                                        icon = Icons.Default.BugReport,
                                        title = "View Playback Diagnostics",
                                        subtitle = if (totalErrors == 0) "No playback errors recorded" else "$totalErrors playback errors recorded",
                                        onClick = { onNavigateToDiagnostics() }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                } else {
                    // Portrait Layout (New Hierarchy)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // 1. COLLECTIONS
                        ProfileSectionTitle("Collections")
                        Text(
                            text = "What you have saved and organized",
                            fontSize = 13.sp,
                            color = AuraMutedSlate,
                            modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp)),
                            color = AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                        ) {
                            Column {
                                SettingsClickRow(
                                    icon = Icons.Default.Favorite,
                                    title = "Favorites",
                                    subtitle = "Your explicit saved media",
                                    onClick = { onNavigateToFavorites() }
                                )
                                
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))
                                
                                SettingsClickRow(
                                    icon = Icons.Default.AutoAwesomeMotion,
                                    title = "Aura Moments",
                                    subtitle = "Intelligent curated slideshows",
                                    onClick = { onLaunchAuraMoments() }
                                )

                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))

                                SettingsClickRow(
                                    icon = Icons.Default.Delete,
                                    title = "Smart Cleanup",
                                    subtitle = "Intelligent storage management",
                                    onClick = { onNavigateToCleanup() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // 2. SIGNATURE STYLES
                        ProfileSectionTitle("Signature Styles")
                        Text(
                            text = "Your intelligence profile and visual style clusters",
                            fontSize = 13.sp,
                            color = AuraMutedSlate,
                            modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                        )
                        
                        VisualTasteSummary(
                            tasteDNA = tasteDNA,
                            aiDescription = report?.tasteProfile?.description
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp)),
                            color = AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                        ) {
                            Column {
                                SettingsClickRow(
                                    icon = Icons.Default.Tune,
                                    title = "Tune My Taste",
                                    subtitle = if (slidersExpanded) "Hide detailed editor" else "Edit detailed Taste DNA dimensions",
                                    onClick = { slidersExpanded = !slidersExpanded }
                                )
                                
                                AnimatedVisibility(
                                    visible = slidersExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                        TasteSliders(
                                            tasteDNA = tasteDNA,
                                            onTasteDnaUpdate = { repository.updateTasteDNA(it, isUserGenerated = true, evidenceCategory = "Profile Manual Tuning") }
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // 3. DISCOVERY
                        ProfileSectionTitle("Discovery")
                        Text(
                            text = "How AURA applies your visual taste",
                            fontSize = 13.sp,
                            color = AuraMutedSlate,
                            modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                        )
                        
                        // Global Discovery Strategy
                        DiscoveryPolicyControl(
                            policy = discoveryPolicy,
                            onPolicyChange = { repository.updateDiscoveryPolicy(it) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Discovery Intelligence (Weights) with Advanced Controls
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp)),
                            color = AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                        ) {
                            Column {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        text = "Discovery Intelligence",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AuraMidnight
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "AURA automatically balances your preferences, engagement, and exploration.",
                                        fontSize = 12.sp,
                                        color = AuraMutedSlate,
                                        lineHeight = 18.sp
                                    )
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))
                                
                                SettingsClickRow(
                                    icon = Icons.Default.SettingsSuggest,
                                    title = "Advanced Controls",
                                    subtitle = if (advancedDiscoveryExpanded) "Hide discovery weights" else "Fine-tune recommendation signals",
                                    onClick = { advancedDiscoveryExpanded = !advancedDiscoveryExpanded }
                                )
                                
                                AnimatedVisibility(
                                    visible = advancedDiscoveryExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                        IntelligenceWeightsSection(
                                            preferenceProfile = preferenceProfile,
                                            onPreferenceProfileUpdate = { repository.updatePreferenceProfile(it) },
                                            showTitle = false
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // 4. ADMINISTRATIVE & PRIVACY
                        ProfileSectionTitle("Administrative & Privacy")
                        Text(
                            text = "Manage app settings and privacy",
                            fontSize = 13.sp,
                            color = AuraMutedSlate,
                            modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                        )
                        PrivacyConsentCard(consentState) { repository.updateConsentState(it) }

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp)),
                            color = AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                        ) {
                            Column {
                                SettingsClickRow(
                                    icon = Icons.Outlined.Feedback,
                                    title = "Customer Feedback",
                                    subtitle = "Share your thoughts about Aura experience",
                                    onClick = { showFeedbackDialog = true }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = AuraSubtleBorder.copy(alpha = 0.5f))
                                SettingsClickRow(
                                    icon = Icons.Default.PrivacyTip,
                                    title = "Privacy Policy",
                                    subtitle = "How Aura protects your data",
                                    onClick = { onNavigateToPrivacyPolicy() }
                                )
                            }
                        }

                        AuraVersionInfo()

                        Spacer(modifier = Modifier.height(24.dp))

                        // 5. PLAYBACK DIAGNOSTICS
                        ProfileSectionTitle("Playback Diagnostics")
                        Text(
                            text = "Review playback failures and technical diagnostics stored on this device.",
                            fontSize = 13.sp,
                            color = AuraMutedSlate,
                            modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp)),
                            color = AuraSubtleSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
                        ) {
                            val totalErrors = errorLogs.sumOf { it.occurrenceCount }
                            Column {
                                SettingsClickRow(
                                    icon = Icons.Default.BugReport,
                                    title = "View Playback Diagnostics",
                                    subtitle = if (totalErrors == 0) "No playback errors recorded" else "$totalErrors playback errors recorded",
                                    onClick = { onNavigateToDiagnostics() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }

        if (showFeedbackDialog) {
            var feedbackText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showFeedbackDialog = false },
                title = { Text("Customer Feedback", color = AuraMidnight, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "How can we improve your Aura experience?",
                            fontSize = 13.sp,
                            color = AuraMutedSlate,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            placeholder = { Text("Enter your feedback here...", fontSize = 14.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DiscoveryViolet,
                                unfocusedBorderColor = AuraSubtleBorder,
                                cursorColor = DiscoveryViolet
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (feedbackText.isNotBlank()) {
                                Toast.makeText(context, "Thank you for your feedback.", Toast.LENGTH_SHORT).show()
                                showFeedbackDialog = false
                                feedbackText = ""
                            }
                        }
                    ) {
                        Text("Submit", color = DiscoveryViolet, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFeedbackDialog = false }) {
                        Text("Cancel", color = AuraMutedSlate)
                    }
                },
                containerColor = AuraCrispWhite,
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Task 1: Visual Taste Detail Overlay (Phase 1 Integrity)
        if (selectedCluster != null) {
            TasteClusterDetailView(
                evidence = selectedCluster!!,
                onDismiss = { selectedCluster = null }
            )
        }
    }
}

@Composable
internal fun TasteClusterDetailView(
    evidence: TasteClusterEvidence,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(evidence.representativeMediaId, evidence.representativeMediaThumbnailUrl) { 
        mutableStateOf<Bitmap?>(null) 
    }

    LaunchedEffect(evidence.representativeMediaId, evidence.representativeMediaThumbnailUrl) {
        val targetUri = evidence.representativeMediaThumbnailUrl
        if (evidence.isVideo && !targetUri.isNullOrEmpty()) {
            thumbnailBitmap = MediaThumbnailFetcher.getThumbnail(context, targetUri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Visual Taste Insight",
                    style = MaterialTheme.typography.labelSmall,
                    color = DiscoveryViolet,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = evidence.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = AuraMidnight
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large Representative Media Example
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AuraSubtleSurface),
                    contentAlignment = Alignment.Center
                ) {
                    val imageModel = evidence.representativeMediaThumbnailUrl
                    if (imageModel != null) {
                        if (thumbnailBitmap != null) {
                            Image(
                                bitmap = thumbnailBitmap!!.asImageBitmap(),
                                contentDescription = "Visual evidence for ${evidence.title}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Visual evidence for ${evidence.title}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        
                        if (evidence.isVideo) {
                            VideoTilePreview(
                                itemId = evidence.representativeMediaId ?: "",
                                videoUri = imageModel,
                                imageUrl = imageModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Description & Analysis
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AURA ANALYSIS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = AuraMutedSlate,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = evidence.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AuraMidnight,
                        lineHeight = 22.sp
                    )
                }

                // Metadata: Strength & Contributing Traits
                Surface(
                    color = DiscoveryViolet.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AFFINITY STRENGTH:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AuraMutedSlate
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = evidence.strengthLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = DiscoveryViolet
                            )
                        }

                        if (evidence.contributingTraits.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "CONTRIBUTING TRAITS:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AuraMutedSlate
                                )
                                Text(
                                    text = evidence.contributingTraits.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AuraSlate
                                )
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
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = AuraCrispWhite,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                color = DiscoveryViolet.copy(alpha = 0.08f),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = DiscoveryViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuraMidnight
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = AuraMutedSlate,
                    lineHeight = 16.sp
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = AuraSubtleBorder,
            modifier = Modifier.size(20.dp)
        )
    }
}

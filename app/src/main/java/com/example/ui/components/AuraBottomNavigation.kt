package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.BuildConfig
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet

sealed class NavDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    object LIBRARY : NavDestination("library", "Library", Icons.Default.PhotoLibrary, Icons.Outlined.PhotoLibrary, "nav_library")
    object DISCOVER : NavDestination("discover", "Discover", Icons.Default.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_discover")
    object COMPARE : NavDestination("compare", "Compare", Icons.AutoMirrored.Filled.CompareArrows, Icons.AutoMirrored.Outlined.CompareArrows, "nav_compare")
    object COLLECTIONS : NavDestination("collections", "Collections", Icons.Default.CollectionsBookmark, Icons.Outlined.CollectionsBookmark, "nav_collections")
    object PROFILE : NavDestination("profile", "Profile", Icons.Default.Person, Icons.Outlined.Person, "nav_profile")
    object INTELLIGENCE : NavDestination("intelligence", "Intelligence", Icons.AutoMirrored.Filled.Rule, Icons.AutoMirrored.Outlined.Rule, "nav_intelligence")
    object FAVORITES : NavDestination("favorites", "Favorites", Icons.Default.Favorite, Icons.Outlined.FavoriteBorder, "nav_favorites")
    object CLEANUP_DASHBOARD : NavDestination("cleanup_dashboard", "Cleanup", Icons.Default.DeleteSweep, Icons.Outlined.DeleteSweep, "nav_cleanup_dashboard")
    object CLEANUP_REVIEW : NavDestination("cleanup_review", "Review", Icons.Default.Delete, Icons.Outlined.Delete, "nav_cleanup_review")
    object PRIVACY_POLICY : NavDestination("privacy_policy", "Privacy Policy", Icons.Default.PrivacyTip, Icons.Outlined.PrivacyTip, "nav_privacy_policy")
    object PLAYBACK_DIAGNOSTICS : NavDestination("playback_diagnostics", "Playback Diagnostics", Icons.Default.BugReport, Icons.Outlined.BugReport, "nav_playback_diagnostics")
}

@Composable
fun AuraBottomNavigation(
    currentRoute: String,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(AuraCrispWhite)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val isLandscape = this.maxWidth > this.maxHeight
        val navHeight = if (isLandscape) 48.dp else 64.dp
        
        Column {
            // Top Border
            HorizontalDivider(color = AuraSubtleBorder, thickness = 1.dp)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navHeight)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val primaryDestinations = if (BuildConfig.ENABLE_DEVELOPER_TOOLS) {
                    listOf(
                        NavDestination.LIBRARY,
                        NavDestination.DISCOVER,
                        NavDestination.COMPARE,
                        NavDestination.COLLECTIONS,
                        NavDestination.PROFILE,
                        NavDestination.INTELLIGENCE
                    )
                } else {
                    listOf(
                        NavDestination.LIBRARY,
                        NavDestination.DISCOVER,
                        NavDestination.COMPARE,
                        NavDestination.COLLECTIONS,
                        NavDestination.PROFILE
                    )
                }

                primaryDestinations.forEach { destination ->
                    val isSelected = currentRoute == destination.route
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                onClick = { 
                                    if (currentRoute != destination.route) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigate(destination) 
                                    }
                                },
                                indication = null,
                                interactionSource = null
                            )
                            .testTag(destination.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title,
                                tint = if (isSelected) DiscoveryViolet else AuraMutedSlate,
                                modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                            )
                            if (!isLandscape) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = destination.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AuraMidnight else AuraMutedSlate
                                )
                            }
                            
                            // Active Indicator (Subtle Gradient Dot)
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(if (isLandscape) 2.dp else 4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(DiscoveryGradient)
                                )
                            } else {
                                if (!isLandscape) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

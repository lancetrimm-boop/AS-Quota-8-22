package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// --- AUTHORITATIVE NEUTRAL SYSTEM ---
val AuraMidnight = Color(0xFF0F1117)
val AuraSlate = Color(0xFF374151)
val AuraMutedSlate = Color(0xFF6B7280)
val AuraCrispWhite = Color(0xFFFFFFFF)
val AuraSubtleSurface = Color(0xFFF9FAFB)
val AuraSubtleBorder = Color(0xFFE5E7EB)

// --- DISCOVERY GRADIENT COMPONENTS ---
val DiscoveryViolet = Color(0xFF8B5CF6)
val DiscoveryMagenta = Color(0xFFD946EF)
val DiscoveryHotPink = Color(0xFFEC4899)

// Official 3-color Discovery Gradient
val DiscoveryGradient = Brush.horizontalGradient(
    colors = listOf(DiscoveryViolet, DiscoveryMagenta, DiscoveryHotPink)
)

val DiscoveryGradientVertical = Brush.verticalGradient(
    colors = listOf(DiscoveryViolet, DiscoveryMagenta, DiscoveryHotPink)
)

// --- LEGACY COMPATIBILITY TOKENS ---
val AuraPurple = DiscoveryViolet
val AuraPurpleLight = DiscoveryViolet.copy(alpha = 0.7f)
val AuraPurpleDark = DiscoveryViolet 
val AuraPurpleContainer = Color(0xFFF3E8FF)

val AuraMagenta = DiscoveryMagenta
val AuraMagentaDark = DiscoveryMagenta

val AuraBrandGradient = DiscoveryGradient
val AuraBrandGradientVertical = DiscoveryGradientVertical


val AuraBackground = AuraCrispWhite
val AuraSurface = AuraSubtleSurface
val AuraSurfaceVariant = Color(0xFFF3F4F6) // To be reviewed
val AuraBorder = AuraSubtleBorder

val AuraOnSurface = AuraMidnight
val AuraOnSurfaceVariant = AuraMutedSlate
val AuraOutline = AuraMutedSlate

// Functional Colors
val AuraStarGold = Color(0xFFF59E0B)
val AuraSuccess = Color(0xFF10B981)

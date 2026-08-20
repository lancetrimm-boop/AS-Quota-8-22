package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Aura Visual Redesign — Phase 1: Theme Foundation
 * Establishes the authoritative Material 3 color mapping based on the 
 * white/light neutral canvas direction.
 */
private val LightColorScheme = lightColorScheme(
    primary = DiscoveryViolet,
    onPrimary = AuraCrispWhite,
    primaryContainer = AuraPurpleContainer, // Legacy, to be reviewed
    onPrimaryContainer = DiscoveryViolet,
    
    secondary = DiscoveryMagenta,
    onSecondary = AuraCrispWhite,
    secondaryContainer = Color(0xFFFDE8FF), // Legacy, to be reviewed
    onSecondaryContainer = DiscoveryMagenta,
    
    tertiary = DiscoveryHotPink,
    onTertiary = AuraCrispWhite,

    background = AuraCrispWhite,      // 75% white canvas
    onBackground = AuraMidnight,      // High contrast typography
    
    surface = AuraSubtleSurface,      // F9FAFB
    onSurface = AuraMidnight,
    
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = AuraMutedSlate,
    
    outline = AuraSubtleBorder,        // E5E7EB
    outlineVariant = Color(0xFFF3F4F6)
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Redesign specifies a light/white canvas dominant experience.
    // For Phase 1, we force LightColorScheme to establish the foundation.
    val colorScheme = LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

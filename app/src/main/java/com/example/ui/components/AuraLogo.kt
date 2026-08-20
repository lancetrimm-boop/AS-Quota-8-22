package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.R
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.DiscoveryGradient

/**
 * Authoritative Brand Name.
 * "Aura"
 */
@Composable
fun AuraBrandName(
    modifier: Modifier = Modifier,
    fontSize: Float = 18f,
    letterSpacing: Float = -0.5f,
    useGradient: Boolean = false
) {
    val baseStyle = MaterialTheme.typography.headlineSmall
    val style = if (useGradient) {
        baseStyle.copy(
            brush = DiscoveryGradient,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            letterSpacing = letterSpacing.sp
        )
    } else {
        baseStyle.copy(
            color = AuraMidnight,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            letterSpacing = letterSpacing.sp
        )
    }

    Text(
        text = stringResource(id = R.string.brand_name_full),
        style = style,
        modifier = modifier
    )
}

/**
 * Aura Unified Visual Identity — Mark Only.
 * Geometric A + centered Aura gradient circle.
 */
@Composable
fun AuraLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.aura_logo_mark),
            contentDescription = "Aura Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Compatibility alias for AuraLogoMark.
 */
@Composable
fun AuraLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    AuraLogoMark(modifier, size)
}

/**
 * Full Aura Branding Lockup.
 * "Aura" + "Media Player"
 */
@Composable
fun AuraFullLockup(
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
    fontSize: Float = 24f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        AuraLogoMark(size = iconSize)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.brand_name_full),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            letterSpacing = 0.sp
        )
        Text(
            text = stringResource(id = R.string.brand_description),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontSize = (fontSize * 0.6f).sp,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Horizontal Aura Branding Wordmark.
 */
@Composable
fun AuraWordmark(
    modifier: Modifier = Modifier,
    fontSize: Float = 22f
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        AuraLogoMark(size = (fontSize * 1.2f).dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(id = R.string.brand_name_full),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            letterSpacing = 0.sp
        )
    }
}

/**
 * Full Product Category Lockup.
 */
@Composable
fun AuraProductLockup(
    modifier: Modifier = Modifier,
    fontSize: Float = 22f
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        AuraLogoMark(size = (fontSize * 1.2f).dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(id = R.string.brand_name_full),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp,
                letterSpacing = 0.sp
            )
            Text(
                text = stringResource(id = R.string.brand_description),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                fontSize = (fontSize * 0.5f).sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

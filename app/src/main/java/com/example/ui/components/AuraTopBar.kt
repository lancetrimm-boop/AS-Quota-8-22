package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraSubtleBorder

@Composable
fun AuraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuraCrispWhite)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(12.dp))
            } else if (showLogo) {
                AuraLogoIcon(size = 28.dp)
                Spacer(modifier = Modifier.width(12.dp))
            }

            Text(
                text = if (showLogo) title else title.uppercase(),
                color = AuraMidnight,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = if (showLogo) 0.sp else 1.sp,
                modifier = Modifier.weight(1f)
            )

            if (actions != null) {
                actions()
            } else {
                IconButton(onClick = { /* Default search action */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = AuraMidnight
                    )
                }
            }
        }
        // Authoritative Bottom Border
        HorizontalDivider(color = AuraSubtleBorder, thickness = 1.dp)
    }
}

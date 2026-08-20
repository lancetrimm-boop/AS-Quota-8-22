package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DiscoveryMode
import com.example.data.DiscoveryPolicy
import com.example.ui.theme.AuraBorder
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.AuraPurpleContainer
import com.example.ui.theme.AuraSurface

@Composable
fun DiscoveryPolicyControl(
    policy: DiscoveryPolicy,
    onPolicyChange: (DiscoveryPolicy) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GLOBAL DISCOVERY STRATEGY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AuraOnSurfaceVariant,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AuraPurpleContainer.copy(alpha = 0.3f))
                    .border(1.dp, AuraBorder, RoundedCornerShape(24.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiscoveryMode.entries.forEach { mode ->
                    val isSelected = policy.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSelected) AuraPurple else Color.Transparent)
                            .clickable { onPolicyChange(policy.copy(mode = mode)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else AuraOnSurface
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = when(policy.mode) {
                    DiscoveryMode.PERSONALIZED -> "Show me more of what Aura already knows I like."
                    DiscoveryMode.BALANCED -> "Mix familiar favorites with meaningful new discoveries."
                    DiscoveryMode.EXPLORATORY -> "Help Aura learn what else I might like."
                },
                fontSize = 13.sp,
                color = AuraOnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TasteReveal
import com.example.ui.components.AuraButton
import com.example.ui.components.AuraWordmark
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraOnSurfaceVariant
import com.example.ui.theme.AuraPurple
import kotlinx.coroutines.delay

@Composable
fun TasteRevealScreen(
    reveal: TasteReveal,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stage by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        delay(500)
        stage = 1
        delay(1000)
        stage = 2
        delay(1000)
        stage = 3
    }

    val alpha1 by animateFloatAsState(if (stage >= 1) 1f else 0f, tween(1000), label = "a1")
    val alpha2 by animateFloatAsState(if (stage >= 2) 1f else 0f, tween(1000), label = "a2")
    val alpha3 by animateFloatAsState(if (stage >= 3) 1f else 0f, tween(1000), label = "a3")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuraWordmark(fontSize = 32f)
        
        Spacer(modifier = Modifier.height(48.dp))

        // Stage 1: Persona
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha1)
        ) {
            Text(
                text = "YOUR AURA",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = AuraPurple,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reveal.persona,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = AuraOnSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Stage 2: Traits
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha2)
        ) {
            Text(
                text = "You seem drawn to:",
                fontSize = 16.sp,
                color = AuraOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            reveal.primaryTraits.forEach { trait ->
                Text(
                    text = trait,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuraOnSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Stage 3: Style & Action
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha3)
        ) {
            Text(
                text = "YOUR DISCOVERY STYLE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = AuraPurple,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reveal.discoveryStyle,
                fontSize = 15.sp,
                color = AuraOnSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            AuraButton(
                text = "LET'S SEE IF I'M RIGHT",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Aura is ${(reveal.confidence * 100).toInt()}% confident in this insight.",
                fontSize = 11.sp,
                color = AuraOnSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

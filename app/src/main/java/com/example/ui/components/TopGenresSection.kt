package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopGenresSection(
    genres: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "TOP GENRES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = DiscoveryViolet,
            letterSpacing = 1.2.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { (genre, percentage) ->
                GenreTag(name = genre, percentage = percentage)
            }
        }
    }
}

@Composable
private fun GenreTag(name: String, percentage: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AuraSubtleSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AuraMidnight
            )
            Text(
                text = "$percentage%",
                fontSize = 12.sp,
                color = AuraSlate,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

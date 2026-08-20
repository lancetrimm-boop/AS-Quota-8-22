package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AuraBorder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.activity.compose.BackHandler
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MediaItem
import com.example.ui.components.AuraSquareMediaTile
import com.example.ui.components.AuraTopBar
import com.example.ui.components.AuraLogoIcon
import com.example.ui.theme.AuraCrispWhite
import com.example.ui.theme.AuraMidnight
import com.example.ui.theme.AuraMutedSlate
import com.example.ui.theme.AuraSubtleBorder
import com.example.ui.theme.DiscoveryGradient
import com.example.ui.theme.DiscoveryViolet

@Composable
fun FavoritesScreen(
    mediaItems: List<MediaItem>,
    onMediaSelect: (MediaItem) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLike: ((String) -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    var selectedFilter by remember { mutableStateOf("ALL") }
    val initialFavoriteIds = remember {
        mediaItems
            .filter { it.isFavorite }
            .map { it.id }
            .toSet()
    }
    val sessionMediaItems = mediaItems.filter {
        it.id in initialFavoriteIds
    }
    val favorites = sessionMediaItems.filter {
        when (selectedFilter) {
            "PHOTO" -> it.mediaType.equals("PHOTO", ignoreCase = true) || it.mediaType.equals("Image", ignoreCase = true)
            "VIDEO" -> it.mediaType.equals("VIDEO", ignoreCase = true) || it.mediaType.equals("Movie", ignoreCase = true)
            else -> true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraCrispWhite)
    ) {
        AuraTopBar(
            title = "Favorites",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AuraMidnight
                    )
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All", "PHOTO" to "Photo", "VIDEO" to "Video").forEach { (code, label) ->
                val isSelected = selectedFilter == code
                Surface(
                    onClick = { selectedFilter = code },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.height(36.dp),
                    color = if (isSelected) DiscoveryViolet.copy(alpha = 0.15f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        brush = if (isSelected) DiscoveryGradient else androidx.compose.ui.graphics.SolidColor(AuraMutedSlate.copy(alpha = 0.5f))
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) DiscoveryViolet else AuraMutedSlate
                        )
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AuraLogoIcon(size = 64.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Favorites Yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AuraMidnight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the heart icon on any media item to add it to your personal favorites collection.",
                        fontSize = 13.sp,
                        color = AuraMutedSlate,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(favorites, key = { it.id }) { item ->
                    AuraSquareMediaTile(
                        item = item,
                        onClick = { onMediaSelect(item) },
                        onLike = { onLike?.invoke(item.id) },
                        onFavoriteToggle = { onFavoriteToggle(item.id) }
                    )
                }
            }
        }
    }
}

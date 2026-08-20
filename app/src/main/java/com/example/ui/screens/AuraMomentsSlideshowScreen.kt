package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.data.MomentsMode
import com.example.ui.theme.AuraBackground
import com.example.ui.theme.AuraOnSurface
import com.example.ui.theme.AuraPurple
import kotlinx.coroutines.delay

@Composable
fun AuraMomentsSlideshowScreen(
    items: List<MediaItem>,
    mode: MomentsMode,
    repository: MediaRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AuraBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "No media available for slideshow", color = AuraOnSurface)
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = AuraOnSurface)
                }
            }
        }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var slideProgress by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }

    val currentItem = items[currentIndex.coerceIn(0, items.size - 1)]
    
    // Auto-advance logic for photos
    LaunchedEffect(currentIndex, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        
        slideProgress = 0f
        val totalSteps = 40
        val stepDelayMs = 100L // 4 seconds total per slide
        for (i in 1..totalSteps) {
            delay(stepDelayMs)
            if (!isPlaying) break
            slideProgress = i.toFloat() / totalSteps.toFloat()
        }
        
        if (isPlaying) {
            // Auto advance to next slide
            if (currentIndex < items.size - 1) {
                currentIndex++
            } else {
                currentIndex = 0 // loop
            }
        }
    }

    // Logging engagement safely
    fun logInteraction(type: String) {
        repository.recordMicroMoment(currentItem.id, 1)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
            .testTag("moments_slideshow_container")
    ) {
        // Photo Renderer Only (Aura Phase 4)
        AsyncImage(
            model = currentItem.uriPath.ifBlank { currentItem.imageUrl },
            contentDescription = currentItem.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Top Gradient & Progress Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Column {
                // Segmented Progress Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.forEachIndexed { index, _ ->
                        val segmentProgress = when {
                            index < currentIndex -> 1f
                            index == currentIndex -> slideProgress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { segmentProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AuraPurple,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = AuraPurple.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "AURA SLIDESHOW • ${mode.title.uppercase()}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("moments_slideshow_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Item Title and Tags
                    Text(
                        text = currentItem.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentItem.genre.isNotBlank() || currentItem.moodTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = listOfNotNull(
                                currentItem.genre.takeIf { it.isNotBlank() },
                                currentItem.moodTags.takeIf { it.isNotEmpty() }?.joinToString(", ")
                            ).joinToString(" • "),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Add to Favorites
                        IconButton(onClick = {
                            repository.addToFavorites(currentItem.id)
                        }) {
                            Icon(
                                imageVector = if (currentItem.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (currentItem.isFavorite) AuraPurple else Color.White
                            )
                        }

                        // Previous slide
                        IconButton(
                            onClick = {
                                logInteraction("previous")
                                if (currentIndex > 0) {
                                    currentIndex--
                                } else {
                                    currentIndex = items.size - 1
                                }
                                slideProgress = 0f
                            },
                            modifier = Modifier.testTag("moments_slideshow_prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play/Pause toggle
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(AuraPurple)
                                .clickable {
                                    isPlaying = !isPlaying
                                    logInteraction("pause_toggle")
                                }
                                .testTag("moments_slideshow_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Next slide
                        IconButton(
                            onClick = {
                                logInteraction("next")
                                if (currentIndex < items.size - 1) {
                                    currentIndex++
                                } else {
                                    currentIndex = 0
                                }
                                slideProgress = 0f
                            },
                            modifier = Modifier.testTag("moments_slideshow_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
